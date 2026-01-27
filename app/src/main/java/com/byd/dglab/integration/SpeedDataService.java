package com.byd.dglab.integration;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;

/**
 * 车速数据服务
 * 负责从BYD SDK或GPS获取车速数据并进行处理
 * 
 * BYD SDK集成说明：
 * 如需使用BYD-AUTO-API，请取消下列代码的注释并导入BYD SDK
 * import android.hardware.bydauto.speed.BYDAutoSpeedDevice;
 * import android.hardware.bydauto.speed.AbsBYDAutoSpeedListener;
 */
public class SpeedDataService implements LocationListener {

    private static final String TAG = Constants.LOG_TAG + "_SpeedService";

    private final Context context;
    private final SpeedChangeListener listener;
    private final Handler handler;
    private final SharedPreferences sharedPreferences;

    // GPS相关
    private LocationManager locationManager;
    private boolean isGpsEnabled = false;

    // BYD SDK相关
    // 取消下列注释以启用BYD SDK集成：
    // private BYDAutoSpeedDevice bydSpeedDevice;
    private boolean isBydApiAvailable = false;

    // 数据源模式
    private int currentDataSourceMode = Constants.DATA_SOURCE_GPS_ONLY;
    private int gpsSpeedForBydFallback = 0; // GPS速度用于BYD不可用时的降级

    // 当前车速
    private double currentSpeedKmh = 0.0;
    private long lastUpdateTime = 0;
    private boolean speedFromBYD = false; // 标记当前速度来源

    public SpeedDataService(Context context, SpeedChangeListener listener) {
        this.context = context;
        this.listener = listener;
        this.handler = new Handler(Looper.getMainLooper());
        this.sharedPreferences = context.getSharedPreferences(
                context.getPackageName() + ".preferences",
                Context.MODE_PRIVATE);

        // 从SharedPreferences读取保存的数据源模式
        this.currentDataSourceMode = sharedPreferences.getInt(
                Constants.PREF_DATA_SOURCE_MODE,
                Constants.DEFAULT_DATA_SOURCE_MODE);

        initializeServices();
    }

    /**
     * 初始化服务
     */
    private void initializeServices() {
        try {
            // 初始化GPS
            initializeGPS();

            // 初始化BYD SDK（如果可用）
            initializeBYDAutoAPI();

            Log.d(TAG, "Speed data services initialized. DataSource mode: " + currentDataSourceMode);

        } catch (Exception e) {
            Log.e(TAG, "Error initializing speed data services", e);
        }
    }

