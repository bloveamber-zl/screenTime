package com.solusibejo.screen_time.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.solusibejo.screen_time.ScreenTimePlugin
import com.solusibejo.screen_time.service.BlockAppService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker responsible for unblocking apps after the block duration has expired
 * This serves as a backup mechanism to ensure apps are unblocked even if the foreground service fails
 */
class UnblockWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "UnblockWorker"
        const val WORK_NAME = "unblock_apps_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "UnblockWorker executing - checking if blocking period has ended")
            
            // Check if we should still be unblocking
            val sharedPreferences = applicationContext.getSharedPreferences(
                ScreenTimePlugin.PREF_NAME,
                Context.MODE_PRIVATE
            )
            
            val isBlocking = sharedPreferences.getBoolean(BlockAppService.KEY_IS_BLOCKING, false)
            val blockEndTime = sharedPreferences.getLong(BlockAppService.KEY_BLOCK_END_TIME, 0)
            val currentTime = System.currentTimeMillis()
            
            Log.d(TAG, "Current time: $currentTime, Block end time: $blockEndTime, Is blocking: $isBlocking")
            
            // If we're still blocking and the end time has passed, unblock
            if (isBlocking && blockEndTime > 0 && currentTime >= blockEndTime) {
                Log.d(TAG, "Block time has ended, unblocking apps via WorkManager")
                
                // Update shared preferences
                sharedPreferences.edit().apply {
                    putBoolean(BlockAppService.KEY_IS_BLOCKING, false)
                    putStringSet(BlockAppService.KEY_BLOCKED_PACKAGES, setOf())
                    putLong(BlockAppService.KEY_BLOCK_END_TIME, 0)
                    apply()
                }
                
                // Stop BlockAppService if it's running (if using foreground service)
                if (BlockAppService.isServiceRunning(applicationContext)) {
                    Log.d(TAG, "Stopping BlockAppService")
                    applicationContext.stopService(Intent(applicationContext, BlockAppService::class.java))
                }
                
                Log.d(TAG, "Successfully unblocked apps via WorkManager")
            } else if (!isBlocking) {
                Log.d(TAG, "Already unblocked, no action needed")
            } else if (blockEndTime > currentTime) {
                val remaining = blockEndTime - currentTime
                Log.d(TAG, "Block time not yet ended, remaining: ${remaining}ms (${remaining / 1000}s)")
            } else {
                Log.d(TAG, "Invalid blocking state, clearing anyway")
                // Clear invalid state
                sharedPreferences.edit().apply {
                    putBoolean(BlockAppService.KEY_IS_BLOCKING, false)
                    putStringSet(BlockAppService.KEY_BLOCKED_PACKAGES, setOf())
                    putLong(BlockAppService.KEY_BLOCK_END_TIME, 0)
                    apply()
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in UnblockWorker", e)
            Result.failure()
        }
    }
}
