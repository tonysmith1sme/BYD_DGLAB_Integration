package com.byd.dglab.integration;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.Map;

/**
 * WebSocket服务
 * 负责与 DG-LAB APP 本地 WebSocket API 建立连接并发送控制命令
 */
public class WebSocketService {

    private static final String TAG = Constants.LOG_TAG + "_WebSocket";

    private final ControlCommandListener listener;
    private final Handler handler;
    private final SocketProtocolHelper protocolHelper;
    private final String serverUrl;

    private WebSocketClient webSocketClient;
    private boolean isConnected = false;
    private int reconnectAttempts = 0;
    private boolean isReconnecting = false;
    private int lastStrengthA = Integer.MIN_VALUE;
    private int lastStrengthB = Integer.MIN_VALUE;

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
    * 连接到 DG-LAB APP
     */
    public void connect() {
        try {
            if (isConnected) {
                Log.d(TAG, "Already connected");
                return;
            }

            URI serverUri = URI.create(serverUrl);
            webSocketClient = new WebSocketClient(serverUri) {

                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Log.d(TAG, "WebSocket connection opened");
                    isConnected = true;
                    reconnectAttempts = 0;
                    isReconnecting = false;
                    queryStrength(true);

                    // 通知监听器
                    if (listener != null) {
                        handler.post(() -> listener.onResponseReceived("connection", "opened"));
                    }
                }

                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "Received message: " + message);
                    handleIncomingMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "WebSocket connection closed: " + code + " - " + reason);
                    isConnected = false;

                    // 通知监听器
                    if (listener != null) {
                        handler.post(() -> listener.onResponseReceived("connection", "closed"));
                    }

                    lastStrengthA = Integer.MIN_VALUE;
                    lastStrengthB = Integer.MIN_VALUE;

                    // 自动重连
                    if (!isReconnecting && reconnectAttempts < Constants.MAX_RECONNECT_ATTEMPTS) {
                        scheduleReconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error", ex);

                    // 通知监听器
                    if (listener != null) {
                        handler.post(() -> listener.onError("websocket", ex.getMessage()));
                    }
                }
            };

            webSocketClient.setConnectionLostTimeout(60);
            webSocketClient.connect();

            Log.d(TAG, "Connecting to DG-LAB APP: " + serverUrl);

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
            if (webSocketClient != null) {
                webSocketClient.close();
                webSocketClient = null;
            }
            isConnected = false;
            isReconnecting = false;
            lastStrengthA = Integer.MIN_VALUE;
            lastStrengthB = Integer.MIN_VALUE;

            Log.d(TAG, "WebSocket disconnected");

        } catch (Exception e) {
            Log.e(TAG, "Error disconnecting WebSocket", e);
        }
    }

    /**
     * 发送双通道强度控制命令
     */
    public void sendStrengthCommand(int strengthA, int strengthB) {
        int safeStrengthA = Math.max(Constants.INTENSITY_MIN, Math.min(Constants.INTENSITY_MAX, strengthA));
        int safeStrengthB = Math.max(Constants.INTENSITY_MIN, Math.min(Constants.INTENSITY_MAX, strengthB));

        if (safeStrengthA == lastStrengthA && safeStrengthB == lastStrengthB) {
            return;
        }

        String command = protocolHelper.generateSetStrengthCommand(safeStrengthA, safeStrengthB);
        if (command != null) {
            lastStrengthA = safeStrengthA;
            lastStrengthB = safeStrengthB;
            sendCommand("setStrength", command);
        }
    }

    /**
     * 查询当前强度
     */
    public void queryStrength(boolean silent) {
        String command = protocolHelper.generateQueryStrengthCommand(silent);
        if (command != null) {
            sendCommand(silent ? "queryStrengthSilent" : "queryStrength", command);
        }
    }

    /**
     * 发送命令到服务器
     * @param commandType 命令类型
     * @param commandData 命令数据
     */
    private void sendCommand(String commandType, String commandData) {
        try {
            if (!isConnected || webSocketClient == null) {
                Log.w(TAG, "Cannot send command: not connected");
                if (listener != null) {
                    handler.post(() -> listener.onError("send", "Not connected"));
                }
                return;
            }

            webSocketClient.send(commandData);
            Log.d(TAG, "Sent command: " + commandType + " - " + commandData);

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

            Object idObject = parsedResponse.get("id");
            int responseId = idObject instanceof Number ? ((Number) idObject).intValue() : -1;

            if (listener != null) {
                String responseType;
                switch (responseId) {
                    case Constants.API_ID_QUERY_STRENGTH:
                    case Constants.API_ID_QUERY_STRENGTH_SILENT:
                        responseType = "queryStrength";
                        break;
                    case Constants.API_ID_SET_STRENGTH:
                        responseType = "setStrength";
                        break;
                    default:
                        responseType = parsedResponse.containsKey("type")
                                ? String.valueOf(parsedResponse.get("type"))
                                : "api";
                        break;
                }

                handler.post(() -> listener.onResponseReceived(responseType, message));
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
}