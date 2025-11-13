import 'screen_time_platform_interface.dart';
import 'src/const/argument.dart';
import 'src/const/method_name.dart';
import 'src/model/screen_time_permission_status.dart';
import 'src/model/screen_time_permission_type.dart';
import 'src/model/app_usage.dart';
import 'src/model/installed_app.dart';
import 'src/model/monitoring_app_usage.dart';
import 'src/model/usage_interval.dart';
export 'src/model/screen_time_permission_status.dart';
export 'src/model/installed_app.dart';
export 'src/model/app_category.dart';
export 'src/model/app_usage.dart';
export 'src/model/monitoring_app_usage.dart';
export 'src/model/request_permission_model.dart';
export 'src/model/usage_interval.dart';
export 'src/model/screen_time_permission_type.dart';
export 'src/util/duration_ext.dart';

class ScreenTime {
  ScreenTime._();

  static final ScreenTime _instance = ScreenTime._();

  factory ScreenTime() {
    return _instance;
  }

  /// Request Screen Time permission from the user.
  ///
  /// Parameters:
  /// - `interval`: The interval to use for usage stats queries (DAILY, WEEKLY, MONTHLY, YEARLY, BEST)
  ///
  /// Returns a [bool] with the following keys:
  /// - `true`: Request Permission launched
  /// - `false`: Request Permission failed to launch
  Future<bool> requestPermission({
    UsageInterval interval = UsageInterval.daily,
    ScreenTimePermissionType permissionType = ScreenTimePermissionType.appUsage,
  }) async {
    return await ScreenTimePlatform.instance
        .requestPermission(interval: interval, permissionType: permissionType);
  }

  Future<ScreenTimePermissionStatus> permissionStatus({
    ScreenTimePermissionType permissionType = ScreenTimePermissionType.appUsage,
  }) async {
    return await ScreenTimePlatform.instance
        .permissionStatus(permissionType: permissionType);
  }

  /// Check overlay and usage stats permissions
  ///
  /// Returns a map with the following keys:
  /// - `hasOverlayPermission`: Whether overlay permission is granted
  /// - `hasUsageStatsPermission`: Whether usage stats permission is granted
  Future<Map<String, bool>> checkPermissions() async {
    return await ScreenTimePlatform.instance.checkPermissions();
  }

  Future<List<InstalledApp>> installedApps({
    bool ignoreSystemApps = true,
  }) {
    return ScreenTimePlatform.instance.installedApps(
      ignoreSystemApps: ignoreSystemApps,
    );
  }

  /// Fetch app usage data from the device.
  ///
  /// Returns a map with the following keys:
  /// - `status`: Whether the data was successfully fetched.
  /// - `data`: Map of app bundle IDs to usage data.
  /// - `error`: Error message if the data fetch failed.
  Future<List<AppUsage>> appUsageData({
    DateTime? startTime,
    DateTime? endTime,
    UsageInterval usageInterval = UsageInterval.daily,
    List<String>? packagesName,
  }) {
    return ScreenTimePlatform.instance.appUsageData(
      startTime: startTime,
      endTime: endTime,
      usageInterval: usageInterval,
      packagesName: packagesName,
    );
  }

  /// Block apps for a specified duration
  ///
  /// Parameters:
  /// - `packagesName`: List of package names to block
  /// - `layoutName`: Optional custom layout name to use for the block overlay
  ///   This allows for customizing the UI of the block screen
  /// - `notificationTitle`: Title for the notification (default: "App Blocker Active")
  /// - `notificationText`: Text template for the notification (default: "Blocking {count} apps for {minutes} more minutes")
  ///   You can use placeholders: {count} for number of apps, {duration} or {minutes} for time remaining
  /// - `endTime`: When to stop blocking (DateTime). Must be in the future.
  Future<bool> blockApps({
    List<String> packagesName = const <String>[],
    required String layoutName,
    String? notificationTitle,
    String? notificationText,
    required DateTime endTime,
    // Overlay UI customization (Android)
    String? shieldTitle,
    String? shieldSubtitle,
    String? shieldTitleColor, // e.g. "#FFFFFF"
    String? shieldSubtitleColor, // e.g. "#AAAAAA"
    String? shieldButtonLabel,
    String? shieldButtonColor, // e.g. "#FF0000"
    String? shieldButtonTextColor, // e.g. "#FFFFFF"
    String? shieldIconName, // optional: android drawable name
  }) async {
    return await ScreenTimePlatform.instance.blockApps(
      packagesName: packagesName,
      layoutName: layoutName,
      notificationTitle: notificationTitle,
      notificationText: notificationText,
      endTime: endTime,
      shieldTitle: shieldTitle,
      shieldSubtitle: shieldSubtitle,
      shieldTitleColor: shieldTitleColor,
      shieldSubtitleColor: shieldSubtitleColor,
      shieldButtonLabel: shieldButtonLabel,
      shieldButtonColor: shieldButtonColor,
      shieldButtonTextColor: shieldButtonTextColor,
      shieldIconName: shieldIconName,
    );
  }

