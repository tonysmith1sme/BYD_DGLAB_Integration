package com.byd.dglab.integration;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.text.TextUtils;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.res.ColorStateList;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

/**
 * 主活动类
 * 负责UI管理和服务协调
 */
public class MainActivity extends AppCompatActivity implements SpeedChangeListener, ControlCommandListener {

    private static final String TAG = Constants.LOG_TAG + "_Main";

    // UI组件
    private TextView speedTextView;
    private TextView intensityTextView;
    private TextView frequencyTextView;
    private TextView statusTextView;
    private TextView logTextView;
    private TextView qrHintTextView;
    private ScrollView logScrollView;
    private android.widget.ImageView qrCodeImageView;
    private EditText reportMultiplierEditText;
    private EditText waveformDurationEditText;
    private Spinner waveformSpinnerA;
    private Spinner waveformSpinnerB;
    private com.google.android.material.materialswitch.MaterialSwitch waveformThrottleSwitch;
    private Button testWaveformButton;
    private Button connectButton;
    private Button disconnectButton;
    private EditText serverUrlEditText;
    private Button scanQrButton;
    private Button applyUrlButton;
    private Button permissionCheckButton;
    private android.widget.RadioGroup dataSourceRadioGroup;
    private android.widget.RadioButton gpsOnlyRadio;
    private android.widget.RadioButton bydAutoRadio;
    private android.widget.RadioButton bydOnlyRadio;
    private TextView dataSourceStatusTextView;
    private com.google.android.material.materialswitch.MaterialSwitch rootGrantSwitch;

    // 服务组件
    private SpeedDataService speedDataService;
    private WebSocketService webSocketService;
    private SpeedToControlConverter converter;

    // 权限请求码
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int BYD_PERMISSION_REQUEST_CODE = 102;
    private static final String PREFS_NAME = "WebSocketConfig";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_REPORT_MULTIPLIER = "report_multiplier";
    private static final String KEY_WAVEFORM_DURATION = "waveform_duration";
    private static final String KEY_WAVEFORM_PRESET_A = "waveform_preset_a";
    private static final String KEY_WAVEFORM_PRESET_B = "waveform_preset_b";
    private static final String KEY_WAVEFORM_THROTTLE_ENABLED = "waveform_throttle_enabled";
    private static final String KEY_DISCLAIMER_SHOWN = "disclaimer_shown";

    // BYD车机权限列表 - 根据文档，只有这些类需要申请动态权限
    private static final String[] BYD_PERMISSIONS = {
            BydManifest.permission.BYDAUTO_BODYWORK_COMMON,
            BydManifest.permission.BYDAUTO_AC_COMMON,
            BydManifest.permission.BYDAUTO_PANORAMA_COMMON,
            BydManifest.permission.BYDAUTO_SETTING_COMMON,
            BydManifest.permission.BYDAUTO_INSTRUMENT_COMMON,
            BydManifest.permission.BYDAUTO_DOOR_LOCK_COMMON
    };

    // SharedPreferences
    private SharedPreferences sharedPreferences;
    private long lastWaveformSentAt = 0L;
    private int lastWaveformIntensity = Integer.MIN_VALUE;
    private String lastWaveformA = null;
    private String lastWaveformB = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 初始化UI组件
        initializeUI();

        // 初始化服务
        initializeServices();

        // 显示免责声明（首次运行）或直接请求权限
        showDisclaimerIfNeeded();

