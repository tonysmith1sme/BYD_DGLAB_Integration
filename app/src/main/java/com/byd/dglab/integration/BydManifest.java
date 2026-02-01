package com.byd.dglab.integration;

/**
 * BYD车机系统权限常量定义
 * 用于访问BYD车机系统中的各项功能需要的权限
 */
public final class BydManifest {
    private BydManifest() {
        throw new RuntimeException("Cannot instantiate BydManifest");
    }

    public static final class permission {
        // BYD车机系统权限常量

        /** 车身系统权限 */
        public static final String BYDAUTO_BODYWORK_COMMON = "android.permission.BYDAUTO_BODYWORK_COMMON";
        public static final String BYDAUTO_BODYWORK_GET = "android.permission.BYDAUTO_BODYWORK_GET";
        public static final String BYDAUTO_BODYWORK_SET = "android.permission.BYDAUTO_BODYWORK_SET";

        /** 空调系统权限 */
        public static final String BYDAUTO_AC_COMMON = "android.permission.BYDAUTO_AC_COMMON";
        public static final String BYDAUTO_AC_GET = "android.permission.BYDAUTO_AC_GET";
        public static final String BYDAUTO_AC_SET = "android.permission.BYDAUTO_AC_SET";

        /** 全景摄像头权限 */
        public static final String BYDAUTO_PANORAMA_COMMON = "android.permission.BYDAUTO_PANORAMA_COMMON";
        public static final String BYDAUTO_PANORAMA_GET = "android.permission.BYDAUTO_PANORAMA_GET";

        /** 仪表板权限 */
        public static final String BYDAUTO_INSTRUMENT_COMMON = "android.permission.BYDAUTO_INSTRUMENT_COMMON";
        public static final String BYDAUTO_INSTRUMENT_GET = "android.permission.BYDAUTO_INSTRUMENT_GET";

        /** 门锁系统权限 */
        public static final String BYDAUTO_DOOR_LOCK_COMMON = "android.permission.BYDAUTO_DOOR_LOCK_COMMON";
        public static final String BYDAUTO_DOOR_LOCK_GET = "android.permission.BYDAUTO_DOOR_LOCK_GET";
        public static final String BYDAUTO_DOOR_LOCK_SET = "android.permission.BYDAUTO_DOOR_LOCK_SET";

        /** 设置系统权限 */
        public static final String BYDAUTO_SETTING_COMMON = "android.permission.BYDAUTO_SETTING_COMMON";
        public static final String BYDAUTO_SETTING_GET = "android.permission.BYDAUTO_SETTING_GET";
        public static final String BYDAUTO_SETTING_SET = "android.permission.BYDAUTO_SETTING_SET";

        /** 引擎系统权限 */
        public static final String BYDAUTO_ENGINE_COMMON = "android.permission.BYDAUTO_ENGINE_COMMON";
        public static final String BYDAUTO_ENGINE_GET = "android.permission.BYDAUTO_ENGINE_GET";

        /** 统计数据权限 */
        public static final String BYDAUTO_STATISTIC_COMMON = "android.permission.BYDAUTO_STATISTIC_COMMON";
        public static final String BYDAUTO_STATISTIC_GET = "android.permission.BYDAUTO_STATISTIC_GET";
    }
}
