package com.byd.dglab.integration;

import android.content.Context;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

/**
 * 权限检查工具类
 * 用于检查和请求应用所需的权限
 */
public class PermissionUtils {

    /**
     * 检查是否需要请求权限
     * @param context 应用上下文
     * @param permissions 权限数组
     * @return 如果有任何权限未授予，返回true，否则返回false
     */
    public static boolean needRequestPermission(Context context, String[] permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查所有权限是否都已授予
     * @param context 应用上下文
     * @param permissions 权限数组
     * @return 所有权限都已授予返回true，否则返回false
     */
    public static boolean allPermissionsGranted(Context context, String[] permissions) {
        return !needRequestPermission(context, permissions);
    }

    /**
     * 获取未授予的权限列表
     * @param context 应用上下文
     * @param permissions 权限数组
     * @return 未授予的权限列表
     */
    public static String[] getNotGrantedPermissions(Context context, String[] permissions) {
        if (permissions == null || permissions.length == 0) {
            return new String[0];
        }

        java.util.List<String> notGranted = new java.util.ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                notGranted.add(permission);
            }
        }
        return notGranted.toArray(new String[0]);
    }

    /**
     * 将权限名称转换为可读的描述
     * @param permission 权限名称
     * @return 权限描述
     */
    public static String getPermissionDescription(String permission) {
        switch (permission) {
            case BydManifest.permission.BYDAUTO_BODYWORK_COMMON:
                return "车身系统权限";
            case BydManifest.permission.BYDAUTO_BODYWORK_GET:
                return "车身数据读取权限";
            case BydManifest.permission.BYDAUTO_SPEED_COMMON:
                return "车速系统权限";
            case BydManifest.permission.BYDAUTO_SPEED_GET:
                return "车速数据读取权限";
            case BydManifest.permission.BYDAUTO_AC_COMMON:
                return "空调系统权限";
            case BydManifest.permission.BYDAUTO_INSTRUMENT_COMMON:
                return "仪表板权限";
            case BydManifest.permission.BYDAUTO_DOOR_LOCK_COMMON:
                return "门锁系统权限";
            case BydManifest.permission.BYDAUTO_SETTING_COMMON:
                return "车机设置权限";
            case BydManifest.permission.BYDAUTO_ENGINE_COMMON:
                return "引擎系统权限";
            case BydManifest.permission.BYDAUTO_STATISTIC_COMMON:
                return "统计数据权限";
            case BydManifest.permission.BYDAUTO_PANORAMA_COMMON:
                return "全景摄像头权限";
            case BydManifest.permission.BYDAUTO_LIGHT_COMMON:
                return "车灯系统权限";
            case BydManifest.permission.BYDAUTO_AIR_QUALITY_COMMON:
                return "空气质量权限";
            case "android.permission.INTERNET":
                return "网络权限";
            case "android.permission.ACCESS_NETWORK_STATE":
                return "网络状态权限";
            case "android.permission.ACCESS_FINE_LOCATION":
                return "精确位置权限";
            case "android.permission.ACCESS_COARSE_LOCATION":
                return "粗定位权限";
            case "android.permission.CAMERA":
                return "相机权限";
            default:
                return permission;
        }
    }
}
