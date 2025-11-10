# iOS 16+ Screen Time 插件使用指南

## 概述

本插件已升级至 iOS 16.0+ 版本，使用 Apple 的 FamilyControls、DeviceActivity 和 ManagedSettings 框架实现完整的应用阻止和定时阻止功能。

## 主要变更

### 版本要求

- **最低 iOS 版本**: 16.0 (从 15.0 升级)
- **必需框架**: FamilyControls, DeviceActivity, ManagedSettings, UserNotifications

### 新增功能

- ✅ 完整的应用阻止实现
- ✅ 应用解除阻止功能
- ✅ 定时阻止和重复阻止
- ✅ 权限状态检查
- ✅ 阻止状态监控

## 配置要求

### 1. Info.plist 权限配置

```xml
<key>NSFamilyControlsUsageDescription</key>
<string>此应用需要访问屏幕时间功能来帮助您管理应用使用</string>
<key>NSDeviceActivityUsageDescription</key>
<string>此应用需要访问设备活动功能来设置应用阻止时间</string>
<key>NSUserNotificationUsageDescription</key>
<string>此应用需要发送通知来提醒您应用阻止状态</string>
```

### 2. Xcode 项目配置

在 Xcode 项目设置中启用以下能力：

- **Family Controls**: 用于应用阻止
- **Device Activity**: 用于定时阻止
- **Managed Settings**: 用于应用管理

### 3. 最低部署目标

确保项目的最低部署目标设置为 iOS 16.0 或更高版本。

## 使用方法

### 权限管理

```dart
final screenTime = ScreenTime();

// 请求权限
final hasPermission = await screenTime.requestPermission(
  permissionType: ScreenTimePermissionType.appUsage
);

// 检查权限状态
final permissionStatus = await screenTime.permissionStatus(
  permissionType: ScreenTimePermissionType.appUsage
);
```

### 应用阻止

```dart
// 阻止应用
final success = await screenTime.blockApps(
  packagesName: ['com.apple.mobilesafari', 'com.apple.mobilemail'],
  duration: Duration(minutes: 30),
  layoutName: 'blocking_layout',
  notificationTitle: '应用被阻止',
  notificationText: '您无法使用这些应用30分钟'
);

// 解除阻止
await screenTime.unblockApps(
  packagesName: ['com.apple.mobilesafari', 'com.apple.mobilemail']
);

// 检查阻止状态
final isBlocking = await screenTime.isOnBlockingApps;
```

### 定时阻止

```dart
// 设置每日定时阻止
final scheduleSuccess = await screenTime.scheduleBlock(
  scheduleId: 'daily_block',
  packagesName: ['com.apple.mobilesafari'],
  startTime: DateTime.now().add(Duration(hours: 1)),
  duration: Duration(hours: 8),
  recurring: true,
  daysOfWeek: [1, 2, 3, 4, 5, 6, 7] // 每天
);

// 取消定时阻止
await screenTime.cancelScheduledBlock('daily_block');

// 获取活跃计划
final activeSchedules = await screenTime.getActiveSchedules();
```

### 阻止状态检查

```dart
// 获取详细阻止状态
final blockingStatus = await screenTime.getBlockingStatus();
if (blockingStatus != null) {
  final isBlocked = blockingStatus['isBlocked'] as bool;
  final blockedApps = blockingStatus['blockedApps'] as List<dynamic>;
  final remainingTime = blockingStatus['remainingTime'] as int;
  final formattedTime = blockingStatus['remainingTimeFormatted'] as String;

  if (isBlocked) {
    print('应用被阻止，剩余时间: $formattedTime');
    print('被阻止的应用: $blockedApps');
  }
}
```

## 完整示例

```dart
class ScreenTimeManager {
  final ScreenTime _screenTime = ScreenTime();

  Future<void> setupScreenTime() async {
    try {
      // 1. 检查权限
      final permissionStatus = await _screenTime.permissionStatus(
        permissionType: ScreenTimePermissionType.appUsage
      );

      if (permissionStatus != ScreenTimePermissionStatus.approved) {
        print('请求 FamilyControls 权限...');
        final hasPermission = await _screenTime.requestPermission(
          permissionType: ScreenTimePermissionType.appUsage
        );

        if (!hasPermission) {
          print('权限获取失败');
          return;
        }
      }

      // 2. 设置定时阻止
      final scheduleSuccess = await _screenTime.scheduleBlock(
        scheduleId: 'night_block',
        packagesName: ['com.apple.mobilesafari', 'com.apple.mobilemail'],
        startTime: DateTime.now().add(Duration(hours: 1)),
        duration: Duration(hours: 8),
        recurring: true,
        daysOfWeek: [1, 2, 3, 4, 5, 6, 7] // 每天
      );

      if (scheduleSuccess) {
        print('定时阻止设置成功');
      }

      // 3. 立即阻止应用（测试用）
      final blockSuccess = await _screenTime.blockApps(
        packagesName: ['com.apple.mobilesafari'],
        duration: Duration(minutes: 1),
        layoutName: 'blocking_layout',
        notificationTitle: '应用被阻止',
        notificationText: '1分钟后自动解除'
      );

      if (blockSuccess) {
        print('应用阻止成功');

        // 4. 检查阻止状态
        final blockingStatus = await _screenTime.getBlockingStatus();
        print('阻止状态: $blockingStatus');
      }

    } catch (e) {
      print('设置屏幕时间失败: $e');
    }
  }
}
```

## 技术实现细节

### 核心服务

- **AppBlockingService**: 处理应用阻止逻辑
- **BlockingStateService**: 管理阻止状态
- **ScreenTimeMethod**: 提供 Flutter 接口

### 使用的框架

- **FamilyControls**: 权限管理和应用选择
- **DeviceActivity**: 定时阻止和监控
- **ManagedSettings**: 应用阻止设置
- **UserNotifications**: 通知管理

## 注意事项

1. **权限限制**: 需要用户明确授权 FamilyControls 权限
2. **应用选择**: 用户需要手动选择要阻止的应用
3. **系统限制**: 某些系统应用无法被阻止
4. **版本要求**: 必须运行在 iOS 16.0 或更高版本

## 故障排除

### 常见问题

1. **权限被拒绝**: 引导用户到设置中手动开启权限
2. **应用无法阻止**: 检查应用是否在允许阻止的列表中
3. **定时不生效**: 检查 DeviceActivity 权限和配置
4. **版本不兼容**: 确保设备运行 iOS 16.0 或更高版本

### 调试建议

- 使用 Xcode 控制台查看日志
- 检查权限状态
- 验证应用配置
- 测试在真实设备上（模拟器可能有限制）

## 迁移指南

如果你从 iOS 15.0 版本迁移：

1. 更新最低部署目标到 iOS 16.0
2. 在 Xcode 中启用 Family Controls 能力
3. 更新权限检查逻辑
4. 测试新的阻止功能

## 示例项目

查看 `AppBlockingExample.swift` 文件获取完整的使用示例和最佳实践。
