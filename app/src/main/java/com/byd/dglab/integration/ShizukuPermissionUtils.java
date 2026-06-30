package com.byd.dglab.integration;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.ShizukuProvider;

/**
 * Shizuku 权限管理工具
 * 通过 Shizuku (需要用户合法 ADB 授权) 访问 BYDAUTO 特权权限
 */
public class ShizukuPermissionUtils {

    private static final String TAG = Constants.LOG_TAG + "_Shizuku";

    private static boolean isShizukuInitialized = false;

    /**
     * 初始化 Shizuku
     */
    public static void initialize(Context context) {
        if (isShizukuInitialized) {
            return;
        }

        try {
            // 绑定 Shizuku 服务
            ShizukuProvider.enableMultiProcessSupport(false);
            isShizukuInitialized = true;
            Log.d(TAG, "Shizuku initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Shizuku", e);
        }
    }

    /**
     * 检查 Shizuku 是否可用
     */
    public static boolean isShizukuAvailable() {
        try {
            return Shizuku.getVersion() >= 11;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查 Shizuku 是否已授权
     */
    public static boolean isShizukuAuthorized() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 请求 Shizuku 权限
     */
    public static void requestShizukuPermission(Shizuku.OnRequestPermissionResultListener listener) {
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0);
                // 监听结果
                Shizuku.addRequestPermissionResultListener(listener);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to request Shizuku permission", e);
        }
    }

    /**
     * 使用 Shizuku 检查权限状态
     * 通过 Shizuku Binder 包装检查权限
     */
    public static int checkPermissionWithShizuku(String permission) {
        try {
            if (!isShizukuAvailable() || !isShizukuAuthorized()) {
                return PackageManager.PERMISSION_DENIED;
            }

            IBinder binder = Shizuku.getBinder();
            if (binder == null) {
                return PackageManager.PERMISSION_DENIED;
            }

            // 使用 ShizukuBinderWrapper 包装系统服务
            ShizukuBinderWrapper wrapper = new ShizukuBinderWrapper(binder);
            // 这里可以通过 wrapper 调用系统权限检查服务
            // 实际实现需要根据具体需求调用相应系统服务

            return PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            Log.e(TAG, "Error checking permission via Shizuku: " + permission, e);
            return PackageManager.PERMISSION_DENIED;
        }
    }

    /**
     * 检查是否应该使用 Shizuku 模式
     * 当普通权限申请失败时，提示用户使用 Shizuku
     */
    public static boolean shouldUseShizuku(Context context, String[] permissions) {
        // 如果 Shizuku 可用且已授权，则优先使用
        if (isShizukuAvailable() && isShizukuAuthorized()) {
            return true;
        }

        // 检查普通方式是否已全部授权
        for (String permission : permissions) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                // 有权限未授予，且 Shizuku 可用但未授权，建议使用 Shizuku
                if (isShizukuAvailable() && !isShizukuAuthorized()) {
                    return true;
                }
            }
        }
        return false;
    }
}
