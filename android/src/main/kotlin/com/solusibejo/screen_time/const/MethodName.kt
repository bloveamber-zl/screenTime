package com.solusibejo.screen_time.const

object MethodName {
    const val requestPermission = "requestPermission"
    const val permissionStatus = "permissionStatus"
    const val installedApps = "installedApps"
    const val appUsageData = "appUsageData"
    const val blockApps = "blockApps"
    const val scheduleBlock = "scheduleBlock"
    const val cancelScheduledBlock = "cancelScheduledBlock"
    const val getActiveSchedules = "getActiveSchedules"
    const val isOnBlockingApps = "isOnBlockingApps"
    const val unblockApps = "unblockApps"
    const val pauseBlockApps = "pauseBlockApps"
    const val isBlockingPaused = "isBlockingPaused"
    const val monitoringAppUsage = "monitoringAppUsage"
    const val configureAppMonitoringService = "configureAppMonitoringService"

    // iOS parity methods
    const val pickApps = "pickApps"
    const val selectAndSaveApps = "selectAndSaveApps"
    const val applySavedSelection = "applySavedSelection"
    const val readSelectionCounts = "readSelectionCounts"
    const val getFamilyControlsAuthorizationStatus = "getFamilyControlsAuthorizationStatus"
    const val requestFamilyControlsAuthorization = "requestFamilyControlsAuthorization"
    const val clearAllShields = "clearAllShields"
}