        Log.d(TAG, "MainActivity created");
    }

    /**
     * 显示免责声明（首次运行）
     */
    private void showDisclaimerIfNeeded() {
        boolean disclaimerShown = sharedPreferences.getBoolean(KEY_DISCLAIMER_SHOWN, false);
        
        if (!disclaimerShown) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("免责声明")
                    .setMessage("为保证行车驾驶安全，行车过程中不建议操作软件，并且由于其软件特殊性不建议将本App相关的智能设备用于主驾上。\n\n" +
                            "免费软件，无偿使用，对于使用过程中造成的任何问题以及影响，本App开发及相关管理、测试人员对此概不负责。\n\n" +
                            "同意请点击确定，否则点击取消退出软件。")
                    .setPositiveButton("确定", (dialog, which) -> {
                        // 记录已显示过免责声明
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean(KEY_DISCLAIMER_SHOWN, true);
                        editor.apply();
                        addLogEntry("已同意免责声明");
                        dialog.dismiss();
                        // 同意后再检查并请求BYD权限
                        checkAndRequestBydPermissions();
                    })
                    .setNegativeButton("取消", (dialog, which) -> {
                        addLogEntry("拒绝免责声明，退出应用");
                        dialog.dismiss();
                        // 延迟退出，让日志显示
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            finish();
                            System.exit(0);
                        }, 500);
                    })
                    .setCancelable(false)
                    .show();
        } else {
            // 已经同意过免责声明，直接检查并请求权限
            checkAndRequestBydPermissions();
        }
    }

    /**
     * 尝试通过Root权限授予权限
     */
    private void tryGrantPermissionsViaRoot() {
        if (!RootUtils.isRootAvailable()) {
            Toast.makeText(this, R.string.root_not_available, Toast.LENGTH_SHORT).show();
            rootGrantSwitch.setChecked(false);
            return;
        }

        addLogEntry("正在尝试通过 Root 权限授予 BYD 车机权限...");
        
        // 开启后台线程执行 Root 命令，避免阻塞 UI
        new Thread(() -> {
            boolean success = RootUtils.grantPermissionsViaRoot(getPackageName(), BYD_PERMISSIONS);
            
            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(this, R.string.root_grant_success, Toast.LENGTH_SHORT).show();
                    addLogEntry("Root 权限授予成功，请手动进入系统设置确认或重启应用以生效");
                    // 刷新权限状态
                    checkAndRequestBydPermissions();
                } else {
                    Toast.makeText(this, R.string.root_grant_failed, Toast.LENGTH_SHORT).show();
                    addLogEntry("Root 权限授予失败");
                    rootGrantSwitch.setChecked(false);
                }
            });
        }).start();
    }

    /**
     * 检查并请求BYD车机权限
     */
    private void checkAndRequestBydPermissions() {
        boolean needBydPermissions = PermissionUtils.needRequestPermission(this, BYD_PERMISSIONS);
        
        if (needBydPermissions) {
            addLogEntry("检测到缺少BYD车机权限，正在请求...");
            ActivityCompat.requestPermissions(this, BYD_PERMISSIONS, BYD_PERMISSION_REQUEST_CODE);
        } else {
            addLogEntry("BYD车机权限已授予");
            // BYD权限已有，继续请求普通权限
            requestPermissions();
        }
    }

    /**
     * 初始化UI组件
     */
    private void initializeUI() {
        speedTextView = findViewById(R.id.speedTextView);
        intensityTextView = findViewById(R.id.intensityTextView);
        frequencyTextView = findViewById(R.id.frequencyTextView);
        statusTextView = findViewById(R.id.statusTextView);
        logTextView = findViewById(R.id.logTextView);
        logScrollView = findViewById(R.id.logScrollView);
        connectButton = findViewById(R.id.connectButton);
        disconnectButton = findViewById(R.id.disconnectButton);
        serverUrlEditText = findViewById(R.id.serverUrlEditText);
        scanQrButton = findViewById(R.id.scanQrButton);
        applyUrlButton = findViewById(R.id.applyUrlButton);
        qrCodeImageView = findViewById(R.id.qrCodeImageView);
        qrHintTextView = findViewById(R.id.qrHintTextView);
        reportMultiplierEditText = findViewById(R.id.reportMultiplierEditText);
        waveformDurationEditText = findViewById(R.id.waveformDurationEditText);
        waveformSpinnerA = findViewById(R.id.waveformSpinnerA);
        waveformSpinnerB = findViewById(R.id.waveformSpinnerB);
        waveformThrottleSwitch = findViewById(R.id.waveformThrottleSwitch);
        testWaveformButton = findViewById(R.id.testWaveformButton);
        permissionCheckButton = findViewById(R.id.permissionCheckButton);
        dataSourceRadioGroup = findViewById(R.id.dataSourceRadioGroup);
        gpsOnlyRadio = findViewById(R.id.gpsOnlyRadio);
        bydAutoRadio = findViewById(R.id.bydAutoRadio);
        bydOnlyRadio = findViewById(R.id.bydOnlyRadio);
        dataSourceStatusTextView = findViewById(R.id.dataSourceStatusTextView);
        rootGrantSwitch = findViewById(R.id.rootGrantSwitch);

        // 加载保存的WebSocket地址
        String savedUrl = sharedPreferences.getString(KEY_SERVER_URL, Constants.SOCKET_SERVER_URL);
        serverUrlEditText.setText(savedUrl);

        float savedMultiplier = sharedPreferences.getFloat(KEY_REPORT_MULTIPLIER, 1.0f);
        reportMultiplierEditText.setText(String.format(Locale.US, "%.1fx", savedMultiplier));
        int savedWaveformDuration = sharedPreferences.getInt(KEY_WAVEFORM_DURATION,
            Constants.WAVEFORM_DURATION_DEFAULT_SECONDS);
        waveformDurationEditText.setText(String.valueOf(savedWaveformDuration));
        initializeWaveformSpinners();
        boolean waveformThrottleEnabled = sharedPreferences.getBoolean(KEY_WAVEFORM_THROTTLE_ENABLED, true);
        waveformThrottleSwitch.setChecked(waveformThrottleEnabled);

        // 设置按钮监听器
        connectButton.setOnClickListener(this::onConnectClicked);
        disconnectButton.setOnClickListener(this::onDisconnectClicked);
        scanQrButton.setOnClickListener(this::onScanQrClicked);
        applyUrlButton.setOnClickListener(this::onApplyUrlClicked);
        permissionCheckButton.setOnClickListener(this::onPermissionCheckClicked);
        testWaveformButton.setOnClickListener(this::onTestWaveformClicked);

        // 设置Root授权开关
        boolean rootGrantEnabled = sharedPreferences.getBoolean(Constants.PREF_ROOT_GRANT_ENABLED, false);
        rootGrantSwitch.setChecked(rootGrantEnabled);
        rootGrantSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(Constants.PREF_ROOT_GRANT_ENABLED, isChecked);
            editor.apply();

            if (isChecked) {
                tryGrantPermissionsViaRoot();
            }
        });

        waveformThrottleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(KEY_WAVEFORM_THROTTLE_ENABLED, isChecked).apply();
            lastWaveformSentAt = 0L;
            lastWaveformIntensity = Integer.MIN_VALUE;
            lastWaveformA = null;
            lastWaveformB = null;
            addLogEntry(isChecked ? "已开启降低波形发送频率" : "已关闭降低波形发送频率");
        });

        // 初始状态
        updateStatus("未连接");
        disconnectButton.setEnabled(false);
        addLogEntry("应用启动");
    }

    private void initializeWaveformSpinners() {
        String[] items = new String[]{
                getString(R.string.waveform_option_a),
                getString(R.string.waveform_option_b),
                getString(R.string.waveform_option_c)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        waveformSpinnerA.setAdapter(adapter);
        waveformSpinnerB.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items));
        ((ArrayAdapter<?>) waveformSpinnerB.getAdapter()).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        bindWaveformSpinner(waveformSpinnerA, KEY_WAVEFORM_PRESET_A, items, getString(R.string.waveform_selector_channel_a));
        bindWaveformSpinner(waveformSpinnerB, KEY_WAVEFORM_PRESET_B, items, getString(R.string.waveform_selector_channel_b));
    }

    private void bindWaveformSpinner(Spinner spinner, String preferenceKey, String[] items, String channelName) {
        int savedIndex = sharedPreferences.getInt(preferenceKey, 0);
        spinner.setSelection(Math.max(0, Math.min(items.length - 1, savedIndex)));
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                sharedPreferences.edit().putInt(preferenceKey, position).apply();
                addLogEntry(channelName + " 波形已切换: " + items[position]);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private String getSelectedWaveformData(Spinner spinner) {
        int selected = spinner.getSelectedItemPosition();
        switch (selected) {
            case 1:
                return Constants.WAVEFORM_B;
            case 2:
                return Constants.WAVEFORM_C;
            case 0:
            default:
                return Constants.WAVEFORM_A;
        }
    }

    private String getSelectedWaveformName(Spinner spinner) {
        int selected = spinner.getSelectedItemPosition();
        switch (selected) {
            case 1:
                return getString(R.string.waveform_option_b);
            case 2:
                return getString(R.string.waveform_option_c);
            case 0:
            default:
                return getString(R.string.waveform_option_a);
        }
    }

    /**
     * 初始化服务组件
     */
    private void initializeServices() {
        try {
            // 创建转换器
            converter = new SpeedToControlConverter();

            // 获取当前配置的WebSocket地址
            String serverUrl = sharedPreferences.getString(KEY_SERVER_URL, Constants.SOCKET_SERVER_URL);

            // 创建车速数据服务
            speedDataService = new SpeedDataService(this, this);

            // 创建WebSocket服务（使用配置的地址）
            webSocketService = new WebSocketService(this, serverUrl);

            // 初始化数据源选择
            initializeDataSourceSelection();

            Log.d(TAG, "Services initialized successfully with URL: " + serverUrl);

        } catch (Exception e) {
            Log.e(TAG, "Error initializing services", e);
            addLogEntry("服务初始化失败: " + e.getMessage());
        }
    }

    /**
     * 初始化数据源选择UI和事件监听
     */
    private void initializeDataSourceSelection() {
        // 根据当前模式选择对应的单选按钮
        int currentMode = speedDataService.getDataSourceMode();
        switch (currentMode) {
            case Constants.DATA_SOURCE_GPS_ONLY:
                gpsOnlyRadio.setChecked(true);
                break;
            case Constants.DATA_SOURCE_BYD_AUTO:
                bydAutoRadio.setChecked(true);
                break;
            case Constants.DATA_SOURCE_BYD_ONLY:
                bydOnlyRadio.setChecked(true);
                break;
        }

        // 更新状态显示
        updateDataSourceStatus(currentMode);

        // 添加事件监听
        dataSourceRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedMode = Constants.DATA_SOURCE_GPS_ONLY;
            String modeName = "";

            if (checkedId == R.id.gpsOnlyRadio) {
                selectedMode = Constants.DATA_SOURCE_GPS_ONLY;
                modeName = "GPS Only";
            } else if (checkedId == R.id.bydAutoRadio) {
                selectedMode = Constants.DATA_SOURCE_BYD_AUTO;
                modeName = "BYD (Auto fallback to GPS)";
            } else if (checkedId == R.id.bydOnlyRadio) {
                selectedMode = Constants.DATA_SOURCE_BYD_ONLY;
                modeName = "BYD Only";
            }

            // 设置新的数据源模式
            speedDataService.setDataSourceMode(selectedMode);
            updateDataSourceStatus(selectedMode);
            addLogEntry("数据源已切换: " + modeName);
        });
    }

    /**
     * 更新数据源状态显示
     * @param mode 当前数据源模式
     */
    private void updateDataSourceStatus(int mode) {
        String statusText = "当前数据源：";
        switch (mode) {
            case Constants.DATA_SOURCE_GPS_ONLY:
                statusText += "GPS";
                break;
            case Constants.DATA_SOURCE_BYD_AUTO:
                statusText += "BYD (GPS备用)";
                break;
            case Constants.DATA_SOURCE_BYD_ONLY:
                statusText += "BYD车机";
                break;
        }
        dataSourceStatusTextView.setText(statusText);
    }

    /**
     * 请求必要权限
     */
    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
        };

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        } else {
            addLogEntry("所有权限已授予");
        }
    }

    /**
     * 连接按钮点击事件
     */
    private void onConnectClicked(View view) {
        addLogEntry("正在启动本地控制服务...");
        updateStatus(getString(R.string.status_service_starting));
        connectButton.setEnabled(false);

        try {
            webSocketService.connect();
        } catch (Exception e) {
            addLogEntry("连接失败: " + e.getMessage());
            connectButton.setEnabled(true);
        }
    }

    /**
     * 断开连接按钮点击事件
     */
    private void onDisconnectClicked(View view) {
        addLogEntry("断开连接...");
        updateStatus(getString(R.string.status_service_stopping));
        disconnectButton.setEnabled(false);

        try {
            webSocketService.disconnect();
        } catch (Exception e) {
            addLogEntry("断开连接失败: " + e.getMessage());
            disconnectButton.setEnabled(true);
        }
    }

    /**
     * 二维码扫描按钮点击事件
     */
    private void onScanQrClicked(View view) {
        renderQrCode(webSocketService.getQrCodeContent());
        addLogEntry("已刷新配对二维码");
    }

    /**
     * 应用WebSocket地址按钮点击事件
     */
    private void onApplyUrlClicked(View view) {
        String url = normalizeWebSocketEndpoint(serverUrlEditText.getText().toString());

        if (url.isEmpty()) {
            Toast.makeText(this, "请输入局域网服务地址或端口", Toast.LENGTH_SHORT).show();
            addLogEntry("错误：局域网服务地址为空");
            return;
        }

        // 验证URL格式
        if (!isValidWebSocketUrl(url)) {
            Toast.makeText(this, "无效的局域网服务地址格式", Toast.LENGTH_SHORT).show();
            addLogEntry("错误：无效的局域网服务地址 - " + url);
            return;
        }

        serverUrlEditText.setText(url);

        // 保存到SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_SERVER_URL, url);
        editor.apply();

        // 重新初始化WebSocket服务
        try {
            if (webSocketService.isConnected()) {
                webSocketService.disconnect();
            }
            webSocketService = new WebSocketService(this, url);
            Toast.makeText(this, "局域网服务地址已更新", Toast.LENGTH_SHORT).show();
            addLogEntry("局域网服务地址已保存并更新: " + url);
        } catch (Exception e) {
            addLogEntry("更新局域网服务地址失败: " + e.getMessage());
            Log.e(TAG, "Error updating WebSocket service", e);
        }
    }

    private float getReportMultiplier() {
        try {
            String raw = reportMultiplierEditText.getText().toString().trim().toLowerCase(Locale.US).replace("x", "");
            if (raw.isEmpty()) {
                return 1.0f;
            }
            float parsed = Float.parseFloat(raw);
            return Math.max(0.1f, Math.min(10.0f, parsed));
        } catch (Exception e) {
            return 1.0f;
        }
    }

    private void saveReportMultiplier(float multiplier) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(KEY_REPORT_MULTIPLIER, multiplier);
        editor.apply();
    }

    private int getWaveformDurationSeconds() {
        try {
            String raw = waveformDurationEditText.getText().toString().trim();
            if (raw.isEmpty()) {
                return Constants.WAVEFORM_DURATION_DEFAULT_SECONDS;
            }
            int parsed = Integer.parseInt(raw);
            return Math.max(Constants.WAVEFORM_DURATION_MIN_SECONDS,
                    Math.min(Constants.WAVEFORM_DURATION_MAX_SECONDS, parsed));
        } catch (Exception e) {
            return Constants.WAVEFORM_DURATION_DEFAULT_SECONDS;
        }
    }

    private void saveWaveformDurationSeconds(int durationSeconds) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_WAVEFORM_DURATION, durationSeconds);
        editor.apply();
    }

    private boolean shouldSendWaveform(int reportedIntensity, String waveformA, String waveformB) {
        boolean throttleEnabled = waveformThrottleSwitch != null && waveformThrottleSwitch.isChecked();
        if (!throttleEnabled) {
            return true;
        }

        long now = System.currentTimeMillis();
        boolean waveformChanged = !safeEquals(lastWaveformA, waveformA) || !safeEquals(lastWaveformB, waveformB);
        boolean intensityChangedEnough = lastWaveformIntensity == Integer.MIN_VALUE
                || Math.abs(reportedIntensity - lastWaveformIntensity) >= Constants.WAVEFORM_THROTTLE_INTENSITY_DELTA;
        boolean intervalElapsed = lastWaveformSentAt == 0L
                || now - lastWaveformSentAt >= Constants.WAVEFORM_THROTTLE_INTERVAL_MS;

        return waveformChanged || intensityChangedEnough || intervalElapsed;
    }

    private void markWaveformSent(int reportedIntensity, String waveformA, String waveformB) {
        lastWaveformSentAt = System.currentTimeMillis();
        lastWaveformIntensity = reportedIntensity;
        lastWaveformA = waveformA;
        lastWaveformB = waveformB;
    }

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    /**
     * 验证WebSocket地址格式
     */
    private boolean isValidWebSocketUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return ("ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme))
                    && !TextUtils.isEmpty(host)
                    && uri.getPath().isEmpty();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String normalizeWebSocketEndpoint(String rawInput) {
        if (rawInput == null) {
            return "";
        }

        String value = rawInput.trim();
        if (value.isEmpty()) {
            return "";
        }

        if (value.startsWith("ws://") || value.startsWith("wss://")) {
            return stripTrailingSlash(value);
        }

        if (value.matches("^\\d{2,5}$")) {
            return "ws://" + Constants.SOCKET_SERVER_BIND_HOST + ":" + value;
        }

        if (value.startsWith("http://") || value.startsWith("https://")) {
            return "";
        }

        if (value.contains(":")) {
            return stripTrailingSlash("ws://" + value);
        }

        return stripTrailingSlash(String.format(Locale.US, "ws://%s:%d", value, Constants.DG_LAB_SOCKET_SERVER_PORT));
    }

    private String stripTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 权限检查按钮点击事件
     */
    private void onPermissionCheckClicked(View view) {
        // 打开权限检查活动
        android.content.Intent intent = new android.content.Intent(MainActivity.this, PermissionActivity.class);
        startActivity(intent);
        addLogEntry("打开权限检查界面");
    }

    private void onTestWaveformClicked(View view) {
        if (!webSocketService.isConnected()) {
            Toast.makeText(this, "请先启动服务并完成配对", Toast.LENGTH_SHORT).show();
            addLogEntry("测试波形失败：DG-LAB APP 尚未连接");
            return;
        }

        String waveformA = getSelectedWaveformData(waveformSpinnerA);
        String waveformB = getSelectedWaveformData(waveformSpinnerB);
        int waveformDurationSeconds = getWaveformDurationSeconds();
        int currentIntensity = getDisplayedIntensity();
        if (currentIntensity <= 0) {
            currentIntensity = Constants.WAVEFORM_TEST_MIN_INTENSITY;
            webSocketService.sendStrengthCommand(currentIntensity, currentIntensity);
            intensityTextView.setText(String.valueOf(currentIntensity));
            addLogEntry("当前强度为 0，已自动提升到测试强度: " + currentIntensity);
        }
        saveWaveformDurationSeconds(waveformDurationSeconds);
        waveformDurationEditText.setText(String.valueOf(waveformDurationSeconds));
        webSocketService.sendWaveformCommand(1, waveformA, waveformDurationSeconds);
        webSocketService.sendWaveformCommand(2, waveformB, waveformDurationSeconds);
        markWaveformSent(lastWaveformIntensity == Integer.MIN_VALUE ? 0 : lastWaveformIntensity, waveformA, waveformB);
        addLogEntry("已手动测试当前波形: A=" + getSelectedWaveformName(waveformSpinnerA)
            + ", B=" + getSelectedWaveformName(waveformSpinnerB)
            + ", 持续=" + waveformDurationSeconds + "s");
    }

    private int getDisplayedIntensity() {
        try {
            return Integer.parseInt(intensityTextView.getText().toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 车速变化回调
     */
    @Override
    public void onSpeedChanged(float speedKmH) {
        runOnUiThread(() -> {
            // 更新UI显示
            speedTextView.setText(String.format("%.1f", speedKmH));

            // 转换为控制参数
            int intensity = converter.convertSpeedToIntensity(speedKmH);
            int frequency = converter.convertSpeedToFrequency(speedKmH);

            float reportMultiplier = getReportMultiplier();
            saveReportMultiplier(reportMultiplier);
                int waveformDurationSeconds = getWaveformDurationSeconds();
                saveWaveformDurationSeconds(waveformDurationSeconds);

            int reportedIntensity = Math.max(Constants.INTENSITY_MIN,
                    Math.min(Constants.INTENSITY_MAX, Math.round(intensity * reportMultiplier)));
            int reportedFrequency = Math.max(Constants.FREQUENCY_MIN,
                    Math.min(Constants.FREQUENCY_MAX, Math.round(frequency * reportMultiplier)));

            intensityTextView.setText(String.valueOf(reportedIntensity));
            frequencyTextView.setText(String.valueOf(reportedFrequency));

            // 发送控制命令（如果已连接）
            if (webSocketService.isConnected()) {
                webSocketService.sendStrengthCommand(reportedIntensity, reportedIntensity);
                String waveformA = getSelectedWaveformData(waveformSpinnerA);
                String waveformB = getSelectedWaveformData(waveformSpinnerB);
                if (shouldSendWaveform(reportedIntensity, waveformA, waveformB)) {
                    webSocketService.sendWaveformCommand(1, waveformA, waveformDurationSeconds);
                    webSocketService.sendWaveformCommand(2, waveformB, waveformDurationSeconds);
                    markWaveformSent(reportedIntensity, waveformA, waveformB);
                }
            }

            // 获取当前数据源
            String dataSource = speedDataService.isSpeedFromBYD() ? "BYD" : "GPS";

                addLogEntry(String.format(Locale.US,
                    "车速更新: %.1f km/h (来自%s) -> 强度:%d, 频率:%d Hz, 倍率:%.1fx, 波形A:%s, 波形B:%s, 波形时长:%ds",
                        speedKmH, dataSource, reportedIntensity, reportedFrequency, reportMultiplier,
                    getSelectedWaveformName(waveformSpinnerA), getSelectedWaveformName(waveformSpinnerB), waveformDurationSeconds));
        });
    }

    @Override
    public void onSpeedError(String message) {
        runOnUiThread(() -> addLogEntry("GPS 提示: " + message));
    }


    /**
     * 命令发送回调
     */
    @Override
    public void onCommandSent(String commandType, String commandData) {
        runOnUiThread(() -> {
            addLogEntry("发送命令: " + commandType);
        });
    }

    /**
     * 响应接收回调
     */
    @Override
    public void onResponseReceived(String responseType, String responseData) {
        runOnUiThread(() -> {
            if ("connection".equals(responseType)) {
                if ("opened".equals(responseData)) {
                    updateStatus(getString(R.string.status_service_running));
                    connectButton.setEnabled(false);
                    disconnectButton.setEnabled(true);
                    addLogEntry("本地控制服务已启动，请使用 DG-LAB APP 扫描二维码");
                } else if ("closed".equals(responseData)) {
                    updateStatus(getString(R.string.status_service_stopped));
                    connectButton.setEnabled(true);
                    disconnectButton.setEnabled(false);
                    qrCodeImageView.setImageBitmap(null);
                    qrHintTextView.setText(getString(R.string.qr_hint_idle));
                    addLogEntry("连接已断开");
                }
            } else if ("qrCode".equals(responseType)) {
                renderQrCode(responseData);
            } else if (Constants.MSG_TYPE_BIND.equals(responseType)) {
                handleBindResponse(responseData);
            } else if (Constants.MSG_TYPE_MESSAGE.equals(responseType)) {
                handleForwardedAppMessage(responseData);
            } else if ("diagnostic".equals(responseType)) {
                addLogEntry("诊断: " + responseData);
            } else if (Constants.MSG_TYPE_BREAK.equals(responseType)) {
                updateStatus(getString(R.string.status_service_stopped));
                disconnectButton.setEnabled(false);
                qrHintTextView.setText(getString(R.string.qr_hint_idle));
                addLogEntry("DG-LAB APP 已断开连接");
            } else {
                addLogEntry("收到响应: " + responseType);
            }
        });
    }

    private void handleBindResponse(String responseData) {
        Map<String, Object> parsed = new SocketProtocolHelper().parseJsonResponse(responseData);
        if (parsed == null) {
            addLogEntry("绑定响应解析失败");
            return;
        }

        Object message = parsed.get("message");
        if (Constants.RESULT_SUCCESS.equals(message)) {
            qrHintTextView.setText(getString(R.string.qr_hint_connected));
            addLogEntry("DG-LAB APP 扫码配对成功");
        } else {
            addLogEntry("绑定失败: " + message);
        }
    }

    private void handleForwardedAppMessage(String responseData) {
        Map<String, Object> parsed = new SocketProtocolHelper().parseJsonResponse(responseData);
        if (parsed == null) {
            addLogEntry("APP 消息解析失败");
            return;
        }

        Object message = parsed.get("message");
        if (message != null) {
            addLogEntry("收到 APP 消息: " + message);
        }
    }

    private void renderQrCode(String qrContent) {
        if (qrContent == null || qrContent.isEmpty()) {
            qrHintTextView.setText(getString(R.string.qr_hint_idle));
            qrCodeImageView.setImageBitmap(null);
            return;
        }

        try {
            BitMatrix bitMatrix = new QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, 640, 640);
            Bitmap bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.RGB_565);
            for (int x = 0; x < 640; x++) {
                for (int y = 0; y < 640; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            qrCodeImageView.setImageBitmap(bitmap);
            qrHintTextView.setText(getString(R.string.qr_hint_ready, extractAdvertisedHost(qrContent)));
            addLogEntry("已生成配对二维码: " + qrContent);
        } catch (WriterException e) {
            addLogEntry("生成二维码失败: " + e.getMessage());
            Log.e(TAG, "QR generation failed", e);
        }
    }

    private String extractAdvertisedHost(String qrContent) {
        try {
            if (qrContent == null || !qrContent.startsWith(Constants.QR_CODE_PREFIX)) {
                return "";
            }
            String wsPart = qrContent.substring(Constants.QR_CODE_PREFIX.length());
            URI uri = new URI(wsPart);
            return uri.getHost() + ":" + uri.getPort();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 错误回调
     */
    @Override
    public void onError(String errorType, String errorMessage) {
        runOnUiThread(() -> {
            addLogEntry("错误 [" + errorType + "]: " + errorMessage);

            if ("connection".equals(errorType) || "server".equals(errorType)) {
                updateStatus(getString(R.string.status_service_failed));
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
            }
        });
    }

    /**
     * 更新状态显示
     */
    private void updateStatus(String status) {
        statusTextView.setText(status);

        int backgroundColor;
        int textColor;

        if (getString(R.string.status_service_running).equals(status)) {
            backgroundColor = MaterialColors.getColor(statusTextView, com.google.android.material.R.attr.colorPrimaryContainer);
            textColor = MaterialColors.getColor(statusTextView, com.google.android.material.R.attr.colorOnPrimaryContainer);
        } else if (getString(R.string.status_service_starting).equals(status)
                || getString(R.string.status_service_stopping).equals(status)) {
            backgroundColor = MaterialColors.getColor(statusTextView, com.google.android.material.R.attr.colorSecondaryContainer);
            textColor = MaterialColors.getColor(statusTextView, com.google.android.material.R.attr.colorOnSecondaryContainer);
        } else {
            backgroundColor = MaterialColors.getColor(statusTextView, com.google.android.material.R.attr.colorErrorContainer);
            textColor = MaterialColors.getColor(statusTextView, com.google.android.material.R.attr.colorOnErrorContainer);
        }

        statusTextView.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        statusTextView.setTextColor(textColor);
    }

    /**
     * 添加日志条目
     */
    private void addLogEntry(String message) {
        String timestamp = String.format("[%tT] ", System.currentTimeMillis());
        String logEntry = timestamp + message + "\n";

        String currentLog = logTextView.getText().toString();
        logTextView.setText(currentLog + logEntry);

        // 自动滚动到底部
        logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
    }

    /**
     * 权限请求结果处理
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == BYD_PERMISSION_REQUEST_CODE) {
            // 处理BYD车机权限结果
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                addLogEntry("BYD车机权限已授予");
                // BYD权限获得后，继续请求普通权限
                requestPermissions();
            } else {
                addLogEntry("BYD车机权限被拒绝，某些功能可能不可用");
                // BYD权限被拒，继续请求普通权限
                requestPermissions();
            }
        } else if (requestCode == PERMISSION_REQUEST_CODE) {
            // 处理普通权限结果
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                addLogEntry("所有权限已授予");
            } else {
                addLogEntry("部分权限被拒绝，可能影响功能");
            }
        }
    }

    /**
     * 处理二维码扫描结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                addLogEntry("扫描已取消");
            } else {
                addLogEntry("已取消扫码；当前应用应生成二维码供 DG-LAB APP 扫描");
            }
        }
    }

    /**
     * 活动销毁时的清理
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 停止服务
        if (speedDataService != null) {
            speedDataService.stop();
        }
        if (webSocketService != null) {
            webSocketService.disconnect();
        }

        Log.d(TAG, "MainActivity destroyed");
    }

    /**
     * 手动设置车速（用于测试）
     * 注意：这只是为了演示，实际应用中应该从BYD SDK获取
     */
    public void setTestSpeed(double speedKmH) {
        if (speedDataService != null) {
            speedDataService.setManualSpeed(speedKmH);
        }
    }
}