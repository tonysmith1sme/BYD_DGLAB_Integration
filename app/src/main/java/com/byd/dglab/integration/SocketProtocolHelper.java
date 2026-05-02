package com.byd.dglab.integration;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.util.HashMap;
import java.util.Map;

/**
 * DG-LAB协议助手类
 * 负责生成与 DG-LAB APP 本地 WebSocket API 兼容的请求
 */
public class SocketProtocolHelper {

    private static final String TAG = Constants.LOG_TAG + "_Protocol";
    private final Gson gson;

    public SocketProtocolHelper() {
        this.gson = new Gson();
    }

    /**
     * 生成设置双通道强度命令
     */
    public String generateSetStrengthCommand(int strengthA, int strengthB) {
        try {
            JsonObject requestData = new JsonObject();
            requestData.addProperty("strengthA", normalizeStrengthForDevice(strengthA));
            requestData.addProperty("strengthB", normalizeStrengthForDevice(strengthB));

            JsonObject request = new JsonObject();
            request.addProperty("id", Constants.API_ID_SET_STRENGTH);
            request.addProperty("method", Constants.API_METHOD_SET_STRENGTH);
            request.add("data", requestData);

            String jsonRequest = gson.toJson(request);
            Log.d(TAG, "Generated setStrength command: " + jsonRequest);
            return jsonRequest;

        } catch (Exception e) {
            Log.e(TAG, "Error generating setStrength command", e);
            return null;
        }
    }

    /**
     * 生成查询强度命令
     */
    public String generateQueryStrengthCommand(boolean silent) {
        try {
            JsonObject request = new JsonObject();
            request.addProperty("id", silent
                    ? Constants.API_ID_QUERY_STRENGTH_SILENT
                    : Constants.API_ID_QUERY_STRENGTH);
            request.addProperty("method", Constants.API_METHOD_QUERY_STRENGTH);

            String jsonRequest = gson.toJson(request);
            Log.d(TAG, "Generated queryStrength command: " + jsonRequest);
            return jsonRequest;

        } catch (Exception e) {
            Log.e(TAG, "Error generating queryStrength command", e);
            return null;
        }
    }

    /**
     * 将业务强度转换为 DG-LAB 设备强度
     */
    public int normalizeStrengthForDevice(int strength) {
        int clamped = Math.max(Constants.INTENSITY_MIN, Math.min(Constants.INTENSITY_MAX, strength));
        return clamped == 0 ? 0 : clamped + Constants.DG_LAB_STRENGTH_OFFSET;
    }

    /**
     * 将 DG-LAB 设备强度转换为业务强度
     */
    public int normalizeStrengthFromDevice(int strength) {
        return strength == 0 ? 0 : Math.max(0, strength - Constants.DG_LAB_STRENGTH_OFFSET);
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

            if (response.has("data") && response.get("data").isJsonObject()) {
                JsonObject data = response.getAsJsonObject("data");
                result.put("data", data);

                if (data.has("totalStrengthA")) {
                    result.put("totalStrengthA", normalizeStrengthFromDevice(data.get("totalStrengthA").getAsInt()));
                }

                if (data.has("totalStrengthB")) {
                    result.put("totalStrengthB", normalizeStrengthFromDevice(data.get("totalStrengthB").getAsInt()));
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