  Future<bool> unblockApps({
    List<String> packagesName = const <String>[],
  }) async {
    return await ScreenTimePlatform.instance.unblockApps(
      packagesName: packagesName,
    );
  }

  /// Temporarily pause blocking apps for a specified duration
  /// After the pause duration expires, blocking will automatically resume
  ///
  /// Parameters:
  /// - `pauseDuration`: How long to pause the blocking for
  /// - `notificationTitle`: Title for the notification when blocking resumes
  /// - `notificationText`: Text for the notification when blocking resumes
  /// - `showNotification`: Whether to show a notification when blocking resumes
  Future<bool> pauseBlockApps({
    required Duration pauseDuration,
    String? notificationTitle,
    String? notificationText,
    bool showNotification = true,
  }) async {
    return await ScreenTimePlatform.instance.pauseBlockApps(
      pauseDuration: pauseDuration,
      notificationTitle: notificationTitle,
      notificationText: notificationText,
      showNotification: showNotification,
    );
  }

  /// Check if app blocking is currently paused
  ///
  /// Returns a map with the following keys:
  /// - `isPaused`: Boolean indicating if blocking is currently paused
  /// - `remainingPauseTime`: Duration in milliseconds until blocking resumes (if paused)
  /// - `pausedPackages`: List of package names that will be blocked when pause ends
  /// - `remainingBlockTime`: Duration in milliseconds of blocking that will resume after pause
  Future<bool> get isOnPausedBlockingApps async {
    return await ScreenTimePlatform.instance.isOnPausedBlockingApps;
  }

  Future<bool?> scheduleBlock({
    required String scheduleId,
    required List<String> packagesName,
    required DateTime startTime,
    required Duration duration,
    bool recurring = false,
    List<int> daysOfWeek = const [],
  }) {
    return ScreenTimePlatform.instance.scheduleBlock(
      scheduleId: scheduleId,
      packagesName: packagesName,
      startTime: startTime,
      duration: duration,
      recurring: recurring,
      daysOfWeek: daysOfWeek,
    );
  }

  /// Cancel a scheduled block
  Future<bool?> cancelScheduledBlock(String scheduleId) {
    return ScreenTimePlatform.instance.cancelScheduledBlock(scheduleId);
  }

  /// Get all active block schedules
  Future<Map<String, dynamic>?> getActiveSchedules() {
    return ScreenTimePlatform.instance.getActiveSchedules();
  }

  /// Check if apps are currently being blocked
  Future<bool> get isOnBlockingApps =>
      ScreenTimePlatform.instance.isOnBlockingApps;

