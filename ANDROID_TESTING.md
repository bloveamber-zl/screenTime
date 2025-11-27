# Android 端测试指南

## 1. 运行 Example App

### 方式一：使用 Flutter 命令

```bash
# 进入 example 目录
cd example

# 运行 Android 应用
flutter run -d <device-id>
```

### 方式二：使用 Android Studio

1. 打开 `example/android` 项目
2. 连接 Android 设备或启动模拟器
3. 点击运行按钮

## 2. 查看日志

### 使用 adb logcat

```bash
# 查看所有日志
adb logcat

# 只查看 ScreenTime 相关日志
adb logcat | grep -E "ScreenTime|BlockAppService|ScreenTimeMethod|ScreenTimePlugin"

# 查看特定标签的日志
adb logcat -s ScreenTimeMethod:D BlockAppService:D ScreenTimePlugin:D

# 清除日志并重新开始
adb logcat -c && adb logcat | grep -E "ScreenTime|BlockAppService"
```

### 使用 Flutter 日志

```bash
# 运行应用并查看日志
flutter run -d <device-id> --verbose
```

## 3. 测试前准备

### 3.1 授予必要权限

在测试前，需要授予以下权限：

#### 悬浮窗权限（必须）

```dart
// 在应用中调用
await ScreenTime().requestPermission(
  permissionType: ScreenTimePermissionType.drawOverlay
);
```

手动设置：

- 设置 → 应用 → 你的应用 → 权限 → 显示在其他应用的上层 → 允许

#### 使用情况统计权限（必须）

```dart
// 在应用中调用
await ScreenTime().requestPermission(
  permissionType: ScreenTimePermissionType.appUsage
);
```

手动设置：

- 设置 → 应用 → 特殊应用访问 → 使用情况访问权限 → 找到你的应用 → 开启

### 3.2 检查权限状态

```dart
final permissions = await ScreenTime().checkPermissions();
print('悬浮窗权限: ${permissions['hasOverlayPermission']}');
print('使用情况统计权限: ${permissions['hasUsageStatsPermission']}');
```

## 4. 测试步骤

### 4.1 测试获取已安装应用

```dart
final apps = await ScreenTime().installedApps(ignoreSystemApps: true);
print('已安装应用数量: ${apps.length}');
for (var app in apps.take(5)) {
  print('应用: ${app.appName}, 包名: ${app.packageName}');
}
```

**预期结果**：返回设备上已安装的应用列表

### 4.2 测试获取应用使用情况

```dart
final now = DateTime.now();
final startTime = now.subtract(Duration(days: 1));
final usage = await ScreenTime().appUsageData(
  startTime: startTime,
  endTime: now,
  usageInterval: UsageInterval.daily,
);
print('应用使用情况: ${usage.length} 个应用');
```

**预期结果**：返回过去 24 小时的应用使用情况

### 4.3 测试屏蔽应用

```dart
// 设置 1 分钟后解除限制
final endTime = DateTime.now().add(Duration(minutes: 1));

final success = await ScreenTime().blockApps(
  packagesName: ['com.example.app'], // 替换为实际的应用包名
  endTime: endTime,
  layoutName: 'screenTimeLock', // 你的自定义布局名称
  notificationTitle: '应用已锁定',
  notificationText: '应用将在 ${endTime.hour}:${endTime.minute} 解除限制',
  shieldTitle: '已锁定',
  shieldSubtitle: '请在指定时间后重试',
  shieldButtonLabel: '我知道了',
  shieldButtonColor: '#FF4D4F',
  shieldButtonTextColor: '#FFFFFF',
);

print('屏蔽结果: $success');
```

**测试步骤**：

1. 调用 `blockApps` 后，应该返回 `true`
2. 打开被屏蔽的应用，应该显示屏蔽页遮罩层
3. 等待时间到期后，遮罩层应该自动消失
4. 查看日志确认服务正常运行

**查看日志**：

```bash
adb logcat | grep -E "BlockAppService|ScreenTimeMethod"
```

**预期日志**：

```
D/ScreenTimeMethod: blockApps() called with args: ...
D/ScreenTimeMethod: Permission diagnostics: canDrawOverlays=true
D/ScreenTimeMethod: Timing computed: now=..., computedEndTime=..., remaining=...ms
D/BlockAppService: Service onCreate
D/BlockAppService: Service guard 已设置
D/BlockAppService: 显示锁屏 - pkg:com.example.app
```

### 4.4 测试清除所有限制

```dart
final success = await ScreenTime().clearAllShields();
print('清除结果: $success');
```

**测试步骤**：

1. 先设置一些应用为屏蔽状态
2. 调用 `clearAllShields()`
3. 应该返回 `true`
4. 被屏蔽的应用应该可以正常打开
5. 遮罩层应该立即消失

