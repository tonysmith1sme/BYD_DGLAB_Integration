package com.byd.dglab.integration;

/**
 * 常量定义类
 * 包含所有协议常量、服务器配置和数据范围定义
 */
public class Constants {

    // ==================== 网络配置 ====================
    /** DG-LAB SOCKET服务器地址 */
    public static final String SOCKET_SERVER_URL = "wss://ws.dg-lab.cn:8443";

    /** 连接超时时间（毫秒） */
    public static final int CONNECTION_TIMEOUT_MS = 10000;

    /** 重连间隔时间（毫秒） */
    public static final int RECONNECT_INTERVAL_MS = 5000;

    /** 最大重连次数 */
    public static final int MAX_RECONNECT_ATTEMPTS = 5;

    // ==================== 数据范围 ====================
    /** 强度最小值 */
    public static final int INTENSITY_MIN = 0;

    /** 强度最大值 */
    public static final int INTENSITY_MAX = 200;

    /** 频率最小值（Hz） */
    public static final int FREQUENCY_MIN = 10;

    /** 频率最大值（Hz） */
    public static final int FREQUENCY_MAX = 240;

    /** 车速最小值（km/h） */
    public static final int SPEED_MIN = 0;

    /** 车速最大值（km/h） */
    public static final int SPEED_MAX = 200;

    // ==================== 转换参数 ====================
    /** 低速段结束车速（km/h） */
    public static final int LOW_SPEED_THRESHOLD = 30;

    /** 中速段结束车速（km/h） */
    public static final int MEDIUM_SPEED_THRESHOLD = 80;

    /** 高速段结束车速（km/h） */
    public static final int HIGH_SPEED_THRESHOLD = 120;

    /** 低速段强度范围 */
    public static final int[] LOW_SPEED_INTENSITY_RANGE = {0, 50};

    /** 中速段强度范围 */
    public static final int[] MEDIUM_SPEED_INTENSITY_RANGE = {50, 120};

    /** 高速段强度范围 */
    public static final int[] HIGH_SPEED_INTENSITY_RANGE = {120, 200};

    /** 低速段频率范围 */
    public static final int[] LOW_SPEED_FREQUENCY_RANGE = {10, 30};

    /** 中速段频率范围 */
    public static final int[] MEDIUM_SPEED_FREQUENCY_RANGE = {30, 80};

    /** 高速段频率范围 */
    public static final int[] HIGH_SPEED_FREQUENCY_RANGE = {80, 150};

    // ==================== 协议常量 ====================
    /** 消息类型：强度控制 */
    public static final String MSG_TYPE_STRENGTH = "strength";

    /** 消息类型：脉冲控制 */
    public static final String MSG_TYPE_PULSE = "pulse";

    /** 消息类型：二维码绑定 */
    public static final String MSG_TYPE_QR_CODE = "qrCode";

    /** 消息类型：心跳 */
    public static final String MSG_TYPE_HEARTBEAT = "heartbeat";

    /** 通道A标识 */
    public static final String CHANNEL_A = "A";

    /** 通道B标识 */
    public static final String CHANNEL_B = "B";

    // ==================== 蓝牙协议常量 ====================
    /** B0指令前缀 */
    public static final String B0_PREFIX = "B0";

    /** BF指令前缀 */
    public static final String BF_PREFIX = "BF";

    /** 指令分隔符 */
    public static final String COMMAND_SEPARATOR = ",";

    /** 结束符 */
    public static final String END_MARKER = ";";

    // ==================== 车速数据源模式 ====================
    /** 只使用GPS数据源 */
    public static final int DATA_SOURCE_GPS_ONLY = 0;

    /** 优先使用BYD车机系统，无法使用时降级到GPS */
    public static final int DATA_SOURCE_BYD_AUTO = 1;

    /** 只使用BYD车机系统 */
    public static final int DATA_SOURCE_BYD_ONLY = 2;

    /** 默认数据源模式 */
    public static final int DEFAULT_DATA_SOURCE_MODE = DATA_SOURCE_GPS_ONLY;

    /** SharedPreferences中数据源模式的键 */
    public static final String PREF_DATA_SOURCE_MODE = "data_source_mode";

    /** SharedPreferences中WebSocket地址的键 */
    public static final String PREF_WEBSOCKET_URL = "websocket_url";

    // ==================== 其他常量 ====================
    /** 日志标签 */
    public static final String LOG_TAG = "BYD_DGLAB";

    /** 数据平滑窗口大小 */
    public static final int SMOOTHING_WINDOW_SIZE = 5;

    /** 更新间隔（毫秒） */
    public static final int UPDATE_INTERVAL_MS = 1000;
}