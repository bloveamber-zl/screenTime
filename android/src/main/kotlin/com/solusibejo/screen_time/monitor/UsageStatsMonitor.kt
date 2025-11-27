package com.solusibejo.screen_time.monitor

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.solusibejo.screen_time.const.UsageInterval
import java.io.ByteArrayOutputStream

/**
 * Lightweight monitor that polls [UsageStatsManager] for the current foreground application.
 * This replaces the legacy AccessibilityService-based implementation.
 */
class UsageStatsMonitor(
    context: Context,
    private var interval: UsageInterval = UsageInterval.DAILY,
    private var lookbackTimeMs: Long = DEFAULT_LOOKBACK,
    private val listener: (Map<String, Any?>) -> Unit
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var isRunningInternal = false
    private var lastDetectedPackage: String? = null

    private val monitorRunnable = object : Runnable {
        override fun run() {
            try {
                val currentApp = fetchCurrentForegroundApp()
                val packageName = currentApp?.get("packageName") as? String
                if (packageName != null && packageName != lastDetectedPackage) {
                    lastDetectedPackage = packageName
                    listener(currentApp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while polling usage stats: ${e.message}", e)
            } finally {
                if (isRunningInternal) {
                    handler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }
    }

    fun start() {
        if (isRunningInternal) return
        isRunningInternal = true
        UsageStatsMonitorRegistry.increment()
        handler.post(monitorRunnable)
    }

    fun stop() {
        if (!isRunningInternal) return
        handler.removeCallbacks(monitorRunnable)
        isRunningInternal = false
        lastDetectedPackage = null
        UsageStatsMonitorRegistry.decrement()
    }

    fun updateConfig(interval: UsageInterval, lookbackTimeMs: Long) {
        this.interval = interval
        this.lookbackTimeMs = lookbackTimeMs
    }

    private fun fetchCurrentForegroundApp(): Map<String, Any>? {
        val usageStatsManager =
            appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = appContext.packageManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - lookbackTimeMs

        val stats = usageStatsManager.queryUsageStats(interval.type, startTime, endTime)
        var latestUsageStats: UsageStats? = null
        var latestUsedTime = 0L

        if (stats != null) {
            for (usageStat in stats) {
                if (usageStat.lastTimeUsed > latestUsedTime) {
                    latestUsedTime = usageStat.lastTimeUsed
                    latestUsageStats = usageStat
                }
            }
        }

        if (latestUsageStats != null) {
            val packageName = latestUsageStats.packageName
            return try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val icon = appIconAsBase64(packageManager.getApplicationIcon(packageName))
                val data = mutableMapOf<String, Any>(
                    "appName" to packageManager.getApplicationLabel(appInfo).toString(),
                    "packageName" to packageName,
                    "lastTimeUsed" to latestUsageStats.lastTimeUsed,
                    "firstTime" to latestUsageStats.firstTimeStamp,
                    "lastTime" to latestUsageStats.lastTimeStamp,
                    "timeAgo" to (endTime - latestUsageStats.lastTimeUsed)
                )
                data["usageTime"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    latestUsageStats.totalTimeVisible
                } else {
                    latestUsageStats.totalTimeInForeground
                }
                icon?.let { data["appIcon"] = it }
                data
            } catch (e: Exception) {
                mapOf(
                    "packageName" to packageName,
                    "lastTimeUsed" to latestUsageStats.lastTimeUsed
                )
            }
        }

        val fallback = fallbackActivityManagerSnapshot()
        if (fallback != null) {
            return fallback
        }

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var lastForegroundPackage: String? = null
        var lastTimestamp = 0L
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundPackage = event.packageName
                lastTimestamp = event.timeStamp
            }
        }
        return lastForegroundPackage?.let { packageName ->
            mapOf(
                "packageName" to packageName,
                "lastTimeUsed" to lastTimestamp
            )
        }
    }

    private fun fallbackActivityManagerSnapshot(): Map<String, Any>? {
        return try {
            val activityManager =
                appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val foregroundProcess = activityManager.runningAppProcesses
                ?.firstOrNull { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
            foregroundProcess?.let {
                mapOf(
                    "packageName" to it.processName,
                    "lastTimeUsed" to System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun appIconAsBase64(drawable: Drawable): String? {
        return try {
            val bitmap = if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val bmp = Bitmap.createBitmap(
                    drawable.intrinsicWidth,
                    drawable.intrinsicHeight,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "UsageStatsMonitor"
        private const val POLL_INTERVAL_MS = 1000L
        private const val DEFAULT_LOOKBACK = 10_000L
    }
}

object UsageStatsMonitorRegistry {
    @Volatile
    private var activeCount = 0

    @Synchronized
    fun increment() {
        activeCount++
    }

    @Synchronized
    fun decrement() {
        if (activeCount > 0) {
            activeCount--
        }
    }

    fun isRunning(): Boolean = activeCount > 0
}

