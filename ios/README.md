# iOS Screen Time Plugin Implementation

## 概述

这个 iOS 实现为 Screen Time 插件提供了完整的应用阻止和监控功能，使用 Apple 的 FamilyControls、DeviceActivity 和 ManagedSettings 框架。

## 功能特性

### ✅ 已实现功能

- **权限管理**: FamilyControls 权限请求和状态检查
- **应用阻止**: 使用 ManagedSettings 进行应用阻止
- **定时阻止**: 使用 DeviceActivity 进行定时阻止
- **通知支持**: 阻止期间的通知管理

### ⚠️ 限制说明

- **应用使用数据**: iOS 隐私限制，无法获取详细的应用使用数据
- **已安装应用**: iOS 隐私限制，无法获取完整的应用列表
- **实时监控**: iOS 隐私限制，无法进行实时应用监控

## 配置要求

### 1. 权限配置

在`Info.plist`中添加以下权限：

```xml
<key>NSFamilyControlsUsageDescription</key>
<string>此应用需要访问屏幕时间功能来帮助您管理应用使用</string>
<key>NSDeviceActivityUsageDescription</key>
<string>此应用需要访问设备活动功能来设置应用阻止时间</string>
```

### 2. 能力配置

在 Xcode 项目设置中启用以下能力：

- **Family Controls**: 用于应用阻止
- **Device Activity**: 用于定时阻止
- **Managed Settings**: 用于应用管理

### 3. 隐私清单

已配置`PrivacyInfo.xcprivacy`文件，包含必要的隐私说明。

## 使用方法

### 权限请求

```dart
final screenTime = ScreenTime();
final hasPermission = await screenTime.requestPermission(
  permissionType: ScreenTimePermissionType.appUsage
);
```

### 应用阻止

```dart
final success = await screenTime.blockApps(
  packagesName: ['com.example.app'],
  duration: Duration(minutes: 30),
  layoutName: 'blocking_layout',
  notificationTitle: '应用被阻止',
  notificationText: '您无法使用此应用30分钟'
);
```

### 定时阻止

```dart
final success = await screenTime.scheduleBlock(
  scheduleId: 'daily_block',
  packagesName: ['com.example.app'],
  startTime: DateTime.now().add(Duration(hours: 1)),
  duration: Duration(hours: 2),
  recurring: true,
  daysOfWeek: [1, 2, 3, 4, 5] // 周一到周五
);
```

## 技术实现

### 核心服务

- **AppBlockingService**: 处理应用阻止逻辑
- **DeviceActivityExtension**: 处理定时阻止
- **ScreenTimeMethod**: 提供 Flutter 接口

### 框架依赖

- **FamilyControls**: 权限管理和应用选择
- **DeviceActivity**: 定时阻止和监控
- **ManagedSettings**: 应用阻止设置
- **UserNotifications**: 通知管理

## 注意事项

1. **权限限制**: iOS 的隐私保护机制限制了某些功能的实现
2. **用户授权**: 需要用户明确授权才能使用 FamilyControls 功能
3. **应用选择**: 用户需要手动选择要阻止的应用
4. **系统限制**: 某些系统应用无法被阻止

## 故障排除

### 常见问题

1. **权限被拒绝**: 引导用户到设置中手动开启权限
2. **应用无法阻止**: 检查应用是否在允许阻止的列表中
3. **定时不生效**: 检查 DeviceActivity 权限和配置

### 调试建议

- 使用 Xcode 控制台查看日志
- 检查权限状态
- 验证应用配置
