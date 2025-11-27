# Screen Time Plugin

A Flutter plugin for monitoring app usage in real-time on Android devices. This plugin provides detailed information about the currently active application and app usage statistics.

## Features

- Get detailed information about the currently active app
- Monitor app usage in real-time
- Configure monitoring intervals and lookback times
- Access app usage statistics

## Installation

Add the plugin to your `pubspec.yaml` file:

```yaml
dependencies:
  screen_time: ^1.0.0
```

## Setup

### Android

#### 1. Permission Information

The core permissions required by this plugin are already declared in the plugin's AndroidManifest.xml:

```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.GET_TASKS" />
```

You don't need to add these permissions to your app's AndroidManifest.xml as they're automatically merged during the build process.

#### 2. Runtime Permission Guidance

Real-time monitoring now relies on two runtime permissions instead of an AccessibilityService:

- **Usage Access** (`PACKAGE_USAGE_STATS`): required for reading the current foreground app via `UsageStatsManager`.
- **Draw over other apps** (`SYSTEM_ALERT_WINDOW`): required for displaying the blocking overlay UI.

Use `ScreenTime.requestPermission` to guide users to the corresponding system settings pages.

## Usage

### Request Permissions

Before using the plugin, request the necessary permissions:

```dart
final ScreenTime screenTime = ScreenTime();
final permissionStatus = await screenTime.requestPermission();

if (permissionStatus.status) {
  // Permission granted, proceed with usage
} else {
  // Handle permission denied
}
```

You can explicitly request specific permissions:

```dart
await screenTime.requestPermission(
  permissionType: ScreenTimePermissionType.appUsage,
);

await screenTime.requestPermission(
  permissionType: ScreenTimePermissionType.drawOverlay,
);
```

### Monitor App Usage

```dart
final result = await screenTime.monitoringAppUsage(
  startHour: 0,
  startMinute: 0,
  endHour: 23,
  endMinute: 59,
  usageInterval: UsageInterval.daily,
  lookbackTimeMs: 10000,
);

if (result.status) {
  // Access current foreground app information
  final currentApp = result.currentForegroundApp;
  print('Current app: ${currentApp?["appName"]}');
}
```

### Get App Usage Data

```dart
final DateTime now = DateTime.now();
final DateTime yesterday = now.subtract(const Duration(days: 1));

final List<AppUsage> usageData = await screenTime.appUsageData(
  startTime: yesterday,
  endTime: now,
  usageInterval: UsageInterval.daily,
);

for (final app in usageData) {
  print('${app.appName}: ${app.usageTime} ms');
}
```

## Example

See the [example](https://github.com/chandrabezzo/screen_time/tree/main/example) directory for a complete sample app demonstrating how to use this plugin.

## Customization

You can customize the blocking UI by overriding the `block_overlay` layout or by providing shield configuration parameters when calling `blockApps`. Refer to the example app for details.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
