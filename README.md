# Synx VIP Unlocker - LSPosed 模块

破解 Synx App (com.synxapp.synx) 的 VIP/Pro 功能。

## 原理

Synx 使用 **RevenueCat** SDK 管理订阅状态，应用通过以下流程检测 VIP：

1. Flutter Dart 层调用 `Purchases.getCustomerInfo()` 
2. 通过 `purchases_flutter` 插件调用到 Native 层
3. Native 层 RevenueCat SDK 返回 `CustomerInfo` 对象
4. Dart 层检查 `entitlements.active` 和 `EntitlementInfo.isActive()`
5. 根据结果设置 `isProMode` 标志，`SubscriptionEnforcement` 类据此启用/限制功能

本模块通过 LSPosed 框架 Hook 以下方法：

| Hook | 目标类 | 目标方法 | 效果 |
|------|--------|----------|------|
| 1 | `EntitlementInfo` | `isActive()` | 始终返回 `true` |
| 2 | `EntitlementInfo` | `getExpirationDate()` | 返回 2099 年 |
| 3 | `CustomerInfo` | `getEntitlements()` | 注入 "pro" 权益 |
| 4 | `CustomerInfo` | `getActiveSubscriptions()` | 注入活跃订阅 |

## 构建

### 环境要求
- Android Studio Hedgehog (2023.1) 或更新版本
- JDK 17
- Android SDK 34
- Gradle 8.5

### 准备 Xposed API

**方式一：使用 Maven 仓库（推荐）**

模块已配置从 Maven 仓库自动下载 Xposed API，直接构建即可。

**方式二：本地 JAR（离线编译）**

```bash
# 下载 Xposed API
cd app/libs
curl -L -o api-82.jar https://github.com/rovo89/XposedBridge/wiki/Development-tutorial

# 然后修改 app/build.gradle.kts 中的依赖为：
# compileOnly(files("libs/api-82.jar"))
```

### 构建步骤

```bash
# 在 Android Studio 中
1. 打开 SynxVipUnlocker 目录作为项目
2. 等待 Gradle 同步完成
3. Build → Build APK(s)
# 或者命令行
cd SynxVipUnlocker
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/release/app-release.apk`

## 安装与使用

1. 安装 LSPosed 框架（需要 Magisk/KernelSU/APatch）
2. 安装本模块 APK
3. 打开 LSPosed 管理器
4. 在「模块」中找到「Synx VIP Unlocker」
5. 点击模块 → 勾选「Synx」应用
6. 强制停止 Synx 应用并重新启动
7. VIP 功能将自动生效

## 验证

重启 Synx 后，检查以下功能是否解锁：
- 无限投资账户数量（免费版限制 3 个）
- Analytics / 自定义视图功能
- API 调用不再受每日限制
- 所有 Pro 功能可用

查看日志（通过 LSPosed 日志或 logcat）：

```bash
adb logcat -s SynxVipUnlocker:D
```

如果看到 `All VIP hooks installed successfully!` 说明 Hook 成功。

## 免责声明

本模块仅供学习和研究 Android Hook 技术使用。
请尊重开发者劳动成果，有能力请支持正版。