  /// - `startHour`: The hour to start monitoring (0-23).
  /// - `startMinute`: The minute to start monitoring (0-59).
  /// - `endHour`: The hour to end monitoring (0-23).
  /// - `endMinute`: The minute to end monitoring (0-59).
  ///
  /// Returns a map with the following keys:
  /// - `status`: Whether monitoring was successfully started.
  /// - `monitoringActive`: Whether monitoring is currently active.
  /// - `schedule`: Map containing schedule details (startTime, endTime, frequency).
  /// - `timestamp`: When monitoring was started.
  /// - `error`: Error message if monitoring failed to start.
  Future<MonitoringAppUsage> monitoringAppUsage({
    required int startHour,
    required int startMinute,
    required int endHour,
    required int endMinute,
    UsageInterval usageInterval = UsageInterval.daily,
    int lookbackTimeMs = 10000, // Default: 10 seconds
    List<String>? packagesName,
  }) {
    return ScreenTimePlatform.instance.monitoringAppUsage(
      startHour: startHour,
      startMinute: startMinute,
      endHour: endHour,
      endMinute: endMinute,
      usageInterval: usageInterval,
      lookbackTimeMs: lookbackTimeMs,
      packagesName: packagesName,
    );
  }

  /// Stream app usage data in real-time.
  ///
  /// This method returns a Stream that emits events whenever the foreground app changes.
  /// It uses the native AppMonitoringService to provide real-time updates.
  ///
  /// Parameters:
  /// - `usageInterval`: The interval to use for usage stats queries (DAILY, WEEKLY, MONTHLY, YEARLY, BEST)
  /// - `lookbackTimeMs`: How far back in time to look for app usage data (in milliseconds)
  ///
  /// Returns a Stream of [Map<String, dynamic>] containing the foreground app data.
  Stream<Map<String, dynamic>> streamAppUsage({
    UsageInterval usageInterval = UsageInterval.daily,
    int lookbackTimeMs = 10000, // Default: 10 seconds
  }) {
    return ScreenTimePlatform.instance.streamAppUsage(
      usageInterval: usageInterval,
      lookbackTimeMs: lookbackTimeMs,
    );
  }

  /// Configures the app monitoring service with the specified interval and lookback time
  ///
  /// Parameters:
  /// - `interval`: The interval to use for usage stats queries (DAILY, WEEKLY, MONTHLY, YEARLY, BEST)
  /// - `lookbackTimeMs`: How far back in time to look for app usage data (in milliseconds)
  ///
  /// Returns `true` if the service was configured successfully, `false` otherwise
  Future<bool> configureAppMonitoringService({
    UsageInterval interval = UsageInterval.daily,
    int lookbackTimeMs = 10000, // Default: 10 seconds
  }) {
    return ScreenTimePlatform.instance.configureAppMonitoringService(
      interval: interval,
      lookbackTimeMs: lookbackTimeMs,
    );
  }

  /// Get current blocking status
  ///
  /// Returns a map with blocking information including:
  /// - `isBlocked`: Whether the app is currently blocked
  /// - `blockedApps`: List of blocked app package names
  /// - `remainingTime`: Remaining blocking time in seconds
  /// - `blockReason`: Reason for blocking
  /// - `canOverride`: Whether the user can override the block
  /// - `remainingTimeFormatted`: Formatted remaining time string
  Future<Map<String, dynamic>?> getBlockingStatus() {
    return ScreenTimePlatform.instance.getBlockingStatus();
  }

