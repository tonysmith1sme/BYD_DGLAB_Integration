package com.byd.dglab.integration;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket服务
 * 负责启动局域网控制端服务，并与 DG-LAB APP 完成扫码配对
 */
public class WebSocketService {

    private static final String TAG = Constants.LOG_TAG + "_WebSocket";

    private final ControlCommandListener listener;
    private final Handler handler;
    private final SocketProtocolHelper protocolHelper;
    private final String serverUrl;

    private LocalControlServer localControlServer;
    private boolean isConnected = false;
    private int reconnectAttempts = 0;
    private boolean isReconnecting = false;
    private int lastStrengthA = Integer.MIN_VALUE;
    private int lastStrengthB = Integer.MIN_VALUE;
    private String controllerClientId;
    private String pairedTargetId;
    private WebSocket pairedSocket;
    private String qrCodeContent;
    private final Map<String, Runnable> waveformTasks = new HashMap<>();

    private static final long WAVEFORM_REPEAT_INTERVAL_MS = 1000L;
    private static final long WAVEFORM_CLEAR_DELAY_MS = 150L;

    private static class ClientSession {
        final String appClientId;
        final String requestedControllerId;

        ClientSession(String appClientId, String requestedControllerId) {
            this.appClientId = appClientId;
            this.requestedControllerId = requestedControllerId;
        }
    }

    public WebSocketService(ControlCommandListener listener) {
        this(listener, Constants.SOCKET_SERVER_URL);
    }

    public WebSocketService(ControlCommandListener listener, String serverUrl) {
        this.listener = listener;
        this.serverUrl = serverUrl;
        this.handler = new Handler(Looper.getMainLooper());
        this.protocolHelper = new SocketProtocolHelper();
    }

