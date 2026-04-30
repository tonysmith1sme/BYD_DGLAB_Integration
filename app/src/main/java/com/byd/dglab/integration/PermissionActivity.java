package com.byd.dglab.integration;

import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

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

    // BYD车机系统所需的权限 - 根据文档，只有这些类需要申请动态权限：
    // 空调、车身、门锁、仪表、全景影像、设置
    private static final String[] BYD_PERMISSIONS = {
            BydManifest.permission.BYDAUTO_BODYWORK_COMMON,
            BydManifest.permission.BYDAUTO_AC_COMMON,
            BydManifest.permission.BYDAUTO_INSTRUMENT_COMMON,
            BydManifest.permission.BYDAUTO_DOOR_LOCK_COMMON,
            BydManifest.permission.BYDAUTO_SETTING_COMMON,
            BydManifest.permission.BYDAUTO_PANORAMA_COMMON,
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
        DynamicColors.applyToActivityIfAvailable(this);
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
            titleTextView.setText(R.string.permission_title);
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
        addPermissionSection(getString(R.string.permission_section_byd), BYD_PERMISSIONS);

        // 添加标准Android权限
        addPermissionSection(getString(R.string.permission_section_standard), STANDARD_PERMISSIONS);

        // 更新整体状态
        updateOverallStatus();
    }

    /**
     * 添加权限组
     */
    private void addPermissionSection(String sectionTitle, String[] permissions) {
        TextView sectionTitleView = new TextView(this);
        sectionTitleView.setText(sectionTitle);
        sectionTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sectionTitleView.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitleView.setPadding(0, 16, 0, 8);
        sectionTitleView.setTextColor(MaterialColors.getColor(sectionTitleView, com.google.android.material.R.attr.colorOnSurface));
        permissionItemsContainer.addView(sectionTitleView);

        for (String permission : permissions) {
            addPermissionItem(permission);
        }
    }

    /**
     * 添加单个权限项
     */
    private void addPermissionItem(String permission) {
        MaterialCardView itemCard = new MaterialCardView(this);
        itemCard.setCardBackgroundColor(MaterialColors.getColor(itemCard, com.google.android.material.R.attr.colorSurfaceBright));
        itemCard.setStrokeColor(MaterialColors.getColor(itemCard, com.google.android.material.R.attr.colorOutlineVariant));
        itemCard.setStrokeWidth(dpToPx(1));
        itemCard.setRadius(dpToPx(20));

        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        TextView permissionView = new TextView(this);
        String description = PermissionUtils.getPermissionDescription(permission);
        boolean isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
        String statusText = isGranted ? "✓ " : "✗ ";
        permissionView.setText(statusText + description);
        permissionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        permissionView.setTextColor(MaterialColors.getColor(permissionView,
            isGranted ? com.google.android.material.R.attr.colorPrimary : com.google.android.material.R.attr.colorError));

        TextView chipView = new TextView(this);
        chipView.setText(isGranted ? "已授权" : "待授权");
        chipView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chipView.setTypeface(null, android.graphics.Typeface.BOLD);
        chipView.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        if (isGranted) {
            chipView.setBackgroundTintList(ColorStateList.valueOf(
                MaterialColors.getColor(chipView, com.google.android.material.R.attr.colorPrimaryContainer)));
            chipView.setTextColor(MaterialColors.getColor(chipView, com.google.android.material.R.attr.colorOnPrimaryContainer));
        } else {
            chipView.setBackgroundTintList(ColorStateList.valueOf(
                MaterialColors.getColor(chipView, com.google.android.material.R.attr.colorErrorContainer)));
            chipView.setTextColor(MaterialColors.getColor(chipView, com.google.android.material.R.attr.colorOnErrorContainer));
        }
        chipView.setBackgroundResource(R.drawable.shape_status_badge_rounded);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.weight = 1f;
        itemLayout.addView(permissionView, params);
        itemLayout.addView(chipView);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(12));
        itemCard.addView(itemLayout);

        permissionItemsContainer.addView(itemCard, cardParams);
    }

    /**
     * 更新整体权限状态
     */
    private void updateOverallStatus() {
        boolean allBydGranted = PermissionUtils.allPermissionsGranted(this, BYD_PERMISSIONS);
        boolean allStandardGranted = PermissionUtils.allPermissionsGranted(this, STANDARD_PERMISSIONS);

        String status;
        int backgroundColor;
        int textColor;
        if (allBydGranted && allStandardGranted) {
            status = getString(R.string.permission_all_granted);
            backgroundColor = MaterialColors.getColor(statusTextView, com.google.android.material.R.attr.colorPrimaryContainer);
            textColor = MaterialColors.getColor(statusTextView, com.google.android.material.R.attr.colorOnPrimaryContainer);
        } else {
            status = getString(R.string.permission_partial_missing);
            backgroundColor = MaterialColors.getColor(statusTextView, com.google.android.material.R.attr.colorErrorContainer);
            textColor = MaterialColors.getColor(statusTextView, com.google.android.material.R.attr.colorOnErrorContainer);
        }

        if (statusTextView != null) {
            statusTextView.setText(status);
            statusTextView.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
            statusTextView.setTextColor(textColor);
        }

        if (requestPermissionsButton != null) {
            if (allBydGranted && allStandardGranted) {
                requestPermissionsButton.setText(R.string.permission_check_complete);
                requestPermissionsButton.setEnabled(false);
            } else {
                requestPermissionsButton.setText(R.string.permission_request_now);
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

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        ));
    }
}