  /// iOS: 拉起 FamilyActivityPicker。iOS16+选择完成后原生侧会立即启用屏蔽，
  /// 返回 { success: bool, count: int }；发生错误时返回 { success: false, error: string }。
  /// Android 或低版本 iOS 返回 null。
  ///
  /// 可选参数用于自定义屏蔽页显示内容（需配合 Shield Configuration Extension 使用）：
  /// - [shieldTitle]: 屏蔽页标题
  /// - [shieldSubtitle]: 屏蔽页副标题
  /// - [shieldButtonLabel]: 屏蔽页按钮文字
  /// - [shieldButtonColor]: 屏蔽页按钮背景颜色（十六进制字符串，如 "#FF0000"）
  /// - [shieldButtonTextColor]: 屏蔽页按钮文字颜色（十六进制字符串，如 "#FFFFFF"）
  /// - [shieldIconName]: 屏蔽页图标名称（系统图标，如 "lock.fill", "hand.raised.fill"）
  /// - [shieldIconAsset]: 屏蔽页自定义图标资源名称（需在扩展的 xcassets 中添加，如 "shield_icon"）
  /// - [appGroupIdentifier]: App Group 标识符（用于与 Extension 共享数据，可选）
  /// - [endTime]: 截止时间（DateTime），设置后将在指定时间自动解除屏蔽
  ///
  /// 注意：屏蔽页的整体背景色由系统控制，无法自定义。
  /// 提示：优先使用 [shieldIconAsset]（自定义资源），若未设置则回退到 [shieldIconName]（SF Symbols）。
  Future<Map<String, dynamic>?> pickApps({
    String? shieldTitle,
    String? shieldSubtitle,
    String? shieldSubtitleColor,
    String? shieldButtonLabel,
    String? shieldButtonColor,
    String? shieldButtonTextColor,
    String? shieldIconName,
    String? shieldIconAsset,
    String? appGroupIdentifier,
    DateTime? endTime,
  }) async {
    final args = <String, dynamic>{};
    if (shieldTitle != null) args[Argument.shieldTitle] = shieldTitle;
    if (shieldSubtitle != null) args[Argument.shieldSubtitle] = shieldSubtitle;
    if (shieldButtonLabel != null)
      args[Argument.shieldButtonLabel] = shieldButtonLabel;
    if (shieldSubtitleColor != null)
      args[Argument.shieldSubtitleColor] = shieldSubtitleColor;
    if (shieldButtonColor != null)
      args[Argument.shieldButtonColor] = shieldButtonColor;
    if (shieldButtonTextColor != null)
      args[Argument.shieldButtonTextColor] = shieldButtonTextColor;
    if (shieldIconName != null) args[Argument.shieldIconName] = shieldIconName;
    if (shieldIconAsset != null)
      args[Argument.shieldIconAsset] = shieldIconAsset;
    if (appGroupIdentifier != null)
      args[Argument.appGroupIdentifier] = appGroupIdentifier;
    if (endTime != null)
      args[Argument.endTime] = endTime.millisecondsSinceEpoch;

    return await ScreenTimePlatform.instance
        .invokeMethod<Map<String, dynamic>?>(
            MethodName.pickApps, args.isEmpty ? null : args);
  }

  // pickAppsSelection 已移除

  // applyShields 已移除

  /// iOS: 清空所有屏蔽（应用、类别、域名）
  Future<bool> clearAllShields() {
    return ScreenTimePlatform.instance.clearAllShields();
  }

  /// iOS: 读取已保存的选择数量（app 与分类）
  /// 返回 { success, appCount, categoryCount, totalSelected }
  Future<Map<String, dynamic>?> readSelectionCounts({
    String? appGroupIdentifier,
    String selectionStorageKey = 'screenTimeSelection',
  }) {
    final args = <String, dynamic>{
      Argument.appGroupIdentifier: appGroupIdentifier,
      Argument.selectionStorageKey: selectionStorageKey,
    };
    return ScreenTimePlatform.instance.invokeMethod<Map<String, dynamic>?>(
        MethodName.readSelectionCounts, args);
  }

  /// iOS: 检查 FamilyControls 授权状态
  /// 返回 { success, status: "approved"|"denied"|"notDetermined", isApproved: bool }
  /// - status: 授权状态字符串
  /// - isApproved: 是否已授权（便捷字段）
  Future<Map<String, dynamic>?> getFamilyControlsAuthorizationStatus() {
    return ScreenTimePlatform.instance.invokeMethod<Map<String, dynamic>?>(
        MethodName.getFamilyControlsAuthorizationStatus, null);
  }

  /// iOS: 主动请求 FamilyControls 授权
  /// 会弹出系统权限请求对话框
  /// 返回 { success, status: "approved"|"denied"|"notDetermined", isApproved: bool, error?: string }
  /// - status: 请求后的授权状态
  /// - isApproved: 是否已授权（便捷字段）
  /// - error: 如果请求失败，包含错误信息
  Future<Map<String, dynamic>?> requestFamilyControlsAuthorization() {
    return ScreenTimePlatform.instance.invokeMethod<Map<String, dynamic>?>(
        MethodName.requestFamilyControlsAuthorization, null);
  }

