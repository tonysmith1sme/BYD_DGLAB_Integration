package com.byd.dglab.integration;

import android.util.Log;
import java.util.LinkedList;
import java.util.Queue;

/**
 * 车速到控制参数转换器
 * 将车速转换为DG-LAB设备的强度和频率参数
 */
public class SpeedToControlConverter {

    private static final String TAG = Constants.LOG_TAG + "_Converter";

    // 数据平滑队列
    private final Queue<Double> speedHistory;
    private final int smoothingWindowSize;

    public SpeedToControlConverter() {
        this.smoothingWindowSize = Constants.SMOOTHING_WINDOW_SIZE;
        this.speedHistory = new LinkedList<>();
    }

    /**
     * 将车速转换为强度值
     * 使用分段线性映射，低速、中速、高速段分别映射
     * @param speedKmH 车速（km/h）
     * @return 强度值（0-200）
     */
    public int convertSpeedToIntensity(double speedKmH) {
        try {
            // 数据平滑处理
            double smoothedSpeed = smoothSpeedData(speedKmH);

            int intensity;
            if (smoothedSpeed <= Constants.LOW_SPEED_THRESHOLD) {
                // 低速段：0-30 km/h -> 0-50
                intensity = mapValue(smoothedSpeed, 0, Constants.LOW_SPEED_THRESHOLD,
                        Constants.LOW_SPEED_INTENSITY_RANGE[0], Constants.LOW_SPEED_INTENSITY_RANGE[1]);
            } else if (smoothedSpeed <= Constants.MEDIUM_SPEED_THRESHOLD) {
                // 中速段：30-80 km/h -> 50-120
                intensity = mapValue(smoothedSpeed, Constants.LOW_SPEED_THRESHOLD, Constants.MEDIUM_SPEED_THRESHOLD,
                        Constants.MEDIUM_SPEED_INTENSITY_RANGE[0], Constants.MEDIUM_SPEED_INTENSITY_RANGE[1]);
            } else if (smoothedSpeed <= Constants.HIGH_SPEED_THRESHOLD) {
                // 高速段：80-120 km/h -> 120-200
                intensity = mapValue(smoothedSpeed, Constants.MEDIUM_SPEED_THRESHOLD, Constants.HIGH_SPEED_THRESHOLD,
                        Constants.HIGH_SPEED_INTENSITY_RANGE[0], Constants.HIGH_SPEED_INTENSITY_RANGE[1]);
            } else {
                // 超高速：保持最大强度
                intensity = Constants.INTENSITY_MAX;
            }

            // 确保在有效范围内
            intensity = Math.max(Constants.INTENSITY_MIN, Math.min(Constants.INTENSITY_MAX, intensity));

            Log.d(TAG, String.format("Speed %.1f km/h -> Intensity %d", smoothedSpeed, intensity));
            return intensity;

        } catch (Exception e) {
            Log.e(TAG, "Error converting speed to intensity", e);
            return Constants.INTENSITY_MIN;
        }
    }

