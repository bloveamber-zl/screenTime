package com.solusibejo.screen_time

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 透明Activity，用于将被阻止的应用顶到后台
 * 启动后会立即finish，达到将前台应用顶到后台的效果
 */
class TransparentActivity : Activity() {
    private val TAG = "TransparentActivity"
    private val AUTO_FINISH_DELAY_MS = 500L // 0.5秒后自动关闭
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "TransparentActivity 启动")
        
        // 延迟一段时间后自动关闭，确保已经将被阻止的应用顶到后台
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "TransparentActivity 自动关闭")
            finish()
        }, AUTO_FINISH_DELAY_MS)
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "TransparentActivity onResume")
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "TransparentActivity onPause")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TransparentActivity onDestroy")
    }
}