**预期日志**：

```
D/ScreenTimeMethod: Unblocked apps using overlay service + WorkManager
D/ScreenTimeMethod: Stopping BlockAppService
D/BlockAppService: Service onDestroy called
```

### 4.5 测试自动解除限制

```dart
// 设置 30 秒后自动解除
final endTime = DateTime.now().add(Duration(seconds: 30));

await ScreenTime().blockApps(
  packagesName: ['com.example.app'],
  endTime: endTime,
  layoutName: 'screenTimeLock',
);
```

**测试步骤**：

1. 设置 30 秒后解除限制
2. 打开被屏蔽的应用，确认显示遮罩层
3. 等待 30 秒
4. 遮罩层应该自动消失
5. 应用应该可以正常使用

**查看日志确认自动解除**：

```bash
adb logcat | grep -E "UnblockWorker|BlockAppService.*隐藏"
```

**预期日志**：

```
D/BlockAppService: 需要隐藏锁屏
D/UnblockWorker: Block time has ended, unblocking apps via WorkManager
D/UnblockWorker: Successfully unblocked apps via WorkManager
```

## 5. 常见问题排查

### 5.1 屏蔽不生效

**检查清单**：

1. ✅ 是否授予了悬浮窗权限？

   ```dart
   final perms = await ScreenTime().checkPermissions();
   print('权限状态: $perms');
   ```

2. ✅ 是否授予了使用情况统计权限？

3. ✅ 应用包名是否正确？

   ```dart
   final apps = await ScreenTime().installedApps();
   // 查看实际包名
   ```

4. ✅ 查看日志是否有错误
   ```bash
   adb logcat | grep -E "ERROR|Exception|Error"
   ```

### 5.2 遮罩层不显示

**可能原因**：

- 布局文件不存在或 ID 不匹配
- 权限未授予
- 服务未正常启动

**排查步骤**：

```bash
# 检查服务是否运行
adb shell dumpsys activity services | grep BlockAppService

# 查看详细日志
adb logcat -s BlockAppService:D ScreenTimeMethod:D
```

### 5.3 自动解除不生效

**检查**：

1. 查看 `BlockAppService` 是否在运行
2. 检查 `UnblockWorker` 是否被调度
3. 查看 SharedPreferences 中的 `block_end_time` 是否正确

```bash
# 查看 WorkManager 任务
adb shell dumpsys jobscheduler | grep unblock

# 查看 SharedPreferences（需要 root）
adb shell run-as <package-name> cat shared_prefs/<prefs-file>.xml
```

## 6. 调试技巧

### 6.1 实时查看日志

```bash
# 在一个终端窗口运行
adb logcat -s ScreenTimeMethod:D BlockAppService:D | grep -v "防抖中"
```

### 6.2 检查服务状态

```bash
# 检查 BlockAppService 是否运行
adb shell dumpsys activity services | grep -A 10 BlockAppService
```

### 6.3 强制停止服务（测试用）

```bash
# 停止服务
adb shell am force-stop <package-name>

# 或通过应用设置手动停止
```

### 6.4 清除应用数据（重置测试）

```bash
adb shell pm clear <package-name>
```

## 7. 测试用例示例

### 完整测试流程

```dart
void testBlockingFlow() async {
  final screenTime = ScreenTime();

  // 1. 检查权限
  final perms = await screenTime.checkPermissions();
  assert(perms['hasOverlayPermission'] == true, '需要悬浮窗权限');
  assert(perms['hasUsageStatsPermission'] == true, '需要使用情况统计权限');

  // 2. 获取一个测试应用
  final apps = await screenTime.installedApps();
  assert(apps.isNotEmpty, '没有找到已安装的应用');
  final testApp = apps.first.packageName;

  // 3. 设置 1 分钟后解除限制
  final endTime = DateTime.now().add(Duration(minutes: 1));
  final success = await screenTime.blockApps(
    packagesName: [testApp],
    endTime: endTime,
    layoutName: 'screenTimeLock',
  );
  assert(success == true, '屏蔽失败');

  // 4. 等待一段时间
  await Future.delayed(Duration(seconds: 5));

  // 5. 清除限制
  final cleared = await screenTime.clearAllShields();
  assert(cleared == true, '清除失败');

  print('测试通过！');
}
```

## 8. 性能测试

### 测试屏蔽响应速度

```dart
final start = DateTime.now();
await screenTime.blockApps(...);
final duration = DateTime.now().difference(start);
print('屏蔽响应时间: ${duration.inMilliseconds}ms');
// 预期: < 500ms
```

### 测试监控性能

观察 `BlockAppService` 的 CPU 和内存使用：

```bash
# 查看进程信息
adb shell top -n 1 | grep <package-name>

# 查看内存使用
adb shell dumpsys meminfo <package-name>
```
