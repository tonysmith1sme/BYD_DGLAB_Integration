# BYD DG-LAB 集成项目

将比亚迪BYD汽车的车速数据与DG-LAB脉冲设备进行实时集成控制的Android应用。

**⚠⚠⚠注意：本项目现阶段仅作为测试使用，请不要将本项目用于主驾驶，避免开车过程中发生危险。⚠⚠⚠**

## 项目结构

```
BYD_DGLAB_Integration/
├── app/src/main/
│   ├── java/com/byd/dglab/integration/
│   │   ├── Constants.java                 常量定义
│   │   ├── MainActivity.java              主活动
│   │   ├── SpeedChangeListener.java       车速监听器接口
│   │   ├── ControlCommandListener.java    控制监听器接口
│   │   ├── SpeedDataService.java          车速数据服务
│   │   ├── WebSocketService.java          WebSocket通信
│   │   ├── SpeedToControlConverter.java   车速转换器
│   │   ├── SocketProtocolHelper.java      SOCKET协议
│   │   ├── BydManifest.java               权限管理
│   │   ├── PermissionUtils.java           权限工具
│   │   └── PermissionActivity.java        权限处理
│   └── res/
│       ├── layout/activity_main.xml       主界面布局
│       └── values/strings.xml             字符串资源
└── build.gradle
```

## 主要功能

- 实时车速获取（GPS或BYD SDK）
- 智能参数转换（车速转强度/频率）
- WebSocket通信（DG-LAB SOCKET V3）
- 自动重连机制
- 数据平滑处理
- 双通道控制（A/B通道）
- 权限管理系统
- Material Design界面

## 环境要求

- Android Studio 2021.1+
- JDK 8+
- Android SDK API 25+

## 快速开始

1. 打开项目：`File -> Open -> BYD_DGLAB_Integration`
2. 等待Gradle同步完成
3. 连接Android设备或启动模拟器
4. 点击运行按钮

## UI界面结构

应用界面分为6个功能区块：

- **服务器配置**: 输入DG-LAB服务器地址和端口
- **车速数据源**: 选择GPS或BYD数据源
- **检查权限**: 查看和申请应用所需权限
- **实时数据**: 显示当前车速、强度、频率
- **连接控制**: 连接/断开DG-LAB设备
- **运行日志**: 实时显示操作日志和错误信息

## 车速转换规则

```
0-30 km/h     -> 强度 0-50,    频率 10-30
30-80 km/h    -> 强度 50-120,  频率 30-80
80-120 km/h   -> 强度 120-200, 频率 80-150
120+ km/h     -> 强度 200,     频率 150
```

使用5点移动平均滤波处理车速波动。

## SOCKET V3 协议

支持的命令类型：

- 强度控制: `{"type":"strength","data":{"channel":"A","intensity":100}}`
- 脉冲控制: `{"type":"pulse","data":{"channel":"A","frequency":50,"intensity":100}}`
- 二维码绑定: `{"type":"qrCode","data":"..."}`
- 心跳: `{"type":"heartbeat","timestamp":...}`

## 技术依赖

- Java-WebSocket 1.5.3
- Gson 2.8.9
- BYD-AUTO-API (车机部署)