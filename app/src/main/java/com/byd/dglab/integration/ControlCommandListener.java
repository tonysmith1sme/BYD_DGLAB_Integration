package com.byd.dglab.integration;

/**
 * 控制命令监听器接口
 */
public interface ControlCommandListener {
    /**
     * 当发送控制命令时调用
     * @param commandType 命令类型
     * @param commandData 命令数据
     */
    void onCommandSent(String commandType, String commandData);

    /**
     * 当接收到响应时调用
     * @param responseType 响应类型
     * @param responseData 响应数据
     */
    void onResponseReceived(String responseType, String responseData);

    /**
     * 当发生错误时调用
     * @param errorType 错误类型
     * @param errorMessage 错误消息
     */
    void onError(String errorType, String errorMessage);
}