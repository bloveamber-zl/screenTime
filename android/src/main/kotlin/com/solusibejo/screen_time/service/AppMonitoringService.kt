package com.solusibejo.screen_time.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.solusibejo.screen_time.R
import com.solusibejo.screen_time.ScreenTimePlugin
import com.solusibejo.screen_time.const.UsageInterval
import io.flutter.Log as FlutterLog
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * AccessibilityService for monitoring current foreground app and blocking apps
 * This service provides real-time information about which app is currently in use
 * and can block specific apps by showing an overlay
 */
class AppMonitoringService : AccessibilityService() {
    private val TAG = "AppMonitoringService"
    private val handler = Handler(Looper.getMainLooper())
    private var lastDetectedPackage: String? = null
    private var currentInterval = UsageInterval.DAILY
    private var lookbackTimeMs = 5 * 1000L // Default: 5 seconds lookback
    
    // Blocking related
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayDisplayed = false
    private val blockedPackages = mutableSetOf<String>()
    private var blockEndTime: Long = 0
    
    private val overlayParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )
    
    private val checkRunnable = object : Runnable {
        override fun run() {
            checkCurrentApp()
            handler.postDelayed(this, 1000) // Check every second
        }
    }
    
    private var blockingCheckRunnable: Runnable? = null
    
    // Callback interface for app monitoring
    interface AppMonitorCallback {
        fun onAppChanged(appData: Map<String, Any>)
    }
    
    // Listener interface for streaming app changes
    interface AppChangeListener {
        fun onAppChanged(appData: Map<String, Any?>)
    }

    companion object {
        private var instance: AppMonitoringService? = null
        private var callback: AppMonitorCallback? = null
        private var appChangeListener: AppChangeListener? = null
        var listenerCount = 0

        fun getInstance(context: Context? = null): AppMonitoringService? {
            return instance
        }
        
        fun setCallback(callback: AppMonitorCallback) {
            this.callback = callback
        }
        
        fun setAppChangeListener(listener: AppChangeListener?) {
            if (listener != null) {
                listenerCount++
            } else {
                listenerCount = maxOf(0, listenerCount - 1)
            }
            this.appChangeListener = listener
        }
        
        /**
         * Configure the app monitoring service with specified parameters
         * @param interval The interval to use for usage stats queries
         * @param lookbackTimeMs Time in milliseconds to look back for app usage data
         */
        fun configure(interval: UsageInterval, lookbackTimeMs: Long) {
            getInstance()?.apply {
                this.currentInterval = interval
                this.lookbackTimeMs = lookbackTimeMs
            }
        }
        
        /**
         * Set the interval for usage stats queries
         * @param interval The interval to use (DAILY, WEEKLY, MONTHLY, YEARLY, BEST)
         */
        fun setInterval(interval: UsageInterval) {
            getInstance()?.currentInterval = interval
        }
        
        /**
         * Set how far back in time to look for app usage data
         * @param timeMs Time in milliseconds
         */
        fun setLookbackTime(timeMs: Long) {
            getInstance()?.lookbackTimeMs = timeMs
        }

        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (AppMonitoringService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
        
        /**
         * Update blocking state (called from ScreenTimeMethod)
         */
        fun updateBlockingState(context: Context, packages: Set<String>, endTime: Long) {
            getInstance()?.apply {
                blockedPackages.clear()
                blockedPackages.addAll(packages)
                blockEndTime = endTime
                
                if (isBlockingActive()) {
                    startBlockingCheck()
                } else {
                    hideOverlay()
                }
            }
        }
        
        /**
         * Clear blocking state (called from ScreenTimeMethod)
         */
        fun clearBlockingState(context: Context) {
            getInstance()?.apply {
                hideOverlay()
                clearBlockingStateInternal()
            }
        }
    }

    private var isMonitoring = false
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        FlutterLog.d(TAG, "Service connected")
        
        // Load blocking state from SharedPreferences
        loadBlockingState()
        
        if (listenerCount > 0) {
            startMonitoring()
        }
        
        // Start blocking check if needed
        if (isBlockingActive()) {
            startBlockingCheck()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (packageName != null) {
                checkAndBlockApp(packageName)
            }
            checkCurrentApp()
        }
    }

    override fun onInterrupt() {
        FlutterLog.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        hideOverlay()
        windowManager = null
        overlayView = null
        instance = null
        isMonitoring = false
        FlutterLog.d(TAG, "Service destroyed")
    }

    fun startMonitoring() {
        if (!isMonitoring) {
            handler.post(checkRunnable)
            isMonitoring = true
            FlutterLog.d(TAG, "Monitoring started")
        }
    }

    fun stopMonitoring() {
        if (isMonitoring) {
            handler.removeCallbacks(checkRunnable)
            isMonitoring = false
            FlutterLog.d(TAG, "Monitoring stopped")
        }
    }

    val isRunning: Boolean
        get() = isMonitoring

    private fun checkCurrentApp() {
        val appData = getCurrentForegroundAppData()
        
        if (appData == null) {
            FlutterLog.d(TAG, "Could not determine foreground app")
            return
        }
        
        val packageName = appData["packageName"] as? String ?: return
        
        // Only notify if the package has changed
        if (packageName != lastDetectedPackage) {
            FlutterLog.d(TAG, "App changed: $packageName")
            lastDetectedPackage = packageName
            
            // Notify through callback with detailed app data
            callback?.onAppChanged(appData)
            
            // Notify through stream listener for real-time updates
            appChangeListener?.onAppChanged(appData)
        }
    }
    
    private fun getCurrentForegroundAppData(): Map<String, Any>? {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val packageManager = applicationContext.packageManager
            val time = System.currentTimeMillis()
            
            // Get usage stats using the configured interval and lookback time
            val stats = usageStatsManager.queryUsageStats(
                currentInterval.type,
                time - lookbackTimeMs,
                time
            )
            
            // Find the most recent app
            if (stats != null) {
                var latestUsageStats: android.app.usage.UsageStats? = null
                var latestUsedTime = 0L
                
                for (usageStats in stats) {
                    if (usageStats.lastTimeUsed > latestUsedTime) {
                        latestUsedTime = usageStats.lastTimeUsed
                        latestUsageStats = usageStats
                    }
                }
                
                if (latestUsageStats != null) {
                    try {
                        val packageName = latestUsageStats.packageName
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        val appIcon = appIconAsBase64(packageManager, packageName)
                        
                        val data = mutableMapOf<String, Any>(
                            // The app name
                            "appName" to packageManager.getApplicationLabel(appInfo).toString(),
                            // The package name of the app
                            "packageName" to packageName,
                            // The last recorded timestamp when the app was used
                            "lastTimeUsed" to latestUsageStats.lastTimeUsed,
                            // The first recorded timestamp when the app was used
                            "firstTime" to latestUsageStats.firstTimeStamp,
                            // The last recorded timestamp when the app was used
                            "lastTime" to latestUsageStats.lastTimeStamp,
                            // How long ago (in milliseconds) the app was last used
                            "timeAgo" to (time - latestUsageStats.lastTimeUsed)
                        )
                        
                        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q){
                            // The total time (in milliseconds) the app was visible on screen
                            data["usageTime"] = latestUsageStats.totalTimeVisible
                        } else {
                            // The total time (in milliseconds) the app was in the foreground
                            data["usageTime"] = latestUsageStats.totalTimeInForeground
                        }
                        
                        if(appIcon != null){
                            data["appIcon"] = appIcon
                        }
                        
                        return data
                    } catch (e: PackageManager.NameNotFoundException) {
                        // App might be a system app or uninstalled
                        return mapOf(
                            "packageName" to latestUsageStats.packageName,
                            "lastTimeUsed" to latestUsageStats.lastTimeUsed
                        )
                    }
                }
            }
            
            // Fallback to ActivityManager if UsageStatsManager doesn't work
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processName = activityManager.runningAppProcesses
                .filter { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
                .firstOrNull()?.processName
                
            if (processName != null) {
                try {
                    val appInfo = packageManager.getApplicationInfo(processName, 0)
                    return mapOf(
                        "appName" to packageManager.getApplicationLabel(appInfo).toString(),
                        "packageName" to processName,
                        "lastTimeUsed" to time
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    return mapOf(
                        "packageName" to processName,
                        "lastTimeUsed" to time
                    )
                }
            }
            
            return null
        } catch (e: Exception) {
            FlutterLog.e(TAG, "Error getting foreground app: ${e.message}")
            return null
        }
    }

    fun getAppName(packageName: String): String {
        val packageManager = applicationContext.packageManager
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
    
    private fun appIconAsBase64(
        packageManager: PackageManager,
        packageName: String,
    ): String? {
        return try {
            val drawable: Drawable = packageManager.getApplicationIcon(packageName)
            val bitmap = drawableToBitmap(drawable)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)  // Convert to Base64
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth, drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
    
    // MARK: - Blocking functionality
    
    /**
     * Load blocking state from SharedPreferences
     */
    private fun loadBlockingState() {
        val sharedPreferences = getSharedPreferences(ScreenTimePlugin.PREF_NAME, Context.MODE_PRIVATE)
        val isBlocking = sharedPreferences.getBoolean(BlockAppService.KEY_IS_BLOCKING, false)
        val blockEndTimeValue = sharedPreferences.getLong(BlockAppService.KEY_BLOCK_END_TIME, 0)
        val blockedPackagesSet = sharedPreferences.getStringSet(BlockAppService.KEY_BLOCKED_PACKAGES, setOf()) ?: setOf()
        
        if (isBlocking && blockEndTimeValue > System.currentTimeMillis() && blockedPackagesSet.isNotEmpty()) {
            blockedPackages.clear()
            blockedPackages.addAll(blockedPackagesSet)
            blockEndTime = blockEndTimeValue
            FlutterLog.d(TAG, "Loaded blocking state: ${blockedPackages.size} apps until $blockEndTime")
        } else {
            blockedPackages.clear()
            blockEndTime = 0
        }
    }
    
    /**
     * Check if blocking is currently active
     */
    private fun isBlockingActive(): Boolean {
        return blockedPackages.isNotEmpty() && 
               blockEndTime > System.currentTimeMillis()
    }
    
    /**
     * Start periodic blocking check
     */
    fun startBlockingCheck() {
        // Cancel any existing blocking check to avoid duplicates
        blockingCheckRunnable?.let { handler.removeCallbacks(it) }
        
        blockingCheckRunnable = object : Runnable {
            override fun run() {
                if (isBlockingActive()) {
                    // Still in blocking period, check current app
                    val currentPackage = getCurrentPackageName()
                    if (currentPackage != null) {
                        checkAndBlockApp(currentPackage)
                    }
                    // Continue checking every second
                    handler.postDelayed(this, 1000)
                } else {
                    // Blocking period ended, hide overlay and clear state
                    FlutterLog.d(TAG, "Blocking period ended, clearing restrictions via AccessibilityService")
                    hideOverlay()
                    clearBlockingStateInternal()
                    // Cancel WorkManager task since we've already unblocked
                    cancelWorkManagerUnblock()
                    blockingCheckRunnable = null
                    // Stop the periodic check
                }
            }
        }
        
        handler.post(blockingCheckRunnable!!)
    }
    
    /**
     * Get current package name from event or usage stats
     */
    private fun getCurrentPackageName(): String? {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 30000 // 30 seconds
            
            val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
            var lastForegroundEvent: android.app.usage.UsageEvents.Event? = null
            
            while (usageEvents.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                usageEvents.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastForegroundEvent = event
                }
            }
            
            lastForegroundEvent?.packageName
        } catch (e: Exception) {
            FlutterLog.e(TAG, "Error getting current package name: ${e.message}")
            null
        }
    }
    
    /**
     * Check if app should be blocked and show/hide overlay accordingly
     */
    private fun checkAndBlockApp(packageName: String) {
        // First check if blocking period has ended
        if (!isBlockingActive()) {
            FlutterLog.d(TAG, "Blocking period ended, hiding overlay")
            hideOverlay()
            clearBlockingStateInternal()
            return
        }
        
        if (!Settings.canDrawOverlays(this)) {
            FlutterLog.e(TAG, "Draw overlay permission not granted")
            return
        }
        
        val isBlocked = blockedPackages.contains(packageName)
        val isDeviceLocked = isDeviceLocked()
        
        if (isBlocked && !isDeviceLocked) {
            showOverlay()
        } else {
            hideOverlay()
        }
    }
    
    /**
     * Check if device is locked
     */
    private fun isDeviceLocked(): Boolean {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isKeyguardLocked
    }
    
    /**
     * Show blocking overlay
     */
    private fun showOverlay() {
        if (isOverlayDisplayed && overlayView?.windowToken != null) {
            return
        }
        
        try {
            if (overlayView == null) {
                overlayView = loadOverlayView()
            }
            
            if (overlayView != null && windowManager != null) {
                windowManager?.addView(overlayView, overlayParams)
                isOverlayDisplayed = true
                FlutterLog.d(TAG, "Overlay displayed")
            }
        } catch (e: Exception) {
            FlutterLog.e(TAG, "Error showing overlay: ${e.message}")
        }
    }
    
    /**
     * Hide blocking overlay
     */
    private fun hideOverlay() {
        if (!isOverlayDisplayed || overlayView == null) {
            return
        }
        
        try {
            if (windowManager != null && overlayView?.windowToken != null) {
                windowManager?.removeView(overlayView)
                isOverlayDisplayed = false
                FlutterLog.d(TAG, "Overlay hidden")
            }
        } catch (e: Exception) {
            FlutterLog.e(TAG, "Error hiding overlay: ${e.message}")
        }
    }
    
    /**
     * Load overlay view from layout or create fallback
     */
    private fun loadOverlayView(): View? {
        try {
            // Try to load from plugin package
            val layoutId = resources.getIdentifier("block_overlay", "layout", packageName)
            if (layoutId != 0) {
                return LayoutInflater.from(this).inflate(layoutId, null)
            }
        } catch (e: Exception) {
            FlutterLog.d(TAG, "Could not load layout from plugin package: ${e.message}")
        }
        
        // Fallback to programmatic view
        return createFallbackOverlayView()
    }
    
    /**
     * Create fallback overlay view programmatically
     */
    private fun createFallbackOverlayView(): View {
        val frameLayout = FrameLayout(this)
        frameLayout.setBackgroundColor(android.graphics.Color.BLACK)
        
        val textView = TextView(this)
        textView.text = getString(R.string.notification_title)
        textView.setTextColor(android.graphics.Color.WHITE)
        textView.textSize = 24f
        
        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        textParams.gravity = Gravity.CENTER
        frameLayout.addView(textView, textParams)
        
        return frameLayout
    }
    
    /**
     * Clear blocking state
     */
    private fun clearBlockingStateInternal() {
        blockedPackages.clear()
        blockEndTime = 0
        val sharedPreferences = getSharedPreferences(ScreenTimePlugin.PREF_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().apply {
            putBoolean(BlockAppService.KEY_IS_BLOCKING, false)
            putStringSet(BlockAppService.KEY_BLOCKED_PACKAGES, setOf())
            putLong(BlockAppService.KEY_BLOCK_END_TIME, 0)
            apply()
        }
    }
    
    /**
     * Cancel WorkManager unblock task (helper method)
     */
    private fun cancelWorkManagerUnblock() {
        try {
            val workManager = androidx.work.WorkManager.getInstance(this)
            workManager.cancelUniqueWork(com.solusibejo.screen_time.worker.UnblockWorker.WORK_NAME)
            FlutterLog.d(TAG, "Cancelled WorkManager unblock task")
        } catch (e: Exception) {
            FlutterLog.e(TAG, "Error cancelling WorkManager unblock", e)
        }
    }
}
