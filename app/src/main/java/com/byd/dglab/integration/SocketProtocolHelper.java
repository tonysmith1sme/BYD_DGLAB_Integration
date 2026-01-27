package com.byd.dglab.integration;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/**
 * SOCKET协议助手类
 * 负责生成符合DG-LAB SOCKET V3协议的JSON命令和蓝牙指令
 */
public class SocketProtocolHelper {

    private static final String TAG = Constants.LOG_TAG + "_Protocol";
    private final Gson gson;

    public SocketProtocolHelper() {
        this.gson = new Gson();
    }

    /**
     * 生成强度控制命令
     * @param channel 通道（A或B）
     * @param intensity 强度值（0-200）
     * @return JSON命令字符串
     */
    public String generateStrengthCommand(String channel, int intensity) {
        try {
            JsonObject command = new JsonObject();
            command.addProperty("type", Constants.MSG_TYPE_STRENGTH);

            JsonObject data = new JsonObject();
            data.addProperty("channel", channel);
            data.addProperty("intensity", Math.max(Constants.INTENSITY_MIN,
                    Math.min(Constants.INTENSITY_MAX, intensity)));

            command.add("data", data);

            String jsonCommand = gson.toJson(command);
            Log.d(TAG, "Generated strength command: " + jsonCommand);
            return jsonCommand;

        } catch (Exception e) {
            Log.e(TAG, "Error generating strength command", e);
            return null;
        }
    }

    /**
     * 生成脉冲控制命令
     * @param channel 通道（A或B）
     * @param frequency 频率（Hz）
     * @param intensity 强度值（0-200）
     * @return JSON命令字符串
     */
    public String generatePulseCommand(String channel, int frequency, int intensity) {
        try {
            JsonObject command = new JsonObject();
            command.addProperty("type", Constants.MSG_TYPE_PULSE);

            JsonObject data = new JsonObject();
            data.addProperty("channel", channel);
            data.addProperty("frequency", Math.max(Constants.FREQUENCY_MIN,
                    Math.min(Constants.FREQUENCY_MAX, frequency)));
            data.addProperty("intensity", Math.max(Constants.INTENSITY_MIN,
                    Math.min(Constants.INTENSITY_MAX, intensity)));

            command.add("data", data);

            String jsonCommand = gson.toJson(command);
            Log.d(TAG, "Generated pulse command: " + jsonCommand);
            return jsonCommand;

        } catch (Exception e) {
            Log.e(TAG, "Error generating pulse command", e);
            return null;
        }
    }

    /**
     * 生成二维码绑定命令
     * @param qrCode 二维码字符串
     * @return JSON命令字符串
     */
    public String generateQrCodeCommand(String qrCode) {
        try {
            JsonObject command = new JsonObject();
            command.addProperty("type", Constants.MSG_TYPE_QR_CODE);
            command.addProperty("data", qrCode);

            String jsonCommand = gson.toJson(command);
            Log.d(TAG, "Generated QR code command: " + jsonCommand);
            return jsonCommand;

        } catch (Exception e) {
            Log.e(TAG, "Error generating QR code command", e);
            return null;
        }
    }

    /**
     * 生成心跳命令
     * @return JSON命令字符串
     */
    public String generateHeartbeatCommand() {
        try {
            JsonObject command = new JsonObject();
            command.addProperty("type", Constants.MSG_TYPE_HEARTBEAT);
            command.addProperty("timestamp", System.currentTimeMillis());

            String jsonCommand = gson.toJson(command);
            Log.d(TAG, "Generated heartbeat command: " + jsonCommand);
            return jsonCommand;

        } catch (Exception e) {
            Log.e(TAG, "Error generating heartbeat command", e);
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
     * 解析接收到的JSON响应
     * @param jsonResponse JSON响应字符串
     * @return 解析后的数据映射
     */
    public Map<String, Object> parseJsonResponse(String jsonResponse) {
        try {
            Map<String, Object> result = new HashMap<>();
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);

            if (response.has("type")) {
                result.put("type", response.get("type").getAsString());
            }

            if (response.has("data")) {
                // 根据不同类型解析data字段
                JsonObject data = response.getAsJsonObject("data");
                result.put("data", data);
            }

            if (response.has("error")) {
                result.put("error", response.get("error").getAsString());
            }

            Log.d(TAG, "Parsed response: " + result);
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Error parsing JSON response", e);
            return null;
        }
    }
}