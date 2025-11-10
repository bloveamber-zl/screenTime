# 应用阻止状态检测使用指南

## 概述

这个功能允许你的应用检测当前是否处于阻止状态，并相应地显示不同的 UI 界面。

## 功能特性

- ✅ 检测应用是否被阻止
- ✅ 获取剩余阻止时间
- ✅ 获取阻止原因
- ✅ 获取被阻止的应用列表
- ✅ 支持 iOS 和 Android 平台

## 使用方法

### 1. 基本检测

```dart
import 'package:screen_time/screen_time.dart';

final screenTime = ScreenTime();

// 检查阻止状态
final blockingStatus = await screenTime.getBlockingStatus();

if (blockingStatus != null && blockingStatus['isBlocked'] == true) {
  // 应用被阻止，显示阻止UI
  showBlockingUI(blockingStatus);
} else {
  // 应用正常，显示正常UI
  showNormalUI();
}
```

### 2. 状态信息

返回的状态信息包含以下字段：

```dart
{
  'isBlocked': bool,           // 是否被阻止
  'blockedApps': List<String>, // 被阻止的应用列表
  'remainingTime': double,     // 剩余阻止时间（秒）
  'blockReason': String,      // 阻止原因
  'canOverride': bool,        // 是否可以覆盖阻止
  'remainingTimeFormatted': String, // 格式化的剩余时间 (HH:MM:SS)
}
```

### 3. 完整示例

```dart
class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final ScreenTime _screenTime = ScreenTime();
  Map<String, dynamic>? _blockingStatus;

  @override
  void initState() {
    super.initState();
    _checkBlockingStatus();
  }

  Future<void> _checkBlockingStatus() async {
    try {
      final status = await _screenTime.getBlockingStatus();
      setState(() {
        _blockingStatus = status;
      });
    } catch (e) {
      print('检查阻止状态失败: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    // 如果被阻止，显示阻止UI
    if (_blockingStatus != null && _blockingStatus!['isBlocked'] == true) {
      return _buildBlockingUI();
    }

    // 否则显示正常UI
    return _buildNormalUI();
  }

  Widget _buildBlockingUI() {
    final remainingTime = _blockingStatus!['remainingTimeFormatted'] ?? '00:00';
    final blockReason = _blockingStatus!['blockReason'] ?? '应用使用时间限制';

    return Scaffold(
      backgroundColor: Colors.red.shade50,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.block, size: 80, color: Colors.red),
            SizedBox(height: 24),
            Text('应用使用受限', style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
            SizedBox(height: 16),
            Text(blockReason),
            SizedBox(height: 24),
            Text('剩余时间: $remainingTime', style: TextStyle(fontSize: 18)),
            SizedBox(height: 24),
            ElevatedButton(
              onPressed: _checkBlockingStatus,
              child: Text('刷新状态'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNormalUI() {
    return Scaffold(
      appBar: AppBar(title: Text('我的应用')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.check_circle, size: 80, color: Colors.green),
            SizedBox(height: 24),
            Text('应用正常运行'),
            SizedBox(height: 24),
            ElevatedButton(
              onPressed: _checkBlockingStatus,
              child: Text('检查阻止状态'),
            ),
          ],
        ),
      ),
    );
  }
}
```

## 最佳实践

### 1. 应用启动时检查

```dart
@override
void initState() {
  super.initState();
  // 应用启动时立即检查阻止状态
  _checkBlockingStatus();
}
```

### 2. 定期检查状态

```dart
Timer.periodic(Duration(seconds: 30), (timer) {
  _checkBlockingStatus();
});
```

### 3. 用户交互时检查

```dart
// 当用户尝试使用应用功能时检查
onTap: () async {
  final status = await _screenTime.getBlockingStatus();
  if (status != null && status['isBlocked'] == true) {
    // 显示阻止提示
    return;
  }
  // 执行正常功能
}
```

## 注意事项

1. **权限要求**: 需要相应的屏幕时间权限
2. **平台差异**: iOS 和 Android 的实现可能有所不同
3. **性能考虑**: 避免过于频繁的状态检查
4. **错误处理**: 始终处理可能的异常情况

## 故障排除

### 常见问题

1. **返回 null**: 检查权限是否正确授予
2. **状态不准确**: 确保在正确的时机调用检查方法
3. **性能问题**: 避免在 build 方法中调用检查方法

### 调试建议

```dart
try {
  final status = await _screenTime.getBlockingStatus();
  print('阻止状态: $status');
} catch (e) {
  print('检查失败: $e');
}
```
