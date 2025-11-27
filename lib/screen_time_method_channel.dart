import 'dart:async';
import 'dart:convert';
import 'dart:isolate';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:screen_time/src/model/monitoring_app_usage.dart';
import 'package:screen_time/src/model/screen_time_permission_status.dart';

import 'screen_time_platform_interface.dart';
import 'src/const/argument.dart';
import 'src/const/method_name.dart';
import 'src/model/app_usage.dart';
import 'src/model/installed_app.dart';
import 'src/model/screen_time_permission_type.dart';
import 'src/model/usage_interval.dart';

/// An implementation of [ScreenTimePlatform] that uses method channels.
class MethodChannelScreenTime extends ScreenTimePlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('screen_time');

  /// The event channel used for streaming app usage data
  @visibleForTesting
  final eventChannel = const EventChannel('screen_time/app_usage_stream');

  @override
  Future<List<InstalledApp>> installedApps({
    bool ignoreSystemApps = true,
  }) async {
    final result = await methodChannel
        .invokeMethod<Map<Object?, Object?>>(MethodName.installedApps, {
      Argument.ignoreSystemApps: ignoreSystemApps,
    });

    return await Isolate.run(() async {
      final map = await _convertToStringDynamicMap(result);
      final response = BaseInstalledApp.fromJson(map);
      if (response.status) {
        return response.data;
      } else {
        debugPrint(map.toString());
        return <InstalledApp>[];
      }
    });
  }

  @override
  Future<bool> requestPermission({
    UsageInterval interval = UsageInterval.daily,
    ScreenTimePermissionType permissionType = ScreenTimePermissionType.appUsage,
  }) async {
    return await methodChannel
            .invokeMethod<bool>(MethodName.requestPermission, {
          Argument.interval: interval.name,
          Argument.permissionType: permissionType.name,
        }) ??
        false;
  }

  @override
  Future<ScreenTimePermissionStatus> permissionStatus({
    ScreenTimePermissionType permissionType = ScreenTimePermissionType.appUsage,
  }) async {
    final result =
        await methodChannel.invokeMethod<String>(MethodName.permissionStatus, {
              Argument.permissionType: permissionType.name,
            }) ??
            ScreenTimePermissionStatus.notDetermined.name;
    return ScreenTimePermissionStatus.values.byName(result);
  }

  @override
  Future<Map<String, bool>> checkPermissions() async {
    final result = await methodChannel
        .invokeMethod<Map<Object?, Object?>>(MethodName.checkPermissions);
    if (result == null) {
      return {
        'hasOverlayPermission': false,
        'hasUsageStatsPermission': false,
      };
    }
    return {
      'hasOverlayPermission':
          (result['hasOverlayPermission'] as bool?) ?? false,
      'hasUsageStatsPermission':
          (result['hasUsageStatsPermission'] as bool?) ?? false,
    };
  }

  @override
  Future<List<AppUsage>> appUsageData({
    DateTime? startTime,
    DateTime? endTime,
    UsageInterval usageInterval = UsageInterval.daily,
    List<String>? packagesName,
  }) async {
    final arguments = <Object?, Object?>{};
    if (startTime != null) {
      arguments[Argument.startTimeInMillisecond] =
          startTime.millisecondsSinceEpoch;
    }
    if (endTime != null) {
      arguments[Argument.endTimeInMillisecond] = endTime.millisecondsSinceEpoch;
    }

    if (packagesName != null) {
      arguments[Argument.packagesName] = packagesName;
    }

    final result = await methodChannel.invokeMethod<Map<Object?, Object?>>(
        MethodName.appUsageData, arguments);

    return await Isolate.run(() async {
      final map = await _convertToStringDynamicMap(result);
      final response = BaseAppUsage.fromJson(map);
      if (response.status) {
        return response.data;
      } else {
        debugPrint(map.toString());
        return <AppUsage>[];
      }
    });
  }

  @override
  Future<bool> blockApps({
    List<String> packagesName = const <String>[],
    required String layoutName,
    String? notificationTitle,
    String? notificationText,
    required DateTime endTime,
    String? shieldTitle,
    String? shieldSubtitle,
    String? shieldTitleColor,
    String? shieldSubtitleColor,
    String? shieldButtonLabel,
    String? shieldButtonColor,
    String? shieldButtonTextColor,
    String? shieldIconName,
  }) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    final endTimeMillis = endTime.millisecondsSinceEpoch;
    final diff = endTimeMillis - now;
    final effectiveDuration = diff > 0 ? diff : 0;

    final arguments = <Object?, Object?>{
      Argument.packagesName: packagesName,
      Argument.duration: effectiveDuration,
      Argument.layoutName: layoutName,
      Argument.notificationTitle: notificationTitle,
      Argument.notificationText: notificationText,
      Argument.endTime: endTimeMillis,
      if (shieldTitle != null) Argument.shieldTitle: shieldTitle,
      if (shieldSubtitle != null) Argument.shieldSubtitle: shieldSubtitle,
      if (shieldTitleColor != null)
        Argument.shieldSubtitleColor: shieldTitleColor,
      if (shieldSubtitleColor != null)
        Argument.shieldSubtitleColor: shieldSubtitleColor,
      if (shieldButtonLabel != null)
        Argument.shieldButtonLabel: shieldButtonLabel,
      if (shieldButtonColor != null)
        Argument.shieldButtonColor: shieldButtonColor,
      if (shieldButtonTextColor != null)
        Argument.shieldButtonTextColor: shieldButtonTextColor,
      if (shieldIconName != null) Argument.shieldIconName: shieldIconName,
    };

    return await methodChannel.invokeMethod<bool>(
            MethodName.blockApps, arguments) ??
        false;
  }

  @override
  Future<bool?> scheduleBlock({
    required String scheduleId,
    required List<String> packagesName,
    required DateTime startTime,
    required Duration duration,
    bool recurring = false,
    List<int> daysOfWeek = const [],
  }) async {
    final bool? result =
        await methodChannel.invokeMethod<bool>(MethodName.scheduleBlock, {
      Argument.scheduleId: scheduleId,
      Argument.packagesName: packagesName,
      Argument.startTimeInMillisecond: startTime.millisecondsSinceEpoch,
      Argument.duration: duration.inMilliseconds,
      Argument.recurring: recurring,
      Argument.daysOfWeek: daysOfWeek,
    });
    return result;
  }

  @override
  Future<bool?> cancelScheduledBlock(String scheduleId) async {
    final bool? result = await methodChannel
        .invokeMethod<bool>(MethodName.cancelScheduledBlock, {
      Argument.scheduleId: scheduleId,
    });
    return result;
  }

  @override
  Future<Map<String, dynamic>?> getActiveSchedules() async {
    final Map<String, dynamic>? result = await methodChannel
        .invokeMapMethod<String, dynamic>(MethodName.getActiveSchedules);
    return result;
  }

  @override
  Future<bool> get isOnBlockingApps async =>
      await methodChannel.invokeMethod<bool>(MethodName.isOnBlockingApps) ??
      false;

  @override
  Future<bool> unblockApps({
    List<String> packagesName = const <String>[],
  }) async {
    final arguments = <Object?, Object?>{
      Argument.packagesName: packagesName,
    };

    return await methodChannel.invokeMethod<bool>(
            MethodName.unblockApps, arguments) ??
        false;
  }

  @override
  Future<bool> pauseBlockApps({
    required Duration pauseDuration,
    String? notificationTitle,
    String? notificationText,
    bool showNotification = true,
  }) async {
    final arguments = <Object?, Object?>{
      Argument.pauseDuration: pauseDuration.inMilliseconds,
      Argument.notificationTitle: notificationTitle,
      Argument.notificationText: notificationText,
      Argument.showNotification: showNotification,
    };

    return await methodChannel.invokeMethod<bool>(
            MethodName.pauseBlockApps, arguments) ??
        false;
  }

  @override
  Future<bool> get isOnPausedBlockingApps async {
    final result =
        await methodChannel.invokeMethod<bool>(MethodName.isBlockingPaused);
    return result ?? false;
  }

  @override
  Future<MonitoringAppUsage> monitoringAppUsage({
    int startHour = 0,
    int startMinute = 0,
    int endHour = 23,
    int endMinute = 59,
    UsageInterval usageInterval = UsageInterval.daily,
    int lookbackTimeMs = 10000, // Default: 10 seconds
    List<String>? packagesName,
  }) async {
    final arguments = <Object?, Object?>{
      Argument.startHour: startHour,
      Argument.startMinute: startMinute,
      Argument.endHour: endHour,
      Argument.endMinute: endMinute,
      Argument.lookbackTimeMs: lookbackTimeMs,
      Argument.interval: usageInterval.name,
    };
    if (packagesName != null) {
      arguments[Argument.packagesName] = packagesName;
    }
    final result = await methodChannel.invokeMethod<Map<Object?, Object?>>(
        MethodName.monitoringAppUsage, arguments);
    final map = await _convertToStringDynamicMap(result);
    final response = BaseMonitoringAppUsage.fromJson(map);
    return response.data;
  }

  @override
  Stream<Map<String, dynamic>> streamAppUsage({
    UsageInterval usageInterval = UsageInterval.daily,
    int lookbackTimeMs = 10000,
  }) {
    return eventChannel.receiveBroadcastStream({
      Argument.interval: usageInterval.name,
      Argument.lookbackTimeMs: lookbackTimeMs,
    }).map((dynamic event) {
      // Convert the event data to the expected type
      if (event is Map) {
        return _convertNestedMap(event);
      } else {
        throw PlatformException(
          code: 'INVALID_EVENT',
          message: 'Invalid event format received from native platform',
        );
      }
    });
  }

  /// Helper method to convert the result from native code to the expected Dart type
  Future<Map<String, dynamic>> _convertToStringDynamicMap(
      Map<Object?, Object?>? result) async {
    if (result == null) {
      final error = result?['error'];
      throw Exception(error);
    }

    return await Isolate.run(() {
      final Map<String, dynamic> convertedMap = {};

      result.forEach((key, value) {
        if (key is String) {
          if (value is Map) {
            // Recursively convert nested maps
            convertedMap[key] = _convertNestedMap(value);
          } else if (value is List) {
            // Convert lists
            convertedMap[key] = _convertList(value);
          } else {
            // Direct assignment for primitive types
            convertedMap[key] = value;
          }
        }
      });

      return convertedMap;
    });
  }

  /// Helper method to convert nested maps
  dynamic _convertNestedMap(Map<dynamic, dynamic> map) {
    final convertedMap = <String, dynamic>{};

    map.forEach((key, value) {
      if (key is String) {
        if (value is Map) {
          convertedMap[key] = _convertNestedMap(value);
        } else if (value is List) {
          convertedMap[key] = _convertList(value);
        } else {
          convertedMap[key] = value;
        }
      }
    });

    return convertedMap;
  }

  /// Helper method to convert lists
  List<dynamic> _convertList(List<dynamic> list) {
    return list.map((item) {
      if (item is Map) {
        return _convertNestedMap(item);
      } else if (item is List) {
        return _convertList(item);
      } else {
        return item;
      }
    }).toList();
  }

  @override
  Future<Map<String, dynamic>?> getBlockingStatus() async {
    final result =
        await methodChannel.invokeMethod<String>(MethodName.getBlockingStatus);
    if (result != null) {
      return await Isolate.run(() {
        return Map<String, dynamic>.from(const JsonDecoder().convert(result));
      });
    }
    return null;
  }

  @override
  Future<Map<String, dynamic>?> invokeMethod<T>(String method,
      [Map<String, dynamic>? args]) async {
    final result = await methodChannel.invokeMethod<String>(method, args);
    if (result == null) return null;
    return await Isolate.run(() {
      return Map<String, dynamic>.from(const JsonDecoder().convert(result));
    });
  }

  @override
  Future<bool> clearAllShields() async {
    final ok =
        await methodChannel.invokeMethod<bool>(MethodName.clearAllShields);
    return ok ?? false;
  }
}
