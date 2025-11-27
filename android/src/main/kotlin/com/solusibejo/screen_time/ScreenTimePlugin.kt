package com.solusibejo.screen_time

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Configuration
import androidx.work.WorkManager
import com.solusibejo.screen_time.const.Argument
import com.solusibejo.screen_time.const.Field
import com.solusibejo.screen_time.const.MethodName
import com.solusibejo.screen_time.const.ScreenTimePermissionType
import com.solusibejo.screen_time.const.UsageInterval
import com.solusibejo.screen_time.monitor.UsageStatsMonitor
import com.solusibejo.screen_time.service.BlockAppService
import com.solusibejo.screen_time.util.EnumExtension.toCamelCase
import com.solusibejo.screen_time.util.EnumExtension.toEnumFormat
import io.flutter.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import android.app.Activity
import android.content.Intent
import io.flutter.plugin.common.PluginRegistry
import org.json.JSONObject
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.Locale

/** ScreenTimePlugin */
class ScreenTimePlugin: FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler, ActivityAware, PluginRegistry.ActivityResultListener {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var eventChannel: EventChannel
  private lateinit var context: Context
  private lateinit var sharedPreferences: SharedPreferences
  private var eventSink: EventChannel.EventSink? = null
  private var activity: Activity? = null
  private var pendingResult: Result? = null
  private var pendingMethod: String? = null
  private val REQUEST_PICK_APPS = 10080
  private val REQUEST_SELECT_AND_SAVE = 10081
  private var pendingSelectionStorageKey: String = "screenTimeSelection"
  private var pendingPickEndTimeMs: Long? = null
  private var pendingShieldConfig: Map<String, String?>? = null
  private var usageStatsMonitor: UsageStatsMonitor? = null