    /**
     * 初始化GPS定位服务
     */
    private void initializeGPS() {
        try {
            locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            if (locationManager != null) {
                // 检查GPS权限
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {

                    // 检查GPS是否可用
                    isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

                    if (isGpsEnabled) {
                        // 注册GPS监听器
                        locationManager.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER,
                                Constants.UPDATE_INTERVAL_MS,
                                1, // 最小距离变化（米）
                                this
                        );

                        Log.d(TAG, "GPS location updates requested");
                    } else {
                        Log.w(TAG, "GPS provider not enabled");
                    }
                } else {
                    Log.w(TAG, "GPS permission not granted");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initializing GPS", e);
        }
    }

    /**
     * 初始化BYD自动驾驶API
     * 
     * 完整BYD API集成示例（需要导入BYD SDK）：
     * <pre>
     * private BYDAutoSpeedDevice bydSpeedDevice;
     * private AbsBYDAutoSpeedListener bydSpeedListener;
     * 
     * private void initializeBYDAutoAPI() {
     *     try {
     *         bydSpeedDevice = BYDAutoSpeedDevice.getInstance();
     *         if (bydSpeedDevice != null) {
     *             bydSpeedListener = new AbsBYDAutoSpeedListener() {
     *                 @Override
     *                 public void onBYDAutoSpeedChanged(float speedKmh) {
     *                     handleSpeedUpdate(speedKmh, true);
     *                 }
     *             };
     *             bydSpeedDevice.registerBYDAutoSpeedListener(bydSpeedListener);
     *             isBydApiAvailable = true;
     *         }
     *     } catch (Exception e) {
     *         isBydApiAvailable = false;
     *     }
     * }
     * </pre>
     */
    private void initializeBYDAutoAPI() {
        try {
            isBydApiAvailable = false;
            if (isBydApiAvailable) {
                Log.d(TAG, "BYD Auto API initialized successfully");
            } else {
                Log.w(TAG, "BYD Auto API not available or disabled");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing BYD Auto API", e);
            isBydApiAvailable = false;
        }
    }

    /**
     * 处理车速更新 - 根据数据源模式决定是否更新
     * @param speedKmh 车速（km/h）
     * @param isFromBYD 速度是否来自BYD系统
     */
    private void handleSpeedUpdate(double speedKmh, boolean isFromBYD) {
        try {
            if (speedKmh < 0) {
                Log.w(TAG, "Invalid speed value: " + speedKmh);
                return;
            }

            boolean shouldUpdate = false;
            String dataSourceName = "";

            switch (currentDataSourceMode) {
                case Constants.DATA_SOURCE_GPS_ONLY:
                    shouldUpdate = !isFromBYD;
                    dataSourceName = "GPS";
                    if (!isFromBYD) {
                        gpsSpeedForBydFallback = (int) speedKmh;
                    }
                    break;

                case Constants.DATA_SOURCE_BYD_AUTO:
                    if (isFromBYD) {
                        shouldUpdate = true;
                        dataSourceName = "BYD";
                    } else if (!isBydApiAvailable) {
                        shouldUpdate = true;
                        dataSourceName = "GPS (BYD unavailable)";
                        gpsSpeedForBydFallback = (int) speedKmh;
                    }
                    break;

                case Constants.DATA_SOURCE_BYD_ONLY:
                    shouldUpdate = isFromBYD;
                    dataSourceName = "BYD";
                    if (!isFromBYD) {
                        gpsSpeedForBydFallback = (int) speedKmh;
                    }
                    break;
            }

            if (shouldUpdate) {
                speedKmh = Math.min(speedKmh, Constants.SPEED_MAX);
                currentSpeedKmh = speedKmh;
                speedFromBYD = isFromBYD;
                lastUpdateTime = System.currentTimeMillis();

                Log.d(TAG, String.format("Speed updated: %.1f km/h (source: %s)", speedKmh, dataSourceName));

                if (listener != null) {
                    final float finalSpeed = (float) speedKmh;
                    handler.post(() -> listener.onSpeedChanged(finalSpeed));
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling speed update", e);
        }
    }

    /**
     * GPS位置变化回调
     */
    @Override
    public void onLocationChanged(Location location) {
        try {
            if (location.hasSpeed()) {
                float speedMs = location.getSpeed();
                double speedKmh = speedMs * 3.6;
                handleSpeedUpdate(speedKmh, false);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing GPS location change", e);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "GPS status changed: " + provider + " -> " + status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "GPS provider enabled: " + provider);
        isGpsEnabled = true;
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "GPS provider disabled: " + provider);
        isGpsEnabled = false;
    }

    /**
     * 设置数据源模式
     * @param mode 数据源模式（GPS_ONLY, BYD_AUTO, BYD_ONLY）
     */
    public void setDataSourceMode(int mode) {
        if (mode != currentDataSourceMode) {
            currentDataSourceMode = mode;
            sharedPreferences.edit()
                    .putInt(Constants.PREF_DATA_SOURCE_MODE, mode)
                    .apply();
            Log.d(TAG, "Data source mode changed to: " + getModeDisplayName(mode));
        }
    }

    /**
     * 获取当前数据源模式
     * @return 当前数据源模式
     */
    public int getDataSourceMode() {
        return currentDataSourceMode;
    }

    /**
     * 获取数据源模式的显示名称
     * @param mode 数据源模式
     * @return 显示名称
     */
    public static String getModeDisplayName(int mode) {
        switch (mode) {
            case Constants.DATA_SOURCE_GPS_ONLY:
                return "GPS Only";
            case Constants.DATA_SOURCE_BYD_AUTO:
                return "BYD (Auto fallback to GPS)";
            case Constants.DATA_SOURCE_BYD_ONLY:
                return "BYD Only";
            default:
                return "Unknown";
        }
    }

    /**
     * 获取当前车速
     * @return 当前车速（km/h）
     */
    public double getCurrentSpeed() {
        return currentSpeedKmh;
    }

    /**
     * 获取当前速度的来源
     * @return true表示来自BYD，false表示来自GPS
     */
    public boolean isSpeedFromBYD() {
        return speedFromBYD;
    }

    /**
     * 检查数据是否新鲜
     * @param maxAgeMs 最大年龄（毫秒）
     * @return 是否新鲜
     */
    public boolean isDataFresh(long maxAgeMs) {
        return (System.currentTimeMillis() - lastUpdateTime) < maxAgeMs;
    }

    /**
     * 手动设置车速（用于测试）
     * @param speedKmh 车速（km/h）
     */
    public void setManualSpeed(double speedKmh) {
        Log.d(TAG, "Manual speed set: " + speedKmh + " km/h");
        handleSpeedUpdate(speedKmh, false);
    }

    /**
     * 停止服务
     */
    public void stop() {
        try {
            if (locationManager != null && ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(this);
            }

            if (isBydApiAvailable) {
                // if (bydSpeedDevice != null && bydSpeedListener != null) {
                //     bydSpeedDevice.unregisterBYDAutoSpeedListener(bydSpeedListener);
                // }
                Log.d(TAG, "BYD Auto API stopped");
            }

            Log.d(TAG, "Speed data service stopped");

        } catch (Exception e) {
            Log.e(TAG, "Error stopping speed data service", e);
        }
    }

    /**
     * 检查BYD API是否可用
     * @return 是否可用
     */
    public boolean isBydApiAvailable() {
        return isBydApiAvailable;
    }

    /**
     * 检查GPS是否启用
     * @return 是否启用
     */
    public boolean isGpsEnabled() {
        return isGpsEnabled;
    }
}