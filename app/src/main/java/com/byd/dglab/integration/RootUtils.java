package com.byd.dglab.integration;

import android.util.Log;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Root 权限工具类
 * 用于通过 Root 权限执行系统命令，如强行授予权限
 */
public class RootUtils {
    private static final String TAG = Constants.LOG_TAG + "_Root";

    /**
     * 检查设备是否具有 Root 权限
     * @return 是否有 Root 权限
     */
    public static boolean isRootAvailable() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("exit\n");
            os.flush();
            int exitValue = process.waitFor();
            return exitValue == 0;
        } catch (Exception e) {
            Log.e(TAG, "Root check failed", e);
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * 通过 Root 权限授予应用权限
     * @param packageName 应用包名
     * @param permissions 权限列表
     * @return 是否全部执行成功
     */
    public static boolean grantPermissionsViaRoot(String packageName, String[] permissions) {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());

            for (String permission : permissions) {
                String cmd = "pm grant " + packageName + " " + permission + "\n";
                os.writeBytes(cmd);
                Log.d(TAG, "Executing: " + cmd.trim());
            }

            os.writeBytes("exit\n");
            os.flush();
            int exitValue = process.waitFor();
            return exitValue == 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to grant permissions via root", e);
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams", e);
            }
        }
    }
}
