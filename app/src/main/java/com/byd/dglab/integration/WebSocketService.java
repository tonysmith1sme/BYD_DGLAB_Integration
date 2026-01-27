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
 * 负责与DG-LAB SOCKET服务器建立连接并发送控制命令
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
     * 连接到DG-LAB服务器
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

                    // 发送心跳开始
                    startHeartbeat();

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

            // 设置连接超时
            webSocketClient.setConnectionLostTimeout(0);
            webSocketClient.connect();

            Log.d(TAG, "Connecting to DG-LAB server...");

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
            stopHeartbeat();

            Log.d(TAG, "WebSocket disconnected");

        } catch (Exception e) {
            Log.e(TAG, "Error disconnecting WebSocket", e);
        }
    }

    /**
     * 发送强度控制命令
     * @param channel 通道（A或B）
     * @param intensity 强度值（0-200）
     */
    public void sendIntensityCommand(String channel, int intensity) {
        String command = protocolHelper.generateStrengthCommand(channel, intensity);
        if (command != null) {
            sendCommand("strength", command);
        }
    }

    /**
     * 发送脉冲控制命令
     * @param channel 通道（A或B）
     * @param frequency 频率（Hz）
     * @param intensity 强度值（0-200）
     */
    public void sendPulseCommand(String channel, int frequency, int intensity) {
        String command = protocolHelper.generatePulseCommand(channel, frequency, intensity);
        if (command != null) {
            sendCommand("pulse", command);
        }
    }

    /**
     * 发送二维码绑定命令
     * @param qrCode 二维码字符串
     */
    public void sendQrCodeCommand(String qrCode) {
        String command = protocolHelper.generateQrCodeCommand(qrCode);
        if (command != null) {
            sendCommand("qrCode", command);
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
            if (parsedResponse != null) {
                String responseType = (String) parsedResponse.get("type");

                // 通知监听器
                if (listener != null) {
                    handler.post(() -> listener.onResponseReceived(responseType, message));
                }

                // 处理特定响应类型
                if ("error".equals(responseType)) {
                    Log.w(TAG, "Server error response: " + message);
                } else if ("heartbeat".equals(responseType)) {
                    Log.d(TAG, "Heartbeat response received");
                }
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
     * 开始心跳
     */
    private void startHeartbeat() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnected) {
                    String heartbeatCommand = protocolHelper.generateHeartbeatCommand();
                    if (heartbeatCommand != null) {
                        sendCommand("heartbeat", heartbeatCommand);
                    }
                    // 每30秒发送一次心跳
                    handler.postDelayed(this, 30000);
                }
            }
        }, 30000);
    }

    /**
     * 停止心跳
     */
    private void stopHeartbeat() {
        // 心跳会自动停止，因为isConnected为false
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