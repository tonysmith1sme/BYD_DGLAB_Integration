package com.byd.dglab.integration;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;

/**
 * 权限检查和授予活动
 * 显示应用所需的权限，检查授予状态，并允许用户请求权限
 */
public class PermissionActivity extends AppCompatActivity {

    private static final String TAG = Constants.LOG_TAG + "_Permission";
    private static final int PERMISSION_REQUEST_CODE = 200;

    private TextView titleTextView;
    private LinearLayout permissionItemsContainer;
    private TextView statusTextView;
    private Button requestPermissionsButton;
    private Button returnButton;
    private ScrollView permissionScrollView;

    // BYD车机系统所需的权限
    private static final String[] BYD_PERMISSIONS = {
            BydManifest.permission.BYDAUTO_BODYWORK_COMMON,
            BydManifest.permission.BYDAUTO_AC_COMMON,
            BydManifest.permission.BYDAUTO_INSTRUMENT_COMMON,
            BydManifest.permission.BYDAUTO_DOOR_LOCK_COMMON,
            BydManifest.permission.BYDAUTO_SETTING_COMMON,
            BydManifest.permission.BYDAUTO_ENGINE_COMMON,
    };

    // 标准Android权限
    private static final String[] STANDARD_PERMISSIONS = {
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.CAMERA",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        initializeUI();
        refreshPermissionStatus();
    }

    /**
     * 初始化UI组件
     */
    private void initializeUI() {
        titleTextView = findViewById(R.id.permission_title);
        permissionItemsContainer = findViewById(R.id.permission_items_container);
        statusTextView = findViewById(R.id.permission_status_text);
        requestPermissionsButton = findViewById(R.id.request_permissions_button);
        returnButton = findViewById(R.id.return_button);
        permissionScrollView = findViewById(R.id.permission_scroll_view);

        if (titleTextView != null) {
            titleTextView.setText("权限检查和授予");
        }

        if (requestPermissionsButton != null) {
            requestPermissionsButton.setOnClickListener(v -> requestAllPermissions());
        }

        if (returnButton != null) {
            returnButton.setOnClickListener(v -> finish());
        }
    }

    /**
     * 刷新权限状态显示
     */
    private void refreshPermissionStatus() {
        if (permissionItemsContainer != null) {
            permissionItemsContainer.removeAllViews();
        }

        // 添加BYD权限
        addPermissionSection("BYD车机系统权限", BYD_PERMISSIONS);

        // 添加标准Android权限
        addPermissionSection("标准权限", STANDARD_PERMISSIONS);

        // 更新整体状态
        updateOverallStatus();
    }

    /**
     * 添加权限组
     */
    private void addPermissionSection(String sectionTitle, String[] permissions) {
        // 添加章节标题
        TextView sectionTitleView = new TextView(this);
        sectionTitleView.setText(sectionTitle);
        sectionTitleView.setTextSize(16);
        sectionTitleView.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitleView.setPadding(16, 24, 16, 8);
        sectionTitleView.setTextColor(getResources().getColor(android.R.color.black));
        permissionItemsContainer.addView(sectionTitleView);

        // 添加权限项
        for (String permission : permissions) {
            addPermissionItem(permission);
        }
    }

    /**
     * 添加单个权限项
     */
    private void addPermissionItem(String permission) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(16, 12, 16, 12);

        // 权限名称和状态
        TextView permissionView = new TextView(this);
        String description = PermissionUtils.getPermissionDescription(permission);
        boolean isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
        String statusText = isGranted ? "✓ " : "✗ ";
        permissionView.setText(statusText + description);
        permissionView.setTextSize(14);
        if (isGranted) {
            permissionView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            permissionView.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 4, 0, 4);
        itemLayout.addView(permissionView, params);

        permissionItemsContainer.addView(itemLayout);
    }

    /**
     * 更新整体权限状态
     */
    private void updateOverallStatus() {
        boolean allBydGranted = PermissionUtils.allPermissionsGranted(this, BYD_PERMISSIONS);
        boolean allStandardGranted = PermissionUtils.allPermissionsGranted(this, STANDARD_PERMISSIONS);

        String status;
        if (allBydGranted && allStandardGranted) {
            status = "✓ 所有权限已授予";
            statusTextView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            status = "✗ 部分权限未授予";
            statusTextView.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }

        if (statusTextView != null) {
            statusTextView.setText(status);
        }

        // 根据权限状态更新按钮
        if (requestPermissionsButton != null) {
            if (allBydGranted && allStandardGranted) {
                requestPermissionsButton.setText("权限检查完成");
                requestPermissionsButton.setEnabled(false);
            } else {
                requestPermissionsButton.setText("请求权限授予");
                requestPermissionsButton.setEnabled(true);
            }
        }

        Log.d(TAG, "BYD权限: " + (allBydGranted ? "已授予" : "未授予") +
                ", 标准权限: " + (allStandardGranted ? "已授予" : "未授予"));
    }

    /**
     * 请求所有权限
     */
    private void requestAllPermissions() {
        // 首先请求BYD权限
        String[] notGrantedBydPermissions = PermissionUtils.getNotGrantedPermissions(this, BYD_PERMISSIONS);
        if (notGrantedBydPermissions.length > 0) {
            Log.d(TAG, "请求BYD权限: " + notGrantedBydPermissions.length + "个");
            ActivityCompat.requestPermissions(this, notGrantedBydPermissions, PERMISSION_REQUEST_CODE);
            return;
        }

        // 如果BYD权限已授予，请求标准权限
        String[] notGrantedStandardPermissions = PermissionUtils.getNotGrantedPermissions(this, STANDARD_PERMISSIONS);
        if (notGrantedStandardPermissions.length > 0) {
            Log.d(TAG, "请求标准权限: " + notGrantedStandardPermissions.length + "个");
            ActivityCompat.requestPermissions(this, notGrantedStandardPermissions, PERMISSION_REQUEST_CODE);
            return;
        }

        // 所有权限都已授予
        Toast.makeText(this, "所有权限都已授予", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "所有权限已授予");
    }

    /**
     * 处理权限请求结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            int grantedCount = 0;
            int deniedCount = 0;

            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    grantedCount++;
                } else {
                    deniedCount++;
                }
            }

            String message = String.format("已授予 %d 个权限, 拒绝 %d 个权限", grantedCount, deniedCount);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            Log.d(TAG, message);

            // 刷新权限状态显示
            refreshPermissionStatus();

            // 如果还有权限未授予，继续请求
            if (deniedCount > 0) {
                String[] notGrantedPermissions = PermissionUtils.getNotGrantedPermissions(
                        this,
                        getAllPermissions()
                );
                if (notGrantedPermissions.length > 0) {
                    Toast.makeText(this, "部分权限被拒绝，请在系统设置中手动授予", Toast.LENGTH_LONG).show();
                    Log.w(TAG, "部分权限被拒绝: " + notGrantedPermissions.length + "个");
                }
            }
        }
    }

    /**
     * 获取所有权限列表
     */
    private String[] getAllPermissions() {
        java.util.List<String> allPermissions = new java.util.ArrayList<>();
        for (String perm : BYD_PERMISSIONS) {
            allPermissions.add(perm);
        }
        for (String perm : STANDARD_PERMISSIONS) {
            allPermissions.add(perm);
        }
        return allPermissions.toArray(new String[0]);
    }
}
