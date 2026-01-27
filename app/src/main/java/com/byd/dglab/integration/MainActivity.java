package com.byd.dglab.integration;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

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
    private ScrollView logScrollView;
    private Button connectButton;
    private Button disconnectButton;
    private EditText serverUrlEditText;
    private Button scanQrButton;
    private Button applyUrlButton;
    private android.widget.RadioGroup dataSourceRadioGroup;
    private android.widget.RadioButton gpsOnlyRadio;
    private android.widget.RadioButton bydAutoRadio;
    private android.widget.RadioButton bydOnlyRadio;
    private TextView dataSourceStatusTextView;

    // 服务组件
    private SpeedDataService speedDataService;
    private WebSocketService webSocketService;
    private SpeedToControlConverter converter;

    // 权限请求码
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int QR_CODE_REQUEST_CODE = 101;
    private static final String PREFS_NAME = "WebSocketConfig";
    private static final String KEY_SERVER_URL = "server_url";

    // SharedPreferences
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 初始化UI组件
        initializeUI();

        // 初始化服务
        initializeServices();

        // 请求权限
        requestPermissions();

        Log.d(TAG, "MainActivity created");
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
        dataSourceRadioGroup = findViewById(R.id.dataSourceRadioGroup);
        gpsOnlyRadio = findViewById(R.id.gpsOnlyRadio);
        bydAutoRadio = findViewById(R.id.bydAutoRadio);
        bydOnlyRadio = findViewById(R.id.bydOnlyRadio);
        dataSourceStatusTextView = findViewById(R.id.dataSourceStatusTextView);

        // 加载保存的WebSocket地址
        String savedUrl = sharedPreferences.getString(KEY_SERVER_URL, Constants.SOCKET_SERVER_URL);
        serverUrlEditText.setText(savedUrl);

        // 设置按钮监听器
        connectButton.setOnClickListener(this::onConnectClicked);
        disconnectButton.setOnClickListener(this::onDisconnectClicked);
        scanQrButton.setOnClickListener(this::onScanQrClicked);
        applyUrlButton.setOnClickListener(this::onApplyUrlClicked);

        // 初始状态
        updateStatus("未连接");
        addLogEntry("应用启动");
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
        String statusText = "当前数据源: ";
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
        addLogEntry("正在连接到DG-LAB服务器...");
        updateStatus("连接中...");
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
        updateStatus("断开中...");
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
        // 检查相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
            return;
        }

        // 启动ZXing二维码扫描
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("请扫描包含WebSocket地址的二维码");
        integrator.setCameraId(0);
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(false);
        integrator.initiateScan();

        addLogEntry("正在打开二维码扫描...");
    }

    /**
     * 应用WebSocket地址按钮点击事件
     */
    private void onApplyUrlClicked(View view) {
        String url = serverUrlEditText.getText().toString().trim();

        if (url.isEmpty()) {
            Toast.makeText(this, "请输入WebSocket地址", Toast.LENGTH_SHORT).show();
            addLogEntry("错误：WebSocket地址为空");
            return;
        }

        // 验证URL格式
        if (!isValidWebSocketUrl(url)) {
            Toast.makeText(this, "无效的WebSocket地址格式", Toast.LENGTH_SHORT).show();
            addLogEntry("错误：无效的WebSocket地址 - " + url);
            return;
        }

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
            Toast.makeText(this, "WebSocket地址已更新", Toast.LENGTH_SHORT).show();
            addLogEntry("WebSocket地址已保存并更新: " + url);
        } catch (Exception e) {
            addLogEntry("更新WebSocket地址失败: " + e.getMessage());
            Log.e(TAG, "Error updating WebSocket service", e);
        }
    }

    /**
     * 验证WebSocket地址格式
     */
    private boolean isValidWebSocketUrl(String url) {
        return url.startsWith("ws://") || url.startsWith("wss://");
    }

    /**
     * 车速变化回调
     */
    @Override
    public void onSpeedChanged(double speedKmH) {
        runOnUiThread(() -> {
            // 更新UI显示
            speedTextView.setText(String.format("%.1f km/h", speedKmH));

            // 转换为控制参数
            int intensity = converter.convertSpeedToIntensity(speedKmH);
            int frequency = converter.convertSpeedToFrequency(speedKmH);

            intensityTextView.setText(String.valueOf(intensity));
            frequencyTextView.setText(String.format("%d Hz", frequency));

            // 发送控制命令（如果已连接）
            if (webSocketService.isConnected()) {
                webSocketService.sendPulseCommand(Constants.CHANNEL_A, frequency, intensity);
                webSocketService.sendPulseCommand(Constants.CHANNEL_B, frequency, intensity);
            }

            // 获取当前数据源
            String dataSource = speedDataService.isSpeedFromBYD() ? "BYD" : "GPS";

            addLogEntry(String.format("车速更新: %.1f km/h (来自%s) -> 强度:%d, 频率:%d Hz",
                    speedKmH, dataSource, intensity, frequency));
        });
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
                    updateStatus("已连接");
                    connectButton.setEnabled(false);
                    disconnectButton.setEnabled(true);
                    addLogEntry("成功连接到DG-LAB服务器");
                } else if ("closed".equals(responseData)) {
                    updateStatus("未连接");
                    connectButton.setEnabled(true);
                    disconnectButton.setEnabled(false);
                    addLogEntry("连接已断开");
                }
            } else {
                addLogEntry("收到响应: " + responseType);
            }
        });
    }

    /**
     * 错误回调
     */
    @Override
    public void onError(String errorType, String errorMessage) {
        runOnUiThread(() -> {
            addLogEntry("错误 [" + errorType + "]: " + errorMessage);

            if ("connection".equals(errorType)) {
                updateStatus("连接失败");
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
            }
        });
    }

    /**
     * 更新状态显示
     */
    private void updateStatus(String status) {
        statusTextView.setText("状态: " + status);
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

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                addLogEntry("权限已授予");
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
                String qrContent = result.getContents();
                addLogEntry("二维码内容: " + qrContent);

                // 检查是否是WebSocket地址
                if (isValidWebSocketUrl(qrContent)) {
                    serverUrlEditText.setText(qrContent);
                    Toast.makeText(this, "已识别WebSocket地址，点击'应用'保存", Toast.LENGTH_LONG).show();
                    addLogEntry("识别到有效的WebSocket地址");
                } else if (qrContent.startsWith("http://") || qrContent.startsWith("https://")) {
                    // 可能是包含WebSocket地址的URL
                    Toast.makeText(this, "请手动修改为ws://或wss://前缀", Toast.LENGTH_SHORT).show();
                    serverUrlEditText.setText(qrContent);
                    addLogEntry("识别到URL，需要修改为WebSocket地址");
                } else {
                    Toast.makeText(this, "二维码内容不是有效的WebSocket地址", Toast.LENGTH_SHORT).show();
                    addLogEntry("二维码内容无效: " + qrContent);
                }
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