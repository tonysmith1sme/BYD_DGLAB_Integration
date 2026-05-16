package com.byd.dglab.integration;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * DG-LAB SOCKET 协议助手类
 */
public class SocketProtocolHelper {

    private static final String TAG = Constants.LOG_TAG + "_Protocol";
    private final Gson gson;

    public SocketProtocolHelper() {
        this.gson = new Gson();
    }

    public String generateBindMessage(String clientId, String targetId, String message) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("type", Constants.MSG_TYPE_BIND);
            payload.addProperty("clientId", clientId);
            payload.addProperty("targetId", targetId);
            payload.addProperty("message", message);
            return gson.toJson(payload);
        } catch (Exception e) {
            Log.e(TAG, "Error generating bind message", e);
            return null;
        }
    }

    public String generateErrorMessage(String clientId, String targetId, String code) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("type", Constants.MSG_TYPE_ERROR);
            payload.addProperty("clientId", clientId);
            payload.addProperty("targetId", targetId);
            payload.addProperty("message", code);
            return gson.toJson(payload);
        } catch (Exception e) {
            Log.e(TAG, "Error generating error message", e);
            return null;
        }
    }

    public String generateBreakMessage(String clientId, String targetId) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("type", Constants.MSG_TYPE_BREAK);
            payload.addProperty("clientId", clientId);
            payload.addProperty("targetId", targetId);
            payload.addProperty("message", Constants.RESULT_PEER_DISCONNECTED);
            return gson.toJson(payload);
        } catch (Exception e) {
            Log.e(TAG, "Error generating break message", e);
            return null;
        }
    }

    public String generateStrengthMessage(String clientId, String targetId, int channel, int mode, int strength) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("type", Constants.MSG_TYPE_MESSAGE);
            payload.addProperty("clientId", clientId);
            payload.addProperty("targetId", targetId);
            payload.addProperty("message", String.format("strength-%d+%d+%d", channel, mode, strength));

            String jsonPayload = gson.toJson(payload);
            Log.d(TAG, "Generated strength message: " + jsonPayload);
            return jsonPayload;
        } catch (Exception e) {
            Log.e(TAG, "Error generating strength message", e);
            return null;
        }
    }

    public String generatePulseMessage(String clientId, String targetId, int channel, String waveformData) {
        return generatePulseMessage(clientId, targetId, channel == 1 ? "A" : "B", waveformData);
    }

    public String generatePulseMessage(String clientId, String targetId, String channel, String waveformData) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("type", Constants.MSG_TYPE_MESSAGE);
            payload.addProperty("clientId", clientId);
            payload.addProperty("targetId", targetId);
            payload.addProperty("message", String.format("pulse-%s:%s", channel, waveformData));

            String jsonPayload = gson.toJson(payload);
            Log.d(TAG, "Generated pulse message: " + jsonPayload);
            return jsonPayload;
        } catch (Exception e) {
            Log.e(TAG, "Error generating pulse message", e);
            return null;
        }
    }

    public String generateClearMessage(String clientId, String targetId, int channel) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("type", Constants.MSG_TYPE_MESSAGE);
            payload.addProperty("clientId", clientId);
            payload.addProperty("targetId", targetId);
            payload.addProperty("message", String.format("clear-%d", channel));

            String jsonPayload = gson.toJson(payload);
            Log.d(TAG, "Generated clear message: " + jsonPayload);
            return jsonPayload;
        } catch (Exception e) {
            Log.e(TAG, "Error generating clear message", e);
            return null;
        }
    }

    public String generateQrCodeContent(String websocketEndpoint, String clientId) {
        return Constants.QR_CODE_PREFIX + websocketEndpoint + "/" + clientId;
    }

    public String extractClientIdFromQrContent(String qrContent) {
        if (qrContent == null || !qrContent.startsWith(Constants.QR_CODE_PREFIX)) {
            return null;
        }

        String wsPart = qrContent.substring(Constants.QR_CODE_PREFIX.length());
        try {
            URI uri = new URI(wsPart);
            String path = uri.getPath();
            if (path == null || path.length() <= 1) {
                return null;
            }
            return path.substring(1);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Error parsing QR content", e);
            return null;
        }
    }

    /**
     * 生成B0蓝牙指令（强度控制）
     * @param channel 通道（A或B）
     * @param intensity 强度值（0-200）
     * @return B0指令字符串
     */
    public String generateB0Command(String channel, int intensity) {
        try {
            // B0指令格式: B0,<channel>,<intensity>,<checksum>;
            int checksum = calculateChecksum(channel, intensity);
            String command = String.format("%s%s%s%d%s%d%s",
                    Constants.B0_PREFIX,
                    Constants.COMMAND_SEPARATOR,
                    channel,
                    Constants.COMMAND_SEPARATOR,
                    intensity,
                    Constants.COMMAND_SEPARATOR,
                    checksum,
                    Constants.END_MARKER);

            Log.d(TAG, "Generated B0 command: " + command);
            return command;

        } catch (Exception e) {
            Log.e(TAG, "Error generating B0 command", e);
            return null;
        }
    }

    /**
     * 生成BF蓝牙指令（脉冲控制）
     * @param channel 通道（A或B）
     * @param frequency 频率（Hz）
     * @param intensity 强度值（0-200）
     * @return BF指令字符串
     */
    public String generateBFCommand(String channel, int frequency, int intensity) {
        try {
            // BF指令格式: BF,<channel>,<frequency>,<intensity>,<checksum>;
            int checksum = calculateChecksum(channel, frequency, intensity);
            String command = String.format("%s%s%s%s%d%s%d%s%d%s",
                    Constants.BF_PREFIX,
                    Constants.COMMAND_SEPARATOR,
                    channel,
                    Constants.COMMAND_SEPARATOR,
                    frequency,
                    Constants.COMMAND_SEPARATOR,
                    intensity,
                    Constants.COMMAND_SEPARATOR,
                    checksum,
                    Constants.END_MARKER);

            Log.d(TAG, "Generated BF command: " + command);
            return command;

        } catch (Exception e) {
            Log.e(TAG, "Error generating BF command", e);
            return null;
        }
    }

    /**
     * 计算校验和（简单求和）
     * @param params 参数
     * @return 校验和
     */
    private int calculateChecksum(Object... params) {
        int sum = 0;
        for (Object param : params) {
            if (param instanceof String) {
                sum += ((String) param).charAt(0);
            } else if (param instanceof Integer) {
                sum += (Integer) param;
            }
        }
        return sum % 256; // 取模256
    }

    /**
     * 将十六进制字符串转换为字节数组
     * @param hexString 十六进制字符串
     * @return 字节数组
     */
    public byte[] hexStringToByteArray(String hexString) {
        try {
            int len = hexString.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                        + Character.digit(hexString.charAt(i + 1), 16));
            }
            return data;
        } catch (Exception e) {
            Log.e(TAG, "Error converting hex string to byte array", e);
            return null;
        }
    }

    /**
     * 解析接收到的 JSON 响应
     */
    public Map<String, Object> parseJsonResponse(String jsonResponse) {
        try {
            Map<String, Object> result = new HashMap<>();
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);

            if (response == null) {
                return null;
            }

            if (response.has("id")) {
                result.put("id", response.get("id").getAsInt());
            }

            if (response.has("code")) {
                result.put("code", response.get("code").getAsInt());
            }

            if (response.has("method")) {
                result.put("method", response.get("method").getAsString());
            }

            if (response.has("result")) {
                result.put("result", response.get("result").getAsString());
            }

            if (response.has("message")) {
                result.put("message", response.get("message").getAsString());
            }

            if (response.has("type")) {
                result.put("type", response.get("type").getAsString());
            }

            if (response.has("clientId")) {
                result.put("clientId", response.get("clientId").getAsString());
            }

            if (response.has("targetId")) {
                result.put("targetId", response.get("targetId").getAsString());
            }

            if (response.has("data") && response.get("data").isJsonObject()) {
                JsonObject data = response.getAsJsonObject("data");
                result.put("data", data);

                if (data.has("totalStrengthA")) {
                    result.put("totalStrengthA", data.get("totalStrengthA").getAsInt());
                }

                if (data.has("totalStrengthB")) {
                    result.put("totalStrengthB", data.get("totalStrengthB").getAsInt());
                }
            } else if (response.has("data")) {
                JsonElement data = response.get("data");
                result.put("data", data);
            }

            Log.d(TAG, "Parsed response: " + result);
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Error parsing JSON response", e);
            return null;
        }
    }
}