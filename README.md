# BYD DG-LAB 集成项目

这是一个完整的Android项目，用于将比亚迪BYD汽车的车速数据与DG-LAB脉冲设备进行集成控制。

⚠⚠⚠本项目现阶段仅作为测试使用，请不要将本项目用于到主驾上，避免开车过程中危险发生⚠⚠⚠

## 项目结构

```
BYD_DGLAB_Integration/
├── app/
│   ├── src/main/
│   │   ├── java/com/byd/dglab/integration/
│   │   │   ├── Constants.java                 # 常量定义
│   │   │   ├── SpeedChangeListener.java       # 车速变化监听器接口
│   │   │   ├── ControlCommandListener.java    # 控制命令监听器接口
│   │   │   ├── SocketProtocolHelper.java      # SOCKET协议助手
│   │   │   ├── SpeedToControlConverter.java   # 车速到控制参数转换器
│   │   │   ├── SpeedDataService.java          # 车速数据服务
│   │   │   ├── WebSocketService.java          # WebSocket通信服务
│   │   │   └── MainActivity.java              # 主活动类
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml          # 主界面布局
│   │   │   └── values/
│   │   │       └── strings.xml                 # 字符串资源
│   │   └── AndroidManifest.xml                 # 应用清单
│   └── build.gradle                            # 应用级构建配置
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties           # Gradle包装器配置
├── build.gradle                                # 项目级构建配置
├── settings.gradle                             # 项目设置
├── gradle.properties                          # Gradle属性
├── gradlew                                     # Unix Gradle包装器脚本
└── gradlew.bat                                 # Windows Gradle包装器脚本
```

## 功能特性

- **实时车速获取**: 支持从BYD SDK或GPS获取车速数据
- **智能参数转换**: 根据车速自动计算强度和频率参数
- **WebSocket通信**: 与DG-LAB SOCKET V3服务器建立安全连接
- **自动重连**: 网络断开时自动重连机制
- **数据平滑**: 使用移动平均滤波减少车速波动
- **双通道控制**: 支持A/B两个通道的独立控制
- **实时UI更新**: 显示当前车速、强度、频率和连接状态

## 技术栈

- **Android SDK**: 25+ (最低支持版本)
- **Java-WebSocket**: 1.5.3 (WebSocket通信)
- **Gson**: 2.8.9 (JSON处理)
- **BYD-AUTO-API**: 车载应用开发SDK

## 安装和设置

### 1. 环境要求

- Android Studio Arctic Fox 或更高版本
- JDK 8 或更高版本
- Android SDK API 25+

### 2. 导入项目

1. 打开Android Studio
2. 选择 "Open an existing Android Studio project"
3. 导航到 `BYD_DGLAB_Integration` 文件夹并选择它
4. 等待Gradle同步完成

### 3. 配置车速数据源

项目支持两种车速数据源：

#### 选项A：GPS（推荐开发测试）
- 无需任何配置
- 可在任何Android设备或模拟器上运行
- 项目默认使用此方案

#### 选项B：BYD-AUTO-API（用于车机部署）
- 详见项目中的 `BYD_API_集成指南.md` 文件
- 按照指南在 `SpeedDataService.java` 中启用BYD API
- 仅在BYD车机上可用

### 4. 构建项目

```bash
# 在项目根目录执行
./gradlew clean build

# 或运行
./gradlew assembleDebug
```

### 5. 运行应用

1. 连接Android设备或启动模拟器
2. 点击Android Studio中的运行按钮
3. 授予必要的权限（位置、互联网等）

## 使用说明

### 基本操作

1. **启动应用**: 应用启动后会自动尝试获取车速数据
2. **连接DG-LAB**: 点击"连接到DG-LAB"按钮建立WebSocket连接
3. **实时控制**: 车速变化时会自动发送控制命令到设备
4. **断开连接**: 点击"断开连接"按钮关闭WebSocket连接

### 界面说明

- **车速显示**: 显示当前车速（km/h）
- **强度显示**: 显示计算出的强度值（0-200）
- **频率显示**: 显示计算出的频率值（Hz）
- **状态显示**: 显示连接状态
- **日志区域**: 显示操作日志和错误信息

## 协议说明

### DG-LAB SOCKET V3 协议

项目实现了完整的SOCKET V3协议支持：

- **强度控制**: `{"type":"strength","data":{"channel":"A","intensity":100}}`
- **脉冲控制**: `{"type":"pulse","data":{"channel":"A","frequency":50,"intensity":100}}`
- **二维码绑定**: `{"type":"qrCode","data":"二维码字符串"}`
- **心跳**: `{"type":"heartbeat","timestamp":1640995200000}`

### 蓝牙协议 (B0/BF指令)

- **B0指令**: 强度控制 `B0,A,100,checksum;`
- **BF指令**: 脉冲控制 `BF,A,50,100,checksum;`

## 车速转换算法

### 分段线性映射

- **0-30 km/h**: 强度 0-50, 频率 10-30 Hz
- **30-80 km/h**: 强度 50-120, 频率 30-80 Hz
- **80-120 km/h**: 强度 120-200, 频率 80-150 Hz
- **120+ km/h**: 强度 200, 频率 150 Hz

### 数据平滑

使用5点移动平均滤波减少车速波动对控制参数的影响。

## 权限要求

应用需要以下权限：

- `INTERNET`: WebSocket通信
- `ACCESS_NETWORK_STATE`: 网络状态检查
- `ACCESS_FINE_LOCATION`: GPS定位获取车速
- `ACCESS_COARSE_LOCATION`: 粗略定位

## 故障排除

### 常见问题

1. **连接失败**
   - 检查网络连接
   - 确认DG-LAB设备在线
   - 查看日志中的错误信息

2. **GPS不可用**
   - 确认位置权限已授予
   - 检查GPS是否开启
   - 在室内环境GPS信号弱

3. **BYD SDK集成**
   - 确认SDK文件正确放置
   - 检查API调用是否正确
   - 查看BYD SDK文档

### 日志分析

应用会记录详细的操作日志，帮助诊断问题：

- 连接状态变化
- 命令发送记录
- 错误信息
- 车速数据更新

## 扩展开发

### 添加新功能

1. **自定义转换算法**: 修改 `SpeedToControlConverter.java`
2. **新协议支持**: 扩展 `SocketProtocolHelper.java`
3. **UI增强**: 更新 `activity_main.xml` 和 `MainActivity.java`
4. **数据持久化**: 添加SharedPreferences或数据库存储

### 测试建议

- 使用模拟车速数据测试转换算法
- 在不同网络环境下测试连接稳定性
- 验证权限处理逻辑
- 测试边界条件（最高/最低车速）