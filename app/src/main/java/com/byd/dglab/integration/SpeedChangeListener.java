package com.byd.dglab.integration;

/**
 * 车速变化监听器接口
 */
public interface SpeedChangeListener {
    /**
     * 当车速发生变化时调用
     * @param speedKmH 当前车速（km/h）
     */
    void onSpeedChanged(double speedKmH);
}