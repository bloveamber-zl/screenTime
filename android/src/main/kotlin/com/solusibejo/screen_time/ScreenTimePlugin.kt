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
import com.solusibejo.screen_time.service.AppMonitoringService
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
  private val REQUEST_SELECT_AND_SAVE = 10081

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
      MethodName.appUsageData -> {
        val args = call.arguments as Map<String, Any?>
        val startTimeInMillisecond = args[Argument.startTimeInMillisecond] as Int?
        val endTimeInMillisecond = args[Argument.endTimeInMillisecond] as Int?
        val usageInterval = args[Argument.interval] as String?
            ?: UsageInterval.DAILY.name.lowercase()
        val packagesName = args[Argument.packagesName] as List<*>?

        val data = ScreenTimeMethod.appUsageData(
          context,
          startTimeInMillisecond?.toLong(),
          endTimeInMillisecond?.toLong(),
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
        val durationInMillisecond = args[Argument.duration] as Int
        val duration = Duration.ofMillis(durationInMillisecond.toLong())
        val layoutName = args[Argument.layoutName] as String?
        val notificationTitle = args[Argument.notificationTitle] as String?
        val notificationText = args[Argument.notificationText] as String?

        val response = ScreenTimeMethod.blockApps(
          context,
          packagesName?.filterIsInstance<String>() ?: mutableListOf(),
          duration,
          sharedPreferences,
          layoutName,
          notificationTitle,
          notificationText
        )

        result.success(response)
      }
      MethodName.scheduleBlock -> {
        val args = call.arguments as Map<String, Any?>
        val scheduleId = args[Argument.scheduleId] as String
        val packagesName = args[Argument.packagesName] as List<*>?
        val startTime = args[Argument.startTime] as Int
        val durationInMillisecond = args[Argument.duration] as Int
        val recurring = args[Argument.recurring]as Boolean
        val daysOfWeek = args[Argument.daysOfWeek] as List<*>?

        val duration = Duration.ofMillis(durationInMillisecond.toLong())

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
        val startHour = args[Argument.startHour] as Int
        val startMinute = args[Argument.startMinute] as Int
        val endHour = args[Argument.endHour] as Int
        val endMinute = args[Argument.endMinute] as Int
        val usageInterval = args[Argument.interval] as String
        val lookbackTimeMs = args[Argument.lookbackTimeMs] as Int
        val packagesName = args[Argument.packagesName] as List<*>?

        val data = ScreenTimeMethod.monitoringAppUsage(
          context,
          startHour,
          startMinute,
          endHour,
          endMinute,
          UsageInterval.valueOf(usageInterval.uppercase(Locale.getDefault())),
          lookbackTimeMs.toLong(),
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
      MethodName.configureAppMonitoringService -> {
        val args = call.arguments as Map<String, Any?>
        val interval = args[Argument.interval] as String
        val lookbackTimeMs = args[Argument.lookbackTimeMs] as Int
        
        val data = ScreenTimeMethod.configureAppMonitoringService(
          UsageInterval.valueOf(interval.uppercase(Locale.getDefault())),
          lookbackTimeMs.toLong(),
        )
        result.success(data)
      }
      MethodName.pauseBlockApps -> {
        val args = call.arguments as Map<String, Any?>
        val pauseDurationInMillisecond = args[Argument.pauseDuration] as Int
        val pauseDuration = Duration.ofMillis(pauseDurationInMillisecond.toLong())
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
      MethodName.getFamilyControlsAuthorizationStatus -> {
        val st = ScreenTimeMethod.permissionStatus(context, com.solusibejo.screen_time.const.ScreenTimePermissionType.APP_USAGE)
        val approved = (st == com.solusibejo.screen_time.const.ScreenTimePermissionStatus.APPROVED)
        val status = if (approved) "approved" else "denied"
        val map = mapOf(
          "success" to true,
          "status" to status,
          "isApproved" to approved
        )
        result.success(JSONObject(map).toString())
      }
      MethodName.requestFamilyControlsAuthorization -> {
        // 打开使用情况访问设置页
        ScreenTimeMethod.requestPermission(context, com.solusibejo.screen_time.const.UsageInterval.DAILY, com.solusibejo.screen_time.const.ScreenTimePermissionType.APP_USAGE)
        val map = mapOf(
          "success" to true,
          "status" to "unknown",
          "isApproved" to false
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

        // 如果提供了 endTime，根据当前时间计算阻止时长并启动阻止
        val endTimeVal = args[Argument.endTime]
        val endTimeMs: Long? = when (endTimeVal) {
          is Int -> endTimeVal.toLong()
          is Long -> endTimeVal
          else -> null
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
            null
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
      MethodName.clearAllShields -> {
        val selectionStorageKey = "screenTimeSelection"
        val packages = sharedPreferences.getStringSet("${selectionStorageKey}_packages", emptySet())?.toList() ?: emptyList()
        val ok = ScreenTimeMethod.unblockApps(context, packages, sharedPreferences)
        result.success(ok)
      }
      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCode == REQUEST_SELECT_AND_SAVE) {
      val res = pendingResult
      pendingResult = null
      pendingMethod = null
      if (res != null) {
        val appCount = data?.getIntExtra("appCount", 0) ?: 0
        val categoryCount = data?.getIntExtra("categoryCount", 0) ?: 0
        val map = mapOf(
          "success" to true,
          "appCount" to appCount,
          "categoryCount" to categoryCount,
          "totalSelected" to (appCount + categoryCount)
        )
        res.success(JSONObject(map).toString())
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
      val lookbackTimeMs = args?.get(Argument.lookbackTimeMs) as? Int ?: 10000
      
      // Configure the service with the specified parameters
      AppMonitoringService.configure(
          UsageInterval.valueOf(intervalName.uppercase(Locale.getDefault())),
          lookbackTimeMs.toLong()
      )
      
      // Set up the app change listener
      AppMonitoringService.setAppChangeListener(object : AppMonitoringService.AppChangeListener {
        override fun onAppChanged(appData: Map<String, Any?>) {
          eventSink?.success(appData)
        }
      })
      
      // Start the service if it's not already running
      val appMonitoringService = AppMonitoringService.getInstance(context)
      if (appMonitoringService != null && !appMonitoringService.isRunning) {
        appMonitoringService.startMonitoring()
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
      AppMonitoringService.setAppChangeListener(null)
      
      // Stop the service if no other listeners are active
      if (AppMonitoringService.listenerCount == 0) {
        val appMonitoringService = AppMonitoringService.getInstance(context)
        appMonitoringService?.stopMonitoring()
      }
    } catch (e: Exception) {
      // Log the error but don't throw as we're cleaning up
      android.util.Log.e("ScreenTimePlugin", "Error cleaning up stream: ${e.message}")
    }
  }
}
