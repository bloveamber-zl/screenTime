package com.solusibejo.screen_time.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.solusibejo.screen_time.R
import com.solusibejo.screen_time.ScreenTimePlugin
import com.solusibejo.screen_time.const.Argument
import com.solusibejo.screen_time.receiver.ServiceRestartReceiver
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class BlockAppService : Service() {
    companion object {
        private const val TAG = "BlockAppService"
        private const val NOTIFICATION_CHANNEL_ID = "BlockAppServiceChannel"
        private const val NOTIFICATION_ID = 1001
        private const val CHECK_INTERVAL_MS = 100L // 优化：100ms检查频率，提升响应速度
        
        // 防抖相关
        private const val MIN_SHOW_INTERVAL_MS = 200L // 最小显示间隔，防止频繁闪烁
        
        // 服务守护
        private const val SERVICE_GUARD_INTERVAL_MS = 10000L // 每10秒检查一次
        
        const val KEY_BLOCK_END_TIME = "block_end_time"
        const val KEY_BLOCKED_PACKAGES = "blocked_packages"
        const val KEY_IS_BLOCKING = "isBlocking"
        const val DEFAULT_LAYOUT_NAME = "block_overlay"
        
        // Intent actions
        const val ACTION_START_BLOCKING = "${ScreenTimePlugin.PACKAGE_NAME}.START_BLOCKING"

        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            return manager.getRunningServices(Int.MAX_VALUE).any {
                it.service.className == BlockAppService::class.java.name 
            }
        }
    }

    private var executor: ScheduledExecutorService? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var indicatorView: View? = null // 左侧指示器
    private var isOverlayDisplayed = false
    private val blockedPackages = mutableSetOf<String>()
    private var blockEndTime: Long = 0
    private var currentForegroundApp = ""
    private var lastForegroundApp: String? = null
    private var selectedLayoutName: String = DEFAULT_LAYOUT_NAME
    private var selectedLayoutPackage: String? = null
    // UI config
    private var uiTitle: String? = null
    private var uiSubtitle: String? = null
    private var uiTitleColor: String? = null
    private var uiSubtitleColor: String? = null
    private var uiButtonLabel: String? = null
    private var uiButtonColor: String? = null
    private var uiButtonTextColor: String? = null
    private var uiIconName: String? = null
    
    // 防抖逻辑：记录最后一次显示锁屏的时间
    private var lastShowOverlayTime = 0L
    
    // 透明Activity启动标记，避免重复启动
    private var transparentActivityLaunched = false
    
    // 预加载相关
    private val overlayParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        loadBlockedApps()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // 设置定期守护，确保服务持续运行
        setupServiceGuard()
        
        startMonitor()
        
        // 显示左侧指示器
        showLeftIndicator()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: $intent")
        
        // 先创建通知通道（Android O 及以上需要）
        createNotificationChannel()
        
        // 加载封锁状态
        loadBlockedApps()
        
        // 读取自定义屏蔽页配置（来自 ScreenTimeMethod 传入）
        intent?.getStringExtra(Argument.layoutName)?.let { name ->
            if (name.isNotBlank()) {
                selectedLayoutName = name
            }
        }
        intent?.getStringExtra(Argument.layoutPackage)?.let { pkg ->
            if (pkg.isNotBlank()) {
                selectedLayoutPackage = pkg
            }
        }
        uiTitle = intent?.getStringExtra(Argument.shieldTitle)
        uiSubtitle = intent?.getStringExtra(Argument.shieldSubtitle)
        uiTitleColor = intent?.getStringExtra(Argument.shieldSubtitleColor) // 复用键
        uiSubtitleColor = intent?.getStringExtra(Argument.shieldSubtitleColor)
        uiButtonLabel = intent?.getStringExtra(Argument.shieldButtonLabel)
        uiButtonColor = intent?.getStringExtra(Argument.shieldButtonColor)
        uiButtonTextColor = intent?.getStringExtra(Argument.shieldButtonTextColor)
        uiIconName = intent?.getStringExtra(Argument.shieldIconName)
        
        // 获取被锁定的app数量
        val blockedCount = blockedPackages.size
        
        // 构建通知文案
        val notificationTitle = if (blockedCount > 0) {
            getString(R.string.notification_title_with_count, blockedCount)
        } else {
            getString(R.string.notification_title)
        }
        val notificationText = if (blockedCount > 0) {
            "正在封锁 $blockedCount 个应用"
        } else {
            ""
        }
        
        // 构建并启动前台服务
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App Blocking Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Monitors and blocks restricted apps"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    override fun onBind(intent: Intent): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy called - 尝试重启")
        
        executor?.shutdownNow()
        
        hideOverlay()
        removeLeftIndicator()
        
        // 通过广播重启服务
        restartServiceViaBroadcast()
    }
    
    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved - 任务被移除")
        
        // 通过广播重启服务
        restartServiceViaBroadcast()
    }
    
    /**
     * 设置服务守护机制，使用 AlarmManager 定期检查并重启服务
     */
    private fun setupServiceGuard() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager == null) return
        
        val intent = Intent(this, ServiceRestartReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        // 每隔 10 秒检查一次服务是否存活
        val triggerAtMillis = SystemClock.elapsedRealtime() + SERVICE_GUARD_INTERVAL_MS
        val intervalMillis = SERVICE_GUARD_INTERVAL_MS
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                intervalMillis,
                pendingIntent
            )
        } else {
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                intervalMillis,
                pendingIntent
            )
        }
        
        Log.d(TAG, "Service guard 已设置")
    }
    
    /**
     * 通过广播接收器重启服务
     */
    private fun restartServiceViaBroadcast() {
        val broadcastIntent = Intent(this, ServiceRestartReceiver::class.java)
        sendBroadcast(broadcastIntent)
    }
    
    private fun loadBlockedApps() {
        val sharedPreferences = getSharedPreferences(ScreenTimePlugin.PREF_NAME, Context.MODE_PRIVATE)
        blockEndTime = sharedPreferences.getLong(KEY_BLOCK_END_TIME, 0)
        blockedPackages.clear()
        blockedPackages.addAll(
            sharedPreferences.getStringSet(KEY_BLOCKED_PACKAGES, setOf()) ?: setOf()
        )
    }
    
    private fun startMonitor() {
        executor = Executors.newSingleThreadScheduledExecutor()
        executor?.scheduleAtFixedRate({
            try {
                val pkg = getForegroundApp()
                if (pkg == null) return@scheduleAtFixedRate
                
                val now = System.currentTimeMillis()
                val isBlocked = blockedPackages.contains(pkg)
                val isWithinBlockTime = now < blockEndTime
                
                // 检测应用切换
                val appChanged = pkg != lastForegroundApp
                if (appChanged) {
                    lastForegroundApp = pkg
                    Log.d(TAG, "应用切换: $pkg isBlocked:$isBlocked")
                }
                
                // 简化逻辑：需要显示锁屏
                if (isBlocked && isWithinBlockTime && !isOverlayDisplayed) {
                    // 防止频繁显示：检查距离上次显示的时间间隔
                    val timeSinceLastShow = now - lastShowOverlayTime
                    
                    if (timeSinceLastShow >= MIN_SHOW_INTERVAL_MS) {
                        Log.d(TAG, "显示锁屏 - pkg:$pkg")
                        currentForegroundApp = pkg
                        lastShowOverlayTime = now
                        mainHandler.post { showOverlay() }
                    } else {
                        Log.d(TAG, "防抖中 - timeSince:$timeSinceLastShow pkg:$pkg")
                    }
                }
                // 需要隐藏锁屏
                else if (isOverlayDisplayed && (!isBlocked || !isWithinBlockTime)) {
                    mainHandler.post { hideOverlay() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "监控异常", e)
            }
        }, 0, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }
    
    private fun getForegroundApp(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            // 优化：减少查询时间窗口从5秒到1秒，提升查询速度
            val events = usm.queryEvents(time - 1000, time)
            val event = UsageEvents.Event()
            var lastResumed: String? = null
            
            // 优化：只关注最近的 ACTIVITY_RESUMED 事件
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastResumed = event.packageName
                    Log.d(TAG, "getForegroundApp - 前台应用: $lastResumed")
                }
            }
            lastResumed
        } catch (e: Exception) {
            Log.e(TAG, "Error getting foreground app", e)
            null
        }
    }

    private fun showOverlay() {
        if (isOverlayDisplayed) return
        
        try {
            // 检查是否过期
            if (System.currentTimeMillis() >= blockEndTime) {
                return
            }
            
            // 加载布局
            overlayView = loadOverlayView(selectedLayoutPackage, selectedLayoutName)
            
            // 使用预创建的窗口参数
            windowManager?.addView(overlayView, overlayParams)
            isOverlayDisplayed = true
            
            // 应用UI配置（容错：仅在id存在时设置）
            try {
                val resPkg = selectedLayoutPackage ?: packageName
                // 标题
                val titleViewId = overlayView?.resources?.getIdentifier("title_text", "id", resPkg)
                titleViewId?.takeIf { it != 0 }?.let {
                    val tv = overlayView?.findViewById<android.widget.TextView>(it)
                    uiTitle?.let { v -> tv?.text = v }
                    uiTitleColor?.let { c -> runCatching { android.graphics.Color.parseColor(c) }.getOrNull()?.let { color -> tv?.setTextColor(color) } }
                }
                // 副标题
                val subtitleViewId = overlayView?.resources?.getIdentifier("subtitle_text", "id", resPkg)
                subtitleViewId?.takeIf { it != 0 }?.let {
                    val tv = overlayView?.findViewById<android.widget.TextView>(it)
                    uiSubtitle?.let { v -> tv?.text = v }
                    uiSubtitleColor?.let { c -> runCatching { android.graphics.Color.parseColor(c) }.getOrNull()?.let { color -> tv?.setTextColor(color) } }
                }
                // 到期时间显示（time_text）
                val timeViewId = overlayView?.resources?.getIdentifier("time_text", "id", resPkg)
                timeViewId?.takeIf { it != 0 }?.let {
                    val tv = overlayView?.findViewById<android.widget.TextView>(it)
                    val now = System.currentTimeMillis()
                    if (blockEndTime > now) {
                        val hhmm = formatTime(blockEndTime)
                        val appLabel = resolveAppLabelSafe(currentForegroundApp)
                        tv?.text = if (appLabel != null) "在 $hhmm 前不使用$appLabel" else ""
                        tv?.visibility = android.view.View.VISIBLE
                    } else {
                        tv?.visibility = android.view.View.GONE
                    }
                }
                // 按钮
                val buttonViewId = overlayView?.resources?.getIdentifier("confirm_button", "id", resPkg)
                buttonViewId?.takeIf { it != 0 }?.let {
                    val btn = overlayView?.findViewById<android.widget.Button>(it)
                    uiButtonLabel?.let { v -> btn?.text = v }
                    var parsedButtonColor: Int? = null
                    uiButtonColor?.let { c ->
                        parsedButtonColor = runCatching { android.graphics.Color.parseColor(c) }
                            .onFailure { exc -> Log.e(TAG, "Invalid shieldButtonColor=$c", exc) }
                            .getOrNull()
                    }
                    uiButtonTextColor?.let { c -> runCatching { android.graphics.Color.parseColor(c) }.getOrNull()?.let { color -> btn?.setTextColor(color) } }
                    btn?.post {
                        val buttonColor = parsedButtonColor ?: android.graphics.Color.parseColor("#FF4D4F")
                        val radiusPx = (btn.height / 2f).takeIf { it > 0 } ?: dpToPxFloat(16f)
                        val drawable = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                            cornerRadius = radiusPx
                            setColor(buttonColor)
                        }
                        btn.backgroundTintList = null
                        btn.stateListAnimator = null
                        btn.background = drawable
                    }
                    // 点击按钮显示桌面
                    btn?.setOnClickListener {
                        openOwnAppInBackground()
                    }
                }
                // 图标
                val iconViewId = overlayView?.resources?.getIdentifier("shield_icon", "id", resPkg)
                iconViewId?.takeIf { it != 0 }?.let {
                    val iv = overlayView?.findViewById<android.widget.ImageView>(it)
                    uiIconName?.let { name ->
                        val resId = try { createPackageContext(resPkg, 0).resources.getIdentifier(name, "drawable", resPkg) } catch (_: Exception) { 0 }
                        if (resId != 0) {
                            val ctx = if (selectedLayoutPackage != null && selectedLayoutPackage != packageName) createPackageContext(resPkg, Context.CONTEXT_IGNORE_SECURITY) else this
                            val drawable = androidx.core.content.ContextCompat.getDrawable(ctx, resId)
                            iv?.setImageDrawable(drawable)
                        }
                    }
                }
            } catch (_: Exception) { /* ignore styling errors */ }
            
            // 仅显示遮罩，不再返回桌面
                    } catch (e: Exception) {
            // 发生异常时确保状态正确
            isOverlayDisplayed = false
            overlayView = null
            transparentActivityLaunched = false
            Log.e(TAG, "Error showing overlay", e)
        }
    }
    
    /**
     * 启动到桌面，将被阻止的应用顶到后台
     */
    private fun openOwnAppInBackground() {
        try {
            // 启动到桌面，这样被阻止的应用会真正进入后台
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            startActivity(homeIntent)
            Log.d(TAG, "已返回桌面，将被阻止的应用顶到后台")
        } catch (e: Exception) {
            Log.e(TAG, "返回桌面失败", e)
        }
    }

    private fun hideOverlay() {
        // 优化：先更新状态，避免重复调用
        if (!isOverlayDisplayed) return
        isOverlayDisplayed = false
        
        try {
            if (overlayView != null) {
                // 优化：使用 removeViewImmediate 立即移除视图
                windowManager?.removeViewImmediate(overlayView)
                overlayView = null
            }
        } catch (e: Exception) {
            // 静默处理异常
        } finally {
            currentForegroundApp = ""
            // 重置透明Activity启动标记，为下次锁屏做准备
            transparentActivityLaunched = false
        }
    }
    
    /**
     * 显示左侧白色半透明竖线指示器
     */
    private fun showLeftIndicator() {
        try {
            // 先移除旧的指示器（如果存在）
            removeLeftIndicator()
            
            // 创建一个新的View作为指示器
            indicatorView = View(this)
            
            // 创建圆角矩形背景
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0x80FFFFFF.toInt()) // 白色半透明 (50%透明度)
                cornerRadius = dpToPxFloat(5f) // 圆角半径5dp
            }
            indicatorView?.background = drawable
            
            // 设置窗口参数
            val params = WindowManager.LayoutParams(
                dpToPx(5f), // 宽度5dp
                dpToPx(35f), // 高度35dp
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                // 设置位置：左侧居中，距离屏幕左边缘5dp
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                x = dpToPx(5f)
                y = 10
            }
            
            // 添加到窗口
            windowManager?.addView(indicatorView, params)
            } catch (e: Exception) {
            Log.e(TAG, "显示左侧指示器失败", e)
        }
    }
    
    /**
     * 移除左侧指示器
     */
    private fun removeLeftIndicator() {
        try {
            if (indicatorView != null) {
                windowManager?.removeView(indicatorView)
                indicatorView = null
            }
        } catch (e: Exception) {
            // 静默处理异常
        }
    }
    
    /**
     * dp转px (返回Int)
     */
    private fun dpToPx(dp: Float): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
    
    /**
     * dp转px (返回Float，用于cornerRadius等需要Float的场景)
     */
    private fun dpToPxFloat(dp: Float): Float {
        val density = resources.displayMetrics.density
        return dp * density
    }
    
    private fun formatTime(epochMillis: Long): String {
        return try {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = epochMillis
            val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val m = cal.get(java.util.Calendar.MINUTE)
            val hh = if (h < 10) "0$h" else "$h"
            val mm = if (m < 10) "0$m" else "$m"
            "$hh:$mm"
        } catch (_: Exception) {
            ""
        }
    }
    
    private fun resolveAppLabelSafe(pkg: String?): String? {
        if (pkg.isNullOrBlank()) return null
        return try {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info)?.toString()
        } catch (_: Exception) { null }
    }

    /**
     * Loads an overlay view from a specified package or creates a default one programmatically
     */
    private fun loadOverlayView(packageName: String?, layoutName: String): View {
        try {
            // First try to load from our own package (the plugin)
            try {
                val layoutId = resources.getIdentifier(layoutName, "layout", this.packageName)
                if (layoutId != 0) {
                    Log.d(TAG, "Loading layout from plugin package: ${this.packageName}, layout: $layoutName")
                    return LayoutInflater.from(this).inflate(layoutId, null)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not load layout from plugin package: ${e.message}")
            }
            
            // Then try to load from the specified package
            if (packageName != null && packageName != this.packageName) {
                try {
                    val flags = Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
                    val packageContext = createPackageContext(packageName, flags)
                    val layoutId = packageContext.resources.getIdentifier(layoutName, "layout", packageName)
                    
                    if (layoutId != 0) {
                        Log.d(TAG, "Loading layout from host package: $packageName, layout: $layoutName")
                        val inflater = LayoutInflater.from(packageContext)
                        return inflater.inflate(layoutId, null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error accessing package context: $packageName", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadOverlayView", e)
        }
        
        // Fallback to creating a view programmatically
        Log.d(TAG, "Using fallback programmatic layout")
        return createFallbackOverlayView()
    }
    
    /**
     * Creates a simple programmatic overlay view as a fallback
     */
    private fun createFallbackOverlayView(): View {
        val frameLayout = android.widget.FrameLayout(this)
        frameLayout.setBackgroundColor(android.graphics.Color.BLACK)
        
        val textView = android.widget.TextView(this)
        textView.text = if (blockedPackages.size > 0) {
            getString(R.string.notification_title_with_count, blockedPackages.size)
        } else {
            getString(R.string.notification_title)
        }
        textView.setTextColor(android.graphics.Color.WHITE)
        textView.textSize = 24f
        
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = android.view.Gravity.CENTER
        frameLayout.addView(textView, params)
        
        // Add a button to close the overlay
        val closeButton = android.widget.Button(this)
        closeButton.text = "关闭"
        closeButton.setOnClickListener {
            hideOverlay()
        }
        
        val buttonParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        buttonParams.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
        buttonParams.bottomMargin = 50
        frameLayout.addView(closeButton, buttonParams)
        
        return frameLayout
    }
}
