package com.solusibejo.screen_time.receiver

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.solusibejo.screen_time.service.BlockAppService

/**
 * 服务重启广播接收器
 * 用于在服务被杀死后自动重启
 */
class ServiceRestartReceiver : BroadcastReceiver() {
    private val TAG = "ServiceRestartReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到重启服务的广播")
        
        // 检查服务是否正在运行
        if (!isServiceRunning(context, BlockAppService::class.java)) {
            Log.d(TAG, "服务未运行，准备重启")
            
            // 检查是否在封锁时间段内
            val sharedPreferences = context.getSharedPreferences(
                "screen_time",
                Context.MODE_PRIVATE
            )
            val isBlocking = sharedPreferences.getBoolean(BlockAppService.KEY_IS_BLOCKING, false)
            val blockEndTime = sharedPreferences.getLong(BlockAppService.KEY_BLOCK_END_TIME, 0)
            val blockedPackages = sharedPreferences.getStringSet(
                BlockAppService.KEY_BLOCKED_PACKAGES,
                setOf()
            )
            
            if (isBlocking && System.currentTimeMillis() < blockEndTime && !blockedPackages.isNullOrEmpty()) {
                val serviceIntent = Intent(context, BlockAppService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "服务已重启")
            } else {
                Log.d(TAG, "不在封锁时间段内，不重启服务")
            }
        } else {
            Log.d(TAG, "服务正在运行，无需重启")
        }
    }
    
    /**
     * 检查服务是否正在运行
     */
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (manager == null) return false
        
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}