    /**
     * 启动本地控制服务
     */
    public void connect() {
        try {
            if (isConnected) {
                Log.d(TAG, "Control server already running");
                return;
            }

            URI serverUri = URI.create(serverUrl);
            controllerClientId = UUID.randomUUID().toString();
            pairedTargetId = null;
            pairedSocket = null;
            qrCodeContent = protocolHelper.generateQrCodeContent(buildAdvertisedEndpoint(serverUri), controllerClientId);

            localControlServer = new LocalControlServer(new InetSocketAddress(serverUri.getPort()));
            localControlServer.start();

            isConnected = true;
            reconnectAttempts = 0;
            isReconnecting = false;

            if (listener != null) {
                final String qr = qrCodeContent;
                handler.post(() -> {
                    listener.onResponseReceived("connection", "opened");
                    listener.onResponseReceived("qrCode", qr);
                });
            }

            Log.d(TAG, "Local control server started: " + qrCodeContent);

        } catch (Exception e) {
            Log.e(TAG, "Error connecting to WebSocket", e);
            if (listener != null) {
                handler.post(() -> listener.onError("connection", e.getMessage()));
            }
        }
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        try {
            if (pairedSocket != null && pairedSocket.isOpen() && controllerClientId != null && pairedTargetId != null) {
                String breakMessage = protocolHelper.generateBreakMessage(controllerClientId, pairedTargetId);
                if (breakMessage != null) {
                    pairedSocket.send(breakMessage);
                }
                pairedSocket.close();
            }
            if (localControlServer != null) {
                localControlServer.stop();
                localControlServer = null;
            }
            isConnected = false;
            isReconnecting = false;
            lastStrengthA = Integer.MIN_VALUE;
            lastStrengthB = Integer.MIN_VALUE;
            controllerClientId = null;
            pairedTargetId = null;
            pairedSocket = null;
            qrCodeContent = null;
            clearAllWaveformTasks();

            Log.d(TAG, "WebSocket disconnected");

            if (listener != null) {
                handler.post(() -> listener.onResponseReceived("connection", "closed"));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error disconnecting WebSocket", e);
        }
    }

    /**
     * 发送双通道强度控制命令
     */
    public void sendStrengthCommand(int strengthA, int strengthB) {
        if (pairedSocket == null || !pairedSocket.isOpen() || controllerClientId == null || pairedTargetId == null) {
            if (listener != null) {
                handler.post(() -> listener.onError("send", "DG-LAB APP 尚未扫码配对"));
            }
            return;
        }

        int safeStrengthA = Math.max(Constants.INTENSITY_MIN, Math.min(Constants.INTENSITY_MAX, strengthA));
        int safeStrengthB = Math.max(Constants.INTENSITY_MIN, Math.min(Constants.INTENSITY_MAX, strengthB));

        if (safeStrengthA == lastStrengthA && safeStrengthB == lastStrengthB) {
            return;
        }

        String commandA = protocolHelper.generateStrengthMessage(controllerClientId, pairedTargetId, 1, 2, safeStrengthA);
        String commandB = protocolHelper.generateStrengthMessage(controllerClientId, pairedTargetId, 2, 2, safeStrengthB);
        if (commandA != null && commandB != null) {
            lastStrengthA = safeStrengthA;
            lastStrengthB = safeStrengthB;
            sendCommand("setStrengthA", commandA);
            sendCommand("setStrengthB", commandB);
        }
    }

    public void sendWaveformCommand(int channel, String waveformData) {
        sendWaveformCommand(channel, waveformData, Constants.WAVEFORM_DURATION_DEFAULT_SECONDS);
    }

    public void sendWaveformCommand(int channel, String waveformData, int durationSeconds) {
        if (pairedSocket == null || !pairedSocket.isOpen() || controllerClientId == null || pairedTargetId == null) {
            if (listener != null) {
                handler.post(() -> listener.onError("send", "DG-LAB APP 尚未扫码配对"));
            }
            return;
        }

        if (waveformData == null || waveformData.trim().isEmpty()) {
            return;
        }

        int safeDurationSeconds = Math.max(Constants.WAVEFORM_DURATION_MIN_SECONDS,
                Math.min(Constants.WAVEFORM_DURATION_MAX_SECONDS, durationSeconds));
        scheduleWaveform(channel, waveformData.trim(), safeDurationSeconds);
    }

    public String getQrCodeContent() {
        return qrCodeContent;
    }

    /**
     * 发送命令到服务器
     * @param commandType 命令类型
     * @param commandData 命令数据
     */
    private void sendCommand(String commandType, String commandData) {
        try {
            if (!isConnected || pairedSocket == null || !pairedSocket.isOpen()) {
                Log.w(TAG, "Cannot send command: not connected");
                if (listener != null) {
                    handler.post(() -> listener.onError("send", "Not connected"));
                }
                return;
            }

            pairedSocket.send(commandData);
            Log.d(TAG, "Sent command: " + commandType + " - " + commandData);
            emitDiagnostic("sent " + commandType + ": " + commandData);

            // 通知监听器
            if (listener != null) {
                handler.post(() -> listener.onCommandSent(commandType, commandData));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error sending command", e);
            if (listener != null) {
                handler.post(() -> listener.onError("send", e.getMessage()));
            }
        }
    }

    private void scheduleWaveform(int channel, String waveformData, int durationSeconds) {
        final String channelName = channel == 1 ? "A" : "B";
        final String taskKey = "waveform-" + channelName;
        final int repeatCount = Math.max(1, durationSeconds);

        emitDiagnostic("schedule waveform channel=" + channelName + ", duration=" + durationSeconds + "s, repeats=" + repeatCount);

        Runnable existingTask = waveformTasks.remove(taskKey);
        if (existingTask != null) {
            handler.removeCallbacks(existingTask);
            String clearCommand = protocolHelper.generateClearMessage(controllerClientId, pairedTargetId, channel);
            if (clearCommand != null) {
                sendCommand("clearWaveform" + channelName, clearCommand);
            }
        }

        Runnable sendTask = new Runnable() {
            private int remaining = repeatCount;

            @Override
            public void run() {
                if (pairedSocket == null || !pairedSocket.isOpen() || controllerClientId == null || pairedTargetId == null) {
                    waveformTasks.remove(taskKey);
                    return;
                }

                String pulseCommand = protocolHelper.generatePulseMessage(controllerClientId, pairedTargetId, channelName, waveformData);
                if (pulseCommand != null) {
                    sendCommand("setWaveform" + channelName, pulseCommand);
                }

                remaining--;
                if (remaining > 0) {
                    handler.postDelayed(this, WAVEFORM_REPEAT_INTERVAL_MS);
                } else {
                    waveformTasks.remove(taskKey);
                }
            }
        };

        waveformTasks.put(taskKey, sendTask);
        handler.postDelayed(sendTask, existingTask != null ? WAVEFORM_CLEAR_DELAY_MS : 0L);
    }

    private void emitDiagnostic(String diagnosticMessage) {
        if (listener != null) {
            handler.post(() -> listener.onResponseReceived("diagnostic", diagnosticMessage));
        }
    }

    private void clearAllWaveformTasks() {
        for (Runnable task : waveformTasks.values()) {
            handler.removeCallbacks(task);
        }
        waveformTasks.clear();
    }

    /**
     * 处理接收到的消息
     * @param message 消息内容
     */
    private void handleIncomingMessage(String message) {
        try {
            Map<String, Object> parsedResponse = protocolHelper.parseJsonResponse(message);
            if (parsedResponse == null) {
                return;
            }

            if (listener != null) {
                String responseType = parsedResponse.containsKey("type")
                        ? String.valueOf(parsedResponse.get("type"))
                        : "api";
                handler.post(() -> {
                    listener.onResponseReceived("diagnostic", "raw message: " + message);
                    listener.onResponseReceived(responseType, message);
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling incoming message", e);
        }
    }

    /**
     * 调度重连
     */
    private void scheduleReconnect() {
        if (isReconnecting) return;

        isReconnecting = true;
        reconnectAttempts++;

        Log.d(TAG, "Scheduling reconnect attempt " + reconnectAttempts + " in " +
                Constants.RECONNECT_INTERVAL_MS + "ms");

        handler.postDelayed(() -> {
            if (!isConnected && reconnectAttempts <= Constants.MAX_RECONNECT_ATTEMPTS) {
                Log.d(TAG, "Attempting to reconnect...");
                connect();
            } else {
                isReconnecting = false;
            }
        }, Constants.RECONNECT_INTERVAL_MS);
    }

    /**
     * 检查连接状态
     * @return 是否已连接
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * 获取重连尝试次数
     * @return 重连次数
     */
    public int getReconnectAttempts() {
        return reconnectAttempts;
    }

    private String buildAdvertisedEndpoint(URI configuredUri) {
        String host = configuredUri.getHost();
        if (host == null || "0.0.0.0".equals(host) || "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)) {
            host = findLocalIpv4Address();
        }
        return "ws://" + host + ":" + configuredUri.getPort();
    }

    private String findLocalIpv4Address() {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress inetAddress : Collections.list(networkInterface.getInetAddresses())) {
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getHostAddress().indexOf(':') < 0) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get local IP", e);
        }
        return "127.0.0.1";
    }

    private class LocalControlServer extends WebSocketServer {
        LocalControlServer(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            String appClientId = UUID.randomUUID().toString();
            String requestedControllerId = extractControllerIdFromPath(handshake.getResourceDescriptor());
            conn.setAttachment(new ClientSession(appClientId, requestedControllerId));
            String bindMessage = protocolHelper.generateBindMessage(appClientId, "", "targetId");
            if (bindMessage != null) {
                conn.send(bindMessage);
            }
            Log.d(TAG, "App connected: appClientId=" + appClientId
                    + ", requestedControllerId=" + requestedControllerId
                    + ", path=" + handshake.getResourceDescriptor());
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            if (conn == pairedSocket) {
                clearAllWaveformTasks();
                pairedSocket = null;
                pairedTargetId = null;
                lastStrengthA = Integer.MIN_VALUE;
                lastStrengthB = Integer.MIN_VALUE;
                if (listener != null) {
                    handler.post(() -> listener.onResponseReceived(Constants.MSG_TYPE_BREAK,
                            protocolHelper.generateBreakMessage(controllerClientId != null ? controllerClientId : "", "")));
                }
            }
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            Log.d(TAG, "Received message: " + message);
            Map<String, Object> parsed = protocolHelper.parseJsonResponse(message);
            if (parsed == null) {
                String error = protocolHelper.generateErrorMessage("", "", Constants.RESULT_INVALID_PAYLOAD);
                if (error != null) {
                    conn.send(error);
                }
                return;
            }

            String type = parsed.containsKey("type") ? String.valueOf(parsed.get("type")) : "";
            String clientId = parsed.containsKey("clientId") ? String.valueOf(parsed.get("clientId")) : "";
            String targetId = parsed.containsKey("targetId") ? String.valueOf(parsed.get("targetId")) : "";

            if (Constants.MSG_TYPE_BIND.equals(type)) {
                ClientSession session = conn.getAttachment();
                String attachedAppId = session != null ? session.appClientId : null;
                String requestedControllerId = session != null ? session.requestedControllerId : null;

                boolean appIdMatches = attachedAppId != null && attachedAppId.equals(targetId);
                boolean controllerIdMatches = controllerClientId != null && controllerClientId.equals(clientId);
                boolean requestedIdMatches = requestedControllerId != null && requestedControllerId.equals(clientId);
                boolean controllerMatches = controllerIdMatches || requestedIdMatches || controllerClientId == null;

                Log.d(TAG, "Bind check. clientId=" + clientId
                        + ", targetId=" + targetId
                        + ", controllerClientId=" + controllerClientId
                        + ", requestedControllerId=" + requestedControllerId
                        + ", attachedAppId=" + attachedAppId
                        + ", controllerMatches=" + controllerMatches
                        + ", appIdMatches=" + appIdMatches);

                if (clientId.isEmpty() || targetId.isEmpty()) {
                    String reason = Constants.RESULT_INVALID_PAYLOAD;
                    Log.w(TAG, "Bind rejected. clientId=" + clientId
                            + ", targetId=" + targetId
                            + ", requestedControllerId=" + requestedControllerId
                            + ", controllerClientId=" + controllerClientId
                            + ", attachedAppId=" + attachedAppId);
                    if (listener != null) {
                        String finalReason = reason;
                        handler.post(() -> listener.onResponseReceived("diagnostic",
                                "bind rejected: clientId=" + clientId + ", targetId=" + targetId + ", reason=" + finalReason));
                    }

                    String error = protocolHelper.generateErrorMessage(clientId, targetId, reason);
                    if (error != null) {
                        conn.send(error);
                    }
                    return;
                }

                if (!controllerMatches || !appIdMatches) {
                    String reason = !appIdMatches ? Constants.RESULT_TARGET_NOT_FOUND : Constants.RESULT_NOT_PAIRED;
                    Log.w(TAG, "Bind rejected. clientId=" + clientId
                            + ", targetId=" + targetId
                            + ", requestedControllerId=" + requestedControllerId
                            + ", controllerClientId=" + controllerClientId
                            + ", attachedAppId=" + attachedAppId);
                    if (listener != null) {
                        String finalReason = reason;
                        handler.post(() -> listener.onResponseReceived("diagnostic",
                                "bind rejected: clientId=" + clientId + ", targetId=" + targetId + ", reason=" + finalReason));
                    }

                    String error = protocolHelper.generateBindMessage(clientId, targetId, reason);
                    if (error != null) {
                        conn.send(error);
                    }
                    return;
                }

                if (pairedSocket != null && pairedSocket != conn) {
                    String error = protocolHelper.generateBindMessage(clientId, targetId, Constants.RESULT_ALREADY_BOUND);
                    if (error != null) {
                        conn.send(error);
                    }
                    return;
                }

                pairedSocket = conn;
                pairedTargetId = targetId;
                String success = protocolHelper.generateBindMessage(controllerClientId, pairedTargetId, Constants.RESULT_SUCCESS);
                if (success != null) {
                    conn.send(success);
                }
                if (listener != null) {
                    String finalClientId = clientId;
                    String finalTargetId = targetId;
                    handler.post(() -> listener.onResponseReceived(Constants.MSG_TYPE_BIND, success));
                    handler.post(() -> listener.onResponseReceived("diagnostic",
                            "bind accepted: clientId=" + finalClientId + ", targetId=" + finalTargetId));
                }
                return;
            }

            handleIncomingMessage(message);
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            Log.e(TAG, "Server error", ex);
            if (listener != null) {
                handler.post(() -> listener.onError("server", ex.getMessage()));
            }
        }

        @Override
        public void onStart() {
            Log.d(TAG, "Local server started");
        }

        private String extractControllerIdFromPath(String resourceDescriptor) {
            if (resourceDescriptor == null || resourceDescriptor.isEmpty() || "/".equals(resourceDescriptor)) {
                return null;
            }

            String normalized = resourceDescriptor;
            int queryIndex = normalized.indexOf('?');
            if (queryIndex >= 0) {
                normalized = normalized.substring(0, queryIndex);
            }

            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }

            if (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }

            return normalized.isEmpty() ? null : normalized;
        }
    }
}