  /// iOS: 获取 DeviceActivityMonitorExtension 的调试信息
  /// 用于检查 Extension 是否正常运行
  ///
  /// 返回的 Map 包含以下字段（如果存在）：
  /// - success: bool - 是否成功
  /// - lastIntervalDidStart: int - 最后开始时间（毫秒时间戳）
  /// - lastActivityName: String - 最后开始的 Activity 名称
  /// - lastIntervalDidEnd: int - 最后结束时间（毫秒时间戳）
  /// - lastEndActivityName: String - 最后结束的 Activity 名称
  /// - currentScheduleId: String - 当前 Schedule ID
  /// - currentScheduleEndTime: int - 当前 Schedule 结束时间（毫秒时间戳）
  /// - isScheduleActive: bool - Schedule 是否激活
  /// - timeRemaining: int - 剩余时间（毫秒）
  /// - lastUnlockSuccess: bool - 最后解锁是否成功
  /// - lastUnlockTime: int - 最后解锁时间（毫秒时间戳）
  /// - appGroupIdentifier: String - App Group ID
  /// - appGroupAccessible: bool - App Group 是否可访问
  ///
  /// 参数：
  /// - [appGroupIdentifier]: App Group 标识符（可选，用于读取共享的 UserDefaults）
  Future<Map<String, dynamic>?> getExtensionDebugInfo({
    String? appGroupIdentifier,
  }) {
    final args = <String, dynamic>{};
    if (appGroupIdentifier != null) {
      args[Argument.appGroupIdentifier] = appGroupIdentifier;
    }
    return ScreenTimePlatform.instance.invokeMethod<Map<String, dynamic>?>(
        MethodName.getExtensionDebugInfo, args.isEmpty ? null : args);
  }

  /// iOS: 展示系统应用选择器并保存所选 FamilyActivitySelection
  /// - 在展示前会尝试读取并应用已保存的选择
  /// - 返回统计信息 { success, appCount, categoryCount, totalSelected }
  Future<Map<String, dynamic>?> selectAndSaveApps({
    String? appGroupIdentifier,
    String selectionStorageKey = 'screenTimeSelection',
  }) {
    final args = <String, dynamic>{
      Argument.appGroupIdentifier: appGroupIdentifier,
      Argument.selectionStorageKey: selectionStorageKey,
    };
    return ScreenTimePlatform.instance.invokeMethod<Map<String, dynamic>?>(
        MethodName.selectAndSaveApps, args);
  }

  /// iOS: 读取已保存的 FamilyActivitySelection 并应用到系统屏蔽
  /// - 返回统计信息 { success, appliedApps, appliedCategories, totalApplied }
  Future<Map<String, dynamic>?> applySavedSelection({
    String? appGroupIdentifier,
    String selectionStorageKey = 'screenTimeSelection',
    String? shieldTitle,
    String? shieldSubtitle,
    String? shieldSubtitleColor,
    String? shieldButtonLabel,
    String? shieldButtonColor,
    String? shieldButtonTextColor,
    String? shieldIconName,
    String? shieldIconAsset,
    DateTime? endTime,
  }) {
    final args = <String, dynamic>{
      Argument.appGroupIdentifier: appGroupIdentifier,
      Argument.selectionStorageKey: selectionStorageKey,
      if (shieldTitle != null) Argument.shieldTitle: shieldTitle,
      if (shieldSubtitle != null) Argument.shieldSubtitle: shieldSubtitle,
      if (shieldButtonLabel != null)
        Argument.shieldButtonLabel: shieldButtonLabel,
      if (shieldSubtitleColor != null)
        Argument.shieldSubtitleColor: shieldSubtitleColor,
      if (shieldButtonColor != null)
        Argument.shieldButtonColor: shieldButtonColor,
      if (shieldButtonTextColor != null)
        Argument.shieldButtonTextColor: shieldButtonTextColor,
      if (shieldIconName != null) Argument.shieldIconName: shieldIconName,
      if (shieldIconAsset != null) Argument.shieldIconAsset: shieldIconAsset,
      if (endTime != null) Argument.endTime: endTime.millisecondsSinceEpoch,
    };
    return ScreenTimePlatform.instance.invokeMethod<Map<String, dynamic>?>(
        MethodName.applySavedSelection, args);
  }
}