    /**
     * 将车速转换为频率值
     * 使用分段线性映射，与强度类似但范围不同
     * @param speedKmH 车速（km/h）
     * @return 频率值（10-240 Hz）
     */
    public int convertSpeedToFrequency(double speedKmH) {
        try {
            // 数据平滑处理
            double smoothedSpeed = smoothSpeedData(speedKmH);

            int frequency;
            if (smoothedSpeed <= Constants.LOW_SPEED_THRESHOLD) {
                // 低速段：0-30 km/h -> 10-30 Hz
                frequency = mapValue(smoothedSpeed, 0, Constants.LOW_SPEED_THRESHOLD,
                        Constants.LOW_SPEED_FREQUENCY_RANGE[0], Constants.LOW_SPEED_FREQUENCY_RANGE[1]);
            } else if (smoothedSpeed <= Constants.MEDIUM_SPEED_THRESHOLD) {
                // 中速段：30-80 km/h -> 30-80 Hz
                frequency = mapValue(smoothedSpeed, Constants.LOW_SPEED_THRESHOLD, Constants.MEDIUM_SPEED_THRESHOLD,
                        Constants.MEDIUM_SPEED_FREQUENCY_RANGE[0], Constants.MEDIUM_SPEED_FREQUENCY_RANGE[1]);
            } else if (smoothedSpeed <= Constants.HIGH_SPEED_THRESHOLD) {
                // 高速段：80-120 km/h -> 80-150 Hz
                frequency = mapValue(smoothedSpeed, Constants.MEDIUM_SPEED_THRESHOLD, Constants.HIGH_SPEED_THRESHOLD,
                        Constants.HIGH_SPEED_FREQUENCY_RANGE[0], Constants.HIGH_SPEED_FREQUENCY_RANGE[1]);
            } else {
                // 超高速：保持较高频率
                frequency = Constants.HIGH_SPEED_FREQUENCY_RANGE[1];
            }

            // 确保在有效范围内
            frequency = Math.max(Constants.FREQUENCY_MIN, Math.min(Constants.FREQUENCY_MAX, frequency));

            Log.d(TAG, String.format("Speed %.1f km/h -> Frequency %d Hz", smoothedSpeed, frequency));
            return frequency;

        } catch (Exception e) {
            Log.e(TAG, "Error converting speed to frequency", e);
            return Constants.FREQUENCY_MIN;
        }
    }

    /**
     * 生成B0指令（基于车速）
     * @param speedKmH 车速（km/h）
     * @param channel 通道（A或B）
     * @return B0指令字符串
     */
    public String generateB0Command(double speedKmH, String channel) {
        int intensity = convertSpeedToIntensity(speedKmH);
        SocketProtocolHelper helper = new SocketProtocolHelper();
        return helper.generateB0Command(channel, intensity);
    }

    /**
     * 生成BF指令（基于车速）
     * @param speedKmH 车速（km/h）
     * @param channel 通道（A或B）
     * @return BF指令字符串
     */
    public String generateBFCommand(double speedKmH, String channel) {
        int intensity = convertSpeedToIntensity(speedKmH);
        int frequency = convertSpeedToFrequency(speedKmH);
        SocketProtocolHelper helper = new SocketProtocolHelper();
        return helper.generateBFCommand(channel, frequency, intensity);
    }

    /**
     * 数据平滑处理
     * 使用移动平均滤波减少车速波动
     * @param newSpeed 新的车速值
     * @return 平滑后的车速值
     */
    private double smoothSpeedData(double newSpeed) {
        // 添加新数据到队列
        speedHistory.add(newSpeed);

        // 保持队列大小
        while (speedHistory.size() > smoothingWindowSize) {
            speedHistory.poll();
        }

        // 计算平均值
        double sum = 0;
        for (double speed : speedHistory) {
            sum += speed;
        }

        return sum / speedHistory.size();
    }

    /**
     * 线性映射函数
     * 将输入范围映射到输出范围
     * @param value 输入值
     * @param inMin 输入最小值
     * @param inMax 输入最大值
     * @param outMin 输出最小值
     * @param outMax 输出最大值
     * @return 映射后的值
     */
    private int mapValue(double value, double inMin, double inMax, int outMin, int outMax) {
        if (inMax == inMin) {
            return outMin;
        }

        double ratio = (value - inMin) / (inMax - inMin);
        ratio = Math.max(0, Math.min(1, ratio)); // 限制在0-1范围内

        return (int) Math.round(outMin + ratio * (outMax - outMin));
    }

    /**
     * 重置平滑数据
     * 用于重新开始数据收集
     */
    public void resetSmoothing() {
        speedHistory.clear();
        Log.d(TAG, "Speed smoothing data reset");
    }

    /**
     * 获取当前平滑窗口大小
     * @return 窗口大小
     */
    public int getSmoothingWindowSize() {
        return smoothingWindowSize;
    }
}