  companion object {
    const val PREF_NAME = "screen_time"
    const val PACKAGE_NAME = "com.solusibejo.screen_time"
  }

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "screen_time")
    channel.setMethodCallHandler(this)
    
    context = flutterPluginBinding.applicationContext

    // Initialize WorkManager
    try {
      val config = Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.INFO)
        .build()
      WorkManager.initialize(context, config)
    } catch (e: IllegalStateException) {
      // WorkManager might already be initialized by the app
      Log.i("ScreenTimePlugin", "WorkManager already initialized")
    }

    // Set up event channel for streaming app usage data
    eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "screen_time/app_usage_stream")
    eventChannel.setStreamHandler(this)
    
    sharedPreferences = context.getSharedPreferences("screen_time", Context.MODE_PRIVATE)
  }
  // ActivityAware
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivity() {
    activity = null
  }


  override fun onMethodCall(call: MethodCall, result: Result) {
    when(call.method){
      MethodName.installedApps -> {
        val args = call.arguments as Map<String, Any?>
        val ignoreSystemApps = args[Argument.ignoreSystemApps] as Boolean? ?: true

        CoroutineScope(Dispatchers.IO).launch {
          val installedApps = ScreenTimeMethod.installedApps(context, ignoreSystemApps)
          withContext(Dispatchers.Main){
            result.success(installedApps)
          }
        }
      }
      MethodName.requestPermission -> {
        val args = call.arguments as Map<String, Any?>
        val usageInterval = args[Argument.interval] as String
        val permissionType = args[Argument.permissionType] as String

        val response = ScreenTimeMethod.requestPermission(context,
          UsageInterval.valueOf(usageInterval.uppercase(Locale.getDefault())),
          ScreenTimePermissionType.valueOf(permissionType.toEnumFormat()),
        )
        if(response){
          result.success(true)
        }
        else {
          result.success(false)
        }
      }
      MethodName.permissionStatus -> {
        val args = call.arguments as Map<String, Any?>
        val permissionType = args[Argument.permissionType] as String

        val response = ScreenTimeMethod.permissionStatus(context,
          ScreenTimePermissionType.valueOf(permissionType.toEnumFormat())
        )

        result.success(response.name.toCamelCase())
      }
      MethodName.checkPermissions -> {
        val permissions = ScreenTimeMethod.checkPermissions(context)
        result.success(permissions)
      }
      MethodName.appUsageData -> {
        val args = call.arguments as Map<String, Any?>
        val startTimeInMillisecond = when (val value = args[Argument.startTimeInMillisecond]) {
          is Int -> value.toLong()
          is Long -> value
          is Number -> value.toLong()
          else -> null
        }
        val endTimeInMillisecond = when (val value = args[Argument.endTimeInMillisecond]) {
          is Int -> value.toLong()
          is Long -> value
          is Number -> value.toLong()
          else -> null
        }
        val usageInterval = args[Argument.interval] as String?
            ?: UsageInterval.DAILY.name.lowercase()
        val packagesName = args[Argument.packagesName] as List<*>?

        val data = ScreenTimeMethod.appUsageData(
          context,
          startTimeInMillisecond,
          endTimeInMillisecond,
          UsageInterval.valueOf(usageInterval.uppercase(Locale.getDefault())),
          packagesName?.filterIsInstance<String>(),
        )
        val status = data[Field.status]
        if(status == true){
          result.success(data)
        }
        else {
          val error = data[Field.error]
          result.error("500", "Failed to fetch app usage data", error)
        }
      }
      MethodName.blockApps -> {
        val args = call.arguments as Map<String, Any?>
        val packagesName = args[Argument.packagesName] as List<*>?
        val durationInMillisecond = when (val value = args[Argument.duration]) {
          is Int -> value.toLong()
          is Long -> value
          is Number -> value.toLong()
          else -> 0L
        }
        val endTimeMillis = when (val value = args[Argument.endTime]) {
          is Int -> value.toLong()
          is Long -> value
          is Number -> value.toLong()
          else -> null
        }
        val now = System.currentTimeMillis()
        val targetEndTime = when {
          endTimeMillis != null && endTimeMillis > now -> endTimeMillis
          endTimeMillis != null -> now
          else -> now + durationInMillisecond
        }
        val effectiveDurationMillis = (targetEndTime - now).coerceAtLeast(0L)
        val duration = Duration.ofMillis(effectiveDurationMillis)
        val layoutName = args[Argument.layoutName] as String?
        val notificationTitle = args[Argument.notificationTitle] as String?
        val notificationText = args[Argument.notificationText] as String?
        val shieldTitle = args[Argument.shieldTitle] as String?
        val shieldSubtitle = args[Argument.shieldSubtitle] as String?
        val shieldTitleColor = args[Argument.shieldSubtitleColor] as String?
        val shieldSubtitleColor = args[Argument.shieldSubtitleColor] as String?
        val shieldButtonLabel = args[Argument.shieldButtonLabel] as String?
        val shieldButtonColor = args[Argument.shieldButtonColor] as String?
        val shieldButtonTextColor = args[Argument.shieldButtonTextColor] as String?
        val shieldIconName = args[Argument.shieldIconName] as String?

        val response = ScreenTimeMethod.blockApps(
          context,
          packagesName?.filterIsInstance<String>() ?: mutableListOf(),
          duration,
          sharedPreferences,
          layoutName,
          notificationTitle,
          notificationText,
          targetEndTime,
          uiTitle = shieldTitle,
          uiSubtitle = shieldSubtitle,
          uiTitleColor = shieldTitleColor,
          uiSubtitleColor = shieldSubtitleColor,
          uiButtonLabel = shieldButtonLabel,
          uiButtonColor = shieldButtonColor,
          uiButtonTextColor = shieldButtonTextColor,
          uiIconName = shieldIconName
        )

        result.success(response)
      }
      MethodName.scheduleBlock -> {
        val args = call.arguments as Map<String, Any?>
        val scheduleId = args[Argument.scheduleId] as String
        val packagesName = args[Argument.packagesName] as List<*>?
        val startTime = when (val value = args[Argument.startTime]) {
          is Int -> value.toLong()
          is Long -> value
          is Number -> value.toLong()
          else -> 0L
        }
        val durationInMillisecond = when (val value = args[Argument.duration]) {
          is Int -> value.toLong()
          is Long -> value
          is Number -> value.toLong()
          else -> 0L
        }
        val recurring = args[Argument.recurring]as Boolean
        val daysOfWeek = args[Argument.daysOfWeek] as List<*>?

        val duration = Duration.ofMillis(durationInMillisecond)

        ScreenTimeMethod.scheduleBlock(
          context,
          scheduleId,
          packagesName?.filterIsInstance<String>() ?: mutableListOf(),
          startTime.toLong(),
          duration,
          recurring,
          daysOfWeek?.filterIsInstance<Int>() ?: mutableListOf(),
        ) { callback ->
          result.success(callback)
        }
      }
      MethodName.cancelScheduledBlock -> {
        val args = call.arguments as Map<String, Any?>
        val scheduleId = args[Argument.scheduleId] as String

        ScreenTimeMethod.cancelScheduledBlock(
          context,
          scheduleId,
        ) { callback ->
          result.success(callback)
        }
      }
      MethodName.getActiveSchedules -> {
        ScreenTimeMethod.getActiveSchedules(
          context,
        ) { callback ->
          result.success(callback)
        }
      }
      MethodName.isOnBlockingApps -> {
        val response = ScreenTimeMethod.isOnBlockingApps(context)
        result.success(response)
      }
      MethodName.unblockApps -> {
        val args = call.arguments as Map<String, Any?>
        val packagesName = args[Argument.packagesName] as List<*>?

        val response = ScreenTimeMethod.unblockApps(
          context,
          packagesName?.filterIsInstance<String>() ?: mutableListOf(),
          sharedPreferences
        )
        result.success(response)
      }
      MethodName.monitoringAppUsage -> {
        val args = call.arguments as Map<String, Any?>
        val startHour = when (val value = args[Argument.startHour]) {
          is Int -> value
          is Number -> value.toInt()
          else -> 0
        }
        val startMinute = when (val value = args[Argument.startMinute]) {
          is Int -> value
          is Number -> value.toInt()
          else -> 0
        }
        val endHour = when (val value = args[Argument.endHour]) {
          is Int -> value
          is Number -> value.toInt()
          else -> 23
        }
        val endMinute = when (val value = args[Argument.endMinute]) {
          is Int -> value
          is Number -> value.toInt()
          else -> 59
        }
        val usageInterval = args[Argument.interval] as String
        val lookbackTimeMs = when (val value = args[Argument.lookbackTimeMs]) {
          is Int -> value.toLong()
          is Long -> value
          is Number -> value.toLong()
          else -> 10000L
        }
        val packagesName = args[Argument.packagesName] as List<*>?

        val data = ScreenTimeMethod.monitoringAppUsage(
          context,
          startHour,
          startMinute,
          endHour,
          endMinute,
          UsageInterval.valueOf(usageInterval.uppercase(Locale.getDefault())),
          lookbackTimeMs,
          packagesName?.filterIsInstance<String>(),
        )

        val status = data[Field.status]
        if(status == true){
          result.success(data)
        }
        else {
          val error = data[Field.error]
          result.error("500", "Failed to start monitoring app usage", error)
        }
      }
      MethodName.pauseBlockApps -> {
        val args = call.arguments as Map<String, Any?>
        val pauseDurationInMillisecond = when (val value = args[Argument.pauseDuration]) {
          is Int -> value.toLong()
          is Long -> value
          is Number -> value.toLong()
          else -> 0L
        }
        val pauseDuration = Duration.ofMillis(pauseDurationInMillisecond)
        val notificationTitle = args[Argument.notificationTitle] as String?
        val notificationText = args[Argument.notificationText] as String?
        val showNotification = args[Argument.showNotification] as Boolean? ?: true

        val response = ScreenTimeMethod.pauseBlockApps(
          context,
          pauseDuration,
          sharedPreferences,
          notificationTitle,
          notificationText,
          showNotification
        )

        result.success(response)
      }
      MethodName.isBlockingPaused -> {
        val response = ScreenTimeMethod.isBlockingPaused(
          context,
          sharedPreferences
        )
        result.success(response)
      }
      // ===== iOS parity methods (Android implementations) =====
      MethodName.pickApps -> {
        val args = call.arguments as? Map<String, Any?> ?: emptyMap()
        val act = activity
        if (act == null) {
          result.error("NO_ACTIVITY", "No foreground activity to present selector", null)
          return
        }
        if (pendingResult != null) {
          result.error("BUSY", "Another selection is in progress", null)
          return
        }
        pendingResult = result
        pendingMethod = MethodName.pickApps
        pendingSelectionStorageKey =
          (args[Argument.selectionStorageKey] as? String)?.takeIf { it.isNotBlank() }
            ?: "screenTimeSelection"
        pendingPickEndTimeMs = when (val value = args[Argument.endTime]) {
          is Int -> value.toLong()
          is Long -> value
          is Number -> value.toLong()
          else -> null
        }
        val config = mutableMapOf<String, String?>()
        config[Argument.shieldTitle] = args[Argument.shieldTitle] as? String
        config[Argument.shieldSubtitle] = args[Argument.shieldSubtitle] as? String
        config[Argument.shieldSubtitleColor] = args[Argument.shieldSubtitleColor] as? String
        config[Argument.shieldButtonLabel] = args[Argument.shieldButtonLabel] as? String
        config[Argument.shieldButtonColor] = args[Argument.shieldButtonColor] as? String
        config[Argument.shieldButtonTextColor] =
          args[Argument.shieldButtonTextColor] as? String
        config[Argument.shieldIconName] = args[Argument.shieldIconName] as? String
        config[Argument.shieldIconAsset] = args[Argument.shieldIconAsset] as? String
        pendingShieldConfig = config

        val intent = Intent(act, AppSelectionActivity::class.java).apply {
          putExtra("selectionStorageKey", pendingSelectionStorageKey)
          putExtra("shieldTitle", config[Argument.shieldTitle])
          putExtra("confirmButtonLabel", config[Argument.shieldButtonLabel])
          putExtra("confirmButtonColor", config[Argument.shieldButtonColor])
          putExtra("confirmButtonTextColor", config[Argument.shieldButtonTextColor])
          putExtra("cancelButtonLabel", "取消")
        }
        act.startActivityForResult(intent, REQUEST_PICK_APPS)
      }
      MethodName.getFamilyControlsAuthorizationStatus -> {
        val st = ScreenTimeMethod.permissionStatus(
          context,
          ScreenTimePermissionType.APP_USAGE
        )
        val approved = (st == com.solusibejo.screen_time.const.ScreenTimePermissionStatus.APPROVED)
        val status = when (st) {
          com.solusibejo.screen_time.const.ScreenTimePermissionStatus.APPROVED -> "approved"
          com.solusibejo.screen_time.const.ScreenTimePermissionStatus.DENIED -> "denied"
          else -> "notDetermined"
        }
        val map = mapOf(
          "success" to true,
          "status" to status,
          "isApproved" to approved
        )
        result.success(JSONObject(map).toString())
      }
      MethodName.requestFamilyControlsAuthorization -> {
        ScreenTimeMethod.requestPermission(
          context,
          UsageInterval.DAILY,
          ScreenTimePermissionType.APP_USAGE
        )
        val newStatus = ScreenTimeMethod.permissionStatus(
          context,
          ScreenTimePermissionType.APP_USAGE
        )
        val statusText = when (newStatus) {
          com.solusibejo.screen_time.const.ScreenTimePermissionStatus.APPROVED -> "approved"
          com.solusibejo.screen_time.const.ScreenTimePermissionStatus.DENIED -> "denied"
          else -> "notDetermined"
        }
        val map = mapOf(
          "success" to (newStatus == com.solusibejo.screen_time.const.ScreenTimePermissionStatus.APPROVED),
          "status" to statusText,
          "isApproved" to (newStatus == com.solusibejo.screen_time.const.ScreenTimePermissionStatus.APPROVED)
        )
        result.success(JSONObject(map).toString())
      }
      MethodName.selectAndSaveApps -> {
        val args = call.arguments as Map<String, Any?>
        val selectionStorageKey = args[Argument.selectionStorageKey] as String? ?: "screenTimeSelection"
        val act = activity
        if (act == null) {
          result.error("NO_ACTIVITY", "No foreground activity to present selector", null)
          return
        }
        if (pendingResult != null) {
          result.error("BUSY", "Another selection is in progress", null)
          return
        }
        pendingResult = result
        pendingMethod = MethodName.selectAndSaveApps
        pendingSelectionStorageKey = selectionStorageKey
        val intent = Intent(act, AppSelectionActivity::class.java)
        intent.putExtra("selectionStorageKey", selectionStorageKey)
        act.startActivityForResult(intent, REQUEST_SELECT_AND_SAVE)
      }
      MethodName.readSelectionCounts -> {
        val args = call.arguments as Map<String, Any?>
        val selectionStorageKey = args[Argument.selectionStorageKey] as String? ?: "screenTimeSelection"
        val appCount = sharedPreferences.getInt("${selectionStorageKey}_app_count", 0)
        val categoryCount = sharedPreferences.getInt("${selectionStorageKey}_category_count", 0)
        val total = sharedPreferences.getInt("${selectionStorageKey}_total_selected", appCount + categoryCount)
        val map = mapOf(
          "success" to true,
          "appCount" to appCount,
          "categoryCount" to categoryCount,
          "totalSelected" to total
        )
        result.success(JSONObject(map).toString())
      }
      MethodName.applySavedSelection -> {
        val args = call.arguments as Map<String, Any?>
        val selectionStorageKey = args[Argument.selectionStorageKey] as String? ?: "screenTimeSelection"
        val appCount = sharedPreferences.getInt("${selectionStorageKey}_app_count", 0)
        val categoryCount = sharedPreferences.getInt("${selectionStorageKey}_category_count", 0)
        val packages = sharedPreferences.getStringSet("${selectionStorageKey}_packages", emptySet())?.toList() ?: emptyList()

        val config = mutableMapOf<String, String?>()
        config[Argument.shieldTitle] = args[Argument.shieldTitle] as? String
        config[Argument.shieldSubtitle] = args[Argument.shieldSubtitle] as? String
        config[Argument.shieldSubtitleColor] = args[Argument.shieldSubtitleColor] as? String
        config[Argument.shieldButtonLabel] = args[Argument.shieldButtonLabel] as? String
        config[Argument.shieldButtonColor] = args[Argument.shieldButtonColor] as? String
        config[Argument.shieldButtonTextColor] = args[Argument.shieldButtonTextColor] as? String
        config[Argument.shieldIconName] = args[Argument.shieldIconName] as? String
        config[Argument.shieldIconAsset] = args[Argument.shieldIconAsset] as? String
        persistShieldConfiguration(config)

        // 如果提供了 endTime，根据当前时间计算阻止时长并启动阻止
        val endTimeVal = args[Argument.endTime]
        val endTimeMs: Long? = when (endTimeVal) {
          is Int -> endTimeVal.toLong()
          is Long -> endTimeVal
          else -> null
        }
        if (endTimeMs != null) {
          sharedPreferences.edit().putLong("shield_config_end_time", endTimeMs).apply()
        } else {
          sharedPreferences.edit().remove("shield_config_end_time").apply()
        }
        if (!packages.isNullOrEmpty() && endTimeMs != null && endTimeMs > System.currentTimeMillis()) {
          val durationMs = endTimeMs - System.currentTimeMillis()
          val duration = java.time.Duration.ofMillis(durationMs)
          ScreenTimeMethod.blockApps(
            context,
            packages,
            duration,
            sharedPreferences,
            null,
            null,
            null,
            endTimeMs
          )
        }

        val map = mapOf(
          "success" to true,
          "appliedApps" to appCount,
          "appliedCategories" to categoryCount,
          "totalApplied" to (appCount + categoryCount)
        )
        result.success(JSONObject(map).toString())
      }
      MethodName.getExtensionDebugInfo -> {
        val debugInfo = ScreenTimeMethod.getExtensionDebugInfo(context, sharedPreferences)
        result.success(JSONObject(debugInfo).toString())
      }
      MethodName.clearAllShields -> {
        // 从实际的屏蔽状态中读取被屏蔽的 app，而不是从 selection 中读取
        val packages = sharedPreferences.getStringSet(BlockAppService.KEY_BLOCKED_PACKAGES, emptySet())?.toList() ?: emptyList()
        val ok = ScreenTimeMethod.unblockApps(context, packages, sharedPreferences)
        result.success(ok)
      }
      MethodName.getBlockingStatus -> {
        val status = ScreenTimeMethod.getBlockingStatus(context, sharedPreferences)
        result.success(JSONObject(status).toString())
      }
      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    when (requestCode) {
      REQUEST_PICK_APPS -> {
        val res = pendingResult
        pendingResult = null
        pendingMethod = null

        val success = data?.getBooleanExtra("success", false) ?: (resultCode == Activity.RESULT_OK)
        val appCount = data?.getIntExtra("appCount", 0) ?: 0
        val categoryCount = data?.getIntExtra("categoryCount", 0) ?: 0
        val packages = data?.getStringArrayListExtra("packages") ?: arrayListOf<String>()

        if (success) {
          persistShieldConfiguration(pendingShieldConfig)
          val endTimeMs = pendingPickEndTimeMs
          if (endTimeMs != null) {
            sharedPreferences.edit().putLong("shield_config_end_time", endTimeMs).apply()
            if (packages.isNotEmpty() && endTimeMs > System.currentTimeMillis()) {
              val remaining = endTimeMs - System.currentTimeMillis()
              if (remaining > 0) {
                ScreenTimeMethod.blockApps(
                  context,
                  packages,
                  Duration.ofMillis(remaining),
                  sharedPreferences,
                  null,
                  null,
                  null,
                  endTimeMs
                )
              }
            }
          } else {
            sharedPreferences.edit().remove("shield_config_end_time").apply()
          }
        }

        pendingShieldConfig = null
        pendingPickEndTimeMs = null

        val map = mutableMapOf<String, Any?>(
          "success" to success,
          "appCount" to appCount,
          "categoryCount" to categoryCount,
          "totalSelected" to (appCount + categoryCount)
        )
        if (!success) {
          map["error"] = "cancelled"
        }

        res?.success(JSONObject(map).toString())
        return true
      }
      REQUEST_SELECT_AND_SAVE -> {
        val res = pendingResult
        pendingResult = null
        pendingMethod = null
        val success = data?.getBooleanExtra("success", false) ?: (resultCode == Activity.RESULT_OK)
        val appCount = data?.getIntExtra("appCount", 0) ?: 0
        val categoryCount = data?.getIntExtra("categoryCount", 0) ?: 0
        val map = mutableMapOf<String, Any?>(
          "success" to success,
          "appCount" to appCount,
          "categoryCount" to categoryCount,
          "totalSelected" to (appCount + categoryCount)
        )
        if (!success) {
          map["error"] = "cancelled"
        }
        res?.success(JSONObject(map).toString())
        return true
      }
    }
    return false
  }
  
  // StreamHandler implementation for EventChannel
  override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
    eventSink = events
    
    try {
      // Extract parameters from arguments
      val args = arguments as? Map<*, *>
      val intervalName = args?.get(Argument.interval) as? String ?: UsageInterval.DAILY.name
      val usageInterval = UsageInterval.valueOf(intervalName.uppercase(Locale.getDefault()))
      val lookbackArg = args?.get(Argument.lookbackTimeMs)
      val lookbackTimeMs = when (lookbackArg) {
        is Int -> lookbackArg.toLong()
        is Long -> lookbackArg
        is Number -> lookbackArg.toLong()
        else -> 10000L
      }

      if (!ScreenTimeMethod.checkIfStatsAreAvailable(context, usageInterval)) {
        eventSink?.error(
          "PERMISSION_DENIED",
          "Usage access permission is required to stream app usage data.",
          null
        )
        eventSink = null
        return
      }

      usageStatsMonitor?.stop()
      usageStatsMonitor = UsageStatsMonitor(
        context = context,
        interval = usageInterval,
        lookbackTimeMs = lookbackTimeMs
      ) { appData ->
        eventSink?.success(appData)
      }.also { monitor ->
        monitor.start()
      }
    } catch (e: Exception) {
      eventSink?.error("500", "Failed to start app usage streaming", e.message)
      eventSink = null
    }
  }
  
  override fun onCancel(arguments: Any?) {
    try {
      // Clean up resources when the stream is cancelled
      eventSink = null
      usageStatsMonitor?.stop()
      usageStatsMonitor = null
    } catch (e: Exception) {
      // Log the error but don't throw as we're cleaning up
      android.util.Log.e("ScreenTimePlugin", "Error cleaning up stream: ${e.message}")
    }
  }

  private fun persistShieldConfiguration(config: Map<String, String?>?) {
    val editor = sharedPreferences.edit()
    val mapping = mapOf(
      Argument.shieldTitle to "shield_config_title",
      Argument.shieldSubtitle to "shield_config_subtitle",
      Argument.shieldSubtitleColor to "shield_config_subtitle_color",
      Argument.shieldButtonLabel to "shield_config_button_label",
      Argument.shieldButtonColor to "shield_config_button_color",
      Argument.shieldButtonTextColor to "shield_config_button_text_color",
      Argument.shieldIconName to "shield_config_icon_name",
      Argument.shieldIconAsset to "shield_config_icon_asset"
    )
    mapping.forEach { (argKey, prefKey) ->
      val value = config?.get(argKey)
      if (value.isNullOrBlank()) {
        editor.remove(prefKey)
      } else {
        editor.putString(prefKey, value)
      }
    }
    editor.apply()
  }
}
