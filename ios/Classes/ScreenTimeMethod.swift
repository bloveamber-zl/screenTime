//
//  ScreenTimeMethod.swift
//  Pods
//
//  Created by Chandra Abdul Fattah on 29/04/25.
//

import Foundation
import UserNotifications
import UIKit
import SwiftUI
import FamilyControls
import ManagedSettings
import DeviceActivity

class ScreenTimeMethod {

    // MARK: - Selection Persistence Helpers
    private static func getDefaults(appGroupIdentifier: String?) -> UserDefaults {
        if let gid = appGroupIdentifier, !gid.isEmpty, let s = UserDefaults(suiteName: gid) { return s }
        return UserDefaults.standard
    }

    @available(iOS 15.0, *)
    @discardableResult
    private static func saveSelection(_ selection: FamilyActivitySelection, to defaults: UserDefaults, key: String) -> Bool {
        if let data = try? JSONEncoder().encode(selection) {
            defaults.set(data, forKey: key)
            defaults.synchronize()
            return true
        }
        return false
    }

    @available(iOS 15.0, *)
    private static func loadSelection(from defaults: UserDefaults, key: String) -> FamilyActivitySelection? {
        guard let data = defaults.data(forKey: key),
              let selection = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data) else {
            return nil
        }
        return selection
    }

    // MARK: - Authorization Status
    @available(iOS 16.0, *)
    static func getFamilyControlsAuthorizationStatus() -> [String: Any] {
        let center = AuthorizationCenter.shared
        let status = center.authorizationStatus
        
        var statusString: String
        switch status {
        case .approved:
            statusString = "approved"
        case .denied:
            statusString = "denied"
        case .notDetermined:
            statusString = "notDetermined"
        @unknown default:
            statusString = "unknown"
        }
        
        return [
            "success": true,
            "status": statusString,
            "isApproved": status == .approved
        ]
    }

    // MARK: - Request Authorization
    @available(iOS 16.0, *)
    @MainActor
    static func requestFamilyControlsAuthorization() async -> [String: Any] {
        do {
            let center = AuthorizationCenter.shared
            try await center.requestAuthorization(for: .individual)
            
            // 请求后再次检查状态
            let status = center.authorizationStatus
            let isApproved = status == .approved
            
            var statusString: String
            switch status {
            case .approved:
                statusString = "approved"
            case .denied:
                statusString = "denied"
            case .notDetermined:
                statusString = "notDetermined"
            @unknown default:
                statusString = "unknown"
            }
            
            return [
                "success": true,
                "status": statusString,
                "isApproved": isApproved
            ]
        } catch {
            return [
                "success": false,
                "error": String(describing: error),
                "isApproved": false
            ]
        }
    }

    // MARK: - Selection Count Helpers
    @available(iOS 16.0, *)
    private static func writeSelectionCounts(
        _ selection: FamilyActivitySelection,
        appGroupIdentifier: String?,
        selectionStorageKey: String
    ) {
        let defaults = getDefaults(appGroupIdentifier: appGroupIdentifier)
        let appCountKey = "\(selectionStorageKey)_app_count"
        let categoryCountKey = "\(selectionStorageKey)_category_count"
        let totalKey = "\(selectionStorageKey)_total_selected"

        let appCount = selection.applicationTokens.count
        let categoryCount = selection.categoryTokens.count
        let totalSelected = appCount + categoryCount

        defaults.set(appCount, forKey: appCountKey)
        defaults.set(categoryCount, forKey: categoryCountKey)
        defaults.set(totalSelected, forKey: totalKey)
        defaults.synchronize()
    }

    static func readSelectionCounts(
        appGroupIdentifier: String? = nil,
        selectionStorageKey: String = "screenTimeSelection"
    ) -> [String: Any] {
        let defaults = getDefaults(appGroupIdentifier: appGroupIdentifier)
        let appCountKey = "\(selectionStorageKey)_app_count"
        let categoryCountKey = "\(selectionStorageKey)_category_count"
        let totalKey = "\(selectionStorageKey)_total_selected"

        let appCount = defaults.integer(forKey: appCountKey)
        let categoryCount = defaults.integer(forKey: categoryCountKey)
        let totalSelected = defaults.object(forKey: totalKey) != nil ? defaults.integer(forKey: totalKey) : (appCount + categoryCount)

        return [
            "success": true,
            "appCount": appCount,
            "categoryCount": categoryCount,
            "totalSelected": totalSelected
        ]
    }

    // MARK: - Extension Debug Info
    /// 获取 DeviceActivityMonitorExtension 的调试信息
    /// 用于检查 Extension 是否正常运行
    static func getExtensionDebugInfo(
        appGroupIdentifier: String? = nil
    ) -> [String: Any] {
        let defaults = getDefaults(appGroupIdentifier: appGroupIdentifier)
        var result: [String: Any] = [:]
        
        // Extension 初始化信息
        if let startDate = defaults.object(forKey: "lastIntervalDidStart") as? Date {
            result["lastIntervalDidStart"] = Int64(startDate.timeIntervalSince1970 * 1000)
        }
        
        if let activityName = defaults.string(forKey: "lastActivityName") {
            result["lastActivityName"] = activityName
        }
        
        // Extension 结束信息
        if let endDate = defaults.object(forKey: "lastIntervalDidEnd") as? Date {
            result["lastIntervalDidEnd"] = Int64(endDate.timeIntervalSince1970 * 1000)
        }
        
        if let endActivityName = defaults.string(forKey: "lastEndActivityName") {
            result["lastEndActivityName"] = endActivityName
        }
        
        // 当前 Schedule 信息
        if let scheduleId = defaults.string(forKey: "current_schedule_id") {
            result["currentScheduleId"] = scheduleId
        }
        
        if let endTime = defaults.object(forKey: "current_schedule_end_time") as? TimeInterval, endTime > 0 {
            result["currentScheduleEndTime"] = Int64(endTime * 1000)
            let endDate = Date(timeIntervalSince1970: endTime)
            let now = Date()
            result["isScheduleActive"] = now < endDate
            result["timeRemaining"] = max(0, Int64((endDate.timeIntervalSince(now)) * 1000))
        }
        
        // 解锁信息
        if let unlockSuccess = defaults.object(forKey: "lastUnlockSuccess") as? Bool {
            result["lastUnlockSuccess"] = unlockSuccess
        }
        
        if let unlockTime = defaults.object(forKey: "lastUnlockTime") as? Date {
            result["lastUnlockTime"] = Int64(unlockTime.timeIntervalSince1970 * 1000)
        }
        
        // App Group 信息
        if let appGroupId = appGroupIdentifier, !appGroupId.isEmpty {
            result["appGroupIdentifier"] = appGroupId
            // 检查 App Group 是否可访问
            if UserDefaults(suiteName: appGroupId) != nil {
                result["appGroupAccessible"] = true
            } else {
                result["appGroupAccessible"] = false
            }
        } else {
            result["appGroupIdentifier"] = NSNull()
            result["appGroupAccessible"] = false
        }
        
        // 诊断信息：检查主 App 是否创建了 Schedule
        let hasScheduleInfo = result["currentScheduleId"] != nil || result["currentScheduleEndTime"] != nil
        result["scheduleCreated"] = hasScheduleInfo
        
        // 诊断信息：检查 Extension 是否被调用过
        let hasExtensionData = result["lastIntervalDidStart"] != nil || 
                               result["lastIntervalDidEnd"] != nil
        result["extensionHasBeenCalled"] = hasExtensionData
        
        // 诊断信息：列出所有相关的 UserDefaults keys（用于调试）
        let allKeys = defaults.dictionaryRepresentation().keys
        let extensionKeys = allKeys.filter { key in
            key.contains("lastInterval") || 
            key.contains("lastActivity") || 
            key.contains("current_schedule") || 
            key.contains("lastUnlock") ||
            key.contains("shield_config") ||
            key.contains("schedule")
        }
        if !extensionKeys.isEmpty {
            result["foundExtensionKeys"] = Array(extensionKeys.sorted())
        }
        
        // 检查是否有 Schedule 创建错误
        if let scheduleError = defaults.string(forKey: "last_schedule_error") {
            result["lastScheduleError"] = scheduleError
        }
        
        // 检查 shield_config_end_time（主 App 保存的结束时间）
        if let shieldEndTime = defaults.object(forKey: "shield_config_end_time") as? TimeInterval, shieldEndTime > 0 {
            result["shieldConfigEndTime"] = Int64(shieldEndTime * 1000)
            let endDate = Date(timeIntervalSince1970: shieldEndTime)
            let now = Date()
            result["shieldConfigTimeRemaining"] = max(0, Int64((endDate.timeIntervalSince(now)) * 1000))
        }
        
        // 诊断提示信息
        var diagnosticMessages: [String] = []
        if !hasScheduleInfo {
            diagnosticMessages.append("未找到 Schedule 信息，请确保调用 pickApps() 或 applySavedSelection() 时传入了 endTime 参数")
        } else if !hasExtensionData {
            diagnosticMessages.append("Schedule 已创建，但 Extension 尚未被系统调用。请等待 Schedule 开始/结束时间到达")
        } else {
            diagnosticMessages.append("Extension 已正常工作")
        }
        if let scheduleError = defaults.string(forKey: "last_schedule_error") {
            diagnosticMessages.append("Schedule 创建失败：\(scheduleError)")
        }
        if !diagnosticMessages.isEmpty {
            result["diagnosticMessages"] = diagnosticMessages
        }
        
        result["success"] = true
        return result
    }

    // MARK: - Family Activity Picker
    @MainActor
    static func pickApps(
        shieldTitle: String? = nil,
        shieldSubtitle: String? = nil,
        shieldSubtitleColor: String? = nil,
        shieldButtonLabel: String? = nil,
        shieldButtonColor: String? = nil,
        shieldButtonTextColor: String? = nil,
        shieldIconName: String? = nil,
        shieldIconAsset: String? = nil,
        appGroupIdentifier: String? = nil,
        endTime: Date? = nil
    ) async -> [String: Any]? {
        if #available(iOS 16.0, *) {
            do {
                let center = AuthorizationCenter.shared
                if center.authorizationStatus != .approved {
                    try await center.requestAuthorization(for: .individual)
                }

                // 使用 SwiftUI 的 FamilyActivityPicker(selection:)
                struct PickerContainer: View {
                    @Environment(\.dismiss) var dismiss
                    @State var selection = FamilyActivitySelection()
                    let onDone: (FamilyActivitySelection) -> Void

                    var body: some View {
                        NavigationView {
                            FamilyActivityPicker(selection: $selection)
                                .navigationTitle("选择要限制的应用")
                                .toolbar {
                                    ToolbarItem(placement: .cancellationAction) {
                                        Button("取消") {
                                            onDone(FamilyActivitySelection())
                                            dismiss()
                                        }
                                    }
                                    ToolbarItem(placement: .confirmationAction) {
                                        Button("完成") {
                                            onDone(selection)
                                            dismiss()
                                        }
                                    }
                                }
                        }
                    }
                }

                return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[String: Any]?, Error>) in
                    guard let root = UIApplication.shared.connectedScenes
                        .compactMap({ ($0 as? UIWindowScene)?.keyWindow })
                        .first?.rootViewController else {
                        continuation.resume(returning: [
                            "success": false,
                            "error": "no_root_view_controller"
                        ])
                        return
                    }

                    let hosting = UIHostingController(rootView: PickerContainer { selection in
                        // 选择完成后，直接应用屏蔽（iOS16+）
                        let store = ManagedSettingsStore()
                        let appTokens = selection.applicationTokens
                        let categoryTokens = selection.categoryTokens
                        let webDomainTokens = selection.webDomainTokens

                        // 屏蔽所选应用（为空则清空）
                        store.shield.applications = appTokens.isEmpty ? nil : appTokens
                        // 屏蔽所选应用类别（为空则清空）
                        if categoryTokens.isEmpty {
                            store.shield.applicationCategories = nil
                        } else {
                            store.shield.applicationCategories = .specific(categoryTokens)
                        }
                        // 可选：网站域名屏蔽（为避免不同 SDK 头文件差异导致编译错误，暂时不设置域名屏蔽）
                        store.shield.webDomains = nil
                        
                        // 保存屏蔽页配置到 UserDefaults（供 Shield Configuration Extension 读取）
                        let userDefaults: UserDefaults
                        if let appGroupId = appGroupIdentifier, !appGroupId.isEmpty {
                            userDefaults = UserDefaults(suiteName: appGroupId) ?? UserDefaults.standard
                        } else {
                            userDefaults = UserDefaults.standard
                        }
                        
                        // 保存配置
                        if let title = shieldTitle {
                            userDefaults.set(title, forKey: "shield_config_title")
                        }
                        if let subtitle = shieldSubtitle {
                            userDefaults.set(subtitle, forKey: "shield_config_subtitle")
                        }
                        if let buttonLabel = shieldButtonLabel {
                            userDefaults.set(buttonLabel, forKey: "shield_config_button_label")
                        }
                        if let subtitleColor = shieldSubtitleColor {
                            userDefaults.set(subtitleColor, forKey: "shield_config_subtitle_color")
                        }
                        if let buttonColor = shieldButtonColor {
                            userDefaults.set(buttonColor, forKey: "shield_config_button_color")
                        }
                        if let buttonTextColor = shieldButtonTextColor {
                            userDefaults.set(buttonTextColor, forKey: "shield_config_button_text_color")
                        }
                        if let iconName = shieldIconName {
                            userDefaults.set(iconName, forKey: "shield_config_icon_name")
                        }
                        if let iconAsset = shieldIconAsset {
                            userDefaults.set(iconAsset, forKey: "shield_config_icon_asset")
                        }
                        // 保存结束时间（用于在屏蔽页显示剩余时间）
                        if let endTime = endTime, endTime > Date() {
                            userDefaults.set(endTime.timeIntervalSince1970, forKey: "shield_config_end_time")
                        } else {
                            userDefaults.removeObject(forKey: "shield_config_end_time")
                        }
                        userDefaults.synchronize()
                        
                        // 如果设置了截止时间，创建 DeviceActivity 定时任务
                        // 注意：需要在 DeviceActivityMonitor Extension 中处理 schedule 结束时的自动清除逻辑
                        if let endTime = endTime {
                            let now = Date()
                            let minimumInterval: TimeInterval = 5 * 60 // 5 分钟
                            if endTime.timeIntervalSince(now) < minimumInterval {
                                let errorMsg = "intervalTooShort(minimum: 300 seconds)"
                                print("❌ Failed to create DeviceActivity schedule: \(errorMsg)")
                                userDefaults.set(errorMsg, forKey: "last_schedule_error")
                                userDefaults.synchronize()
                                continuation.resume(returning: [
                                    "success": false,
                                    "error": "interval_too_short",
                                    "minIntervalSeconds": Int(minimumInterval)
                                ])
                                return
                            }
                        }
                        if let endTime = endTime, endTime > Date() {
                            do {
                                // 生成唯一的 scheduleId
                                let scheduleId = "pickApps_\(UUID().uuidString)"
                                
                                // 创建时间间隔：从当前时间到截止时间
                                let calendar = Calendar.current
                                let now = Date()
                                let startComponents = calendar.dateComponents([.hour, .minute], from: now)
                                let endComponents = calendar.dateComponents([.hour, .minute], from: endTime)
                                
                                // 创建一次性计划（不重复）
                                // 注意：DeviceActivitySchedule 基于时间而非具体日期，适用于当天的时间段
                                // 如果 endTime 跨天，会在当天晚些时候结束，需要额外处理
                                let schedule = DeviceActivitySchedule(
                                    intervalStart: DateComponents(
                                        hour: startComponents.hour,
                                        minute: startComponents.minute
                                    ),
                                    intervalEnd: DateComponents(
                                        hour: endComponents.hour,
                                        minute: endComponents.minute
                                    ),
                                    repeats: false,
                                    warningTime: nil
                                )
                                
                                // 启动 DeviceActivity 监控
                                let activityName = DeviceActivityName(scheduleId)
                                let activityCenter = DeviceActivityCenter()
                                try activityCenter.startMonitoring(activityName, during: schedule)
                                
                                // 保存 scheduleId 和 endTime 到 UserDefaults，供 DeviceActivityMonitor Extension 使用
                                // Extension 可以在 schedule 结束时调用 clearAllShields 来清除屏蔽
                                userDefaults.set(scheduleId, forKey: "current_schedule_id")
                                userDefaults.set(endTime.timeIntervalSince1970, forKey: "current_schedule_end_time")
                                userDefaults.synchronize()
                                userDefaults.removeObject(forKey: "last_schedule_error")
                                userDefaults.synchronize()
                                
                                print("✅ DeviceActivity Schedule 创建成功: \(scheduleId), 结束时间: \(endTime)")
                                
                            } catch {
                                // 如果创建定时任务失败，不影响屏蔽功能，但记录详细错误
                                let errorMsg = "Failed to create DeviceActivity schedule: \(error)"
                                print("❌ \(errorMsg)")
                                // 保存错误信息到 UserDefaults，供调试使用
                                userDefaults.set(errorMsg, forKey: "last_schedule_error")
                                userDefaults.synchronize()
                            }
                        }
                        
                        continuation.resume(returning: [
                            "success": true,
                            "appCount": appTokens.count,
                            "categoryCount": categoryTokens.count,
                            "totalSelected": appTokens.count + categoryTokens.count
                        ])
                    })

                    root.present(hosting, animated: true)
                }
            } catch {
                print("pickApps error: \(error)")
                return [
                    "success": false,
                    "error": String(describing: error)
                ]
            }
        } else {
            return [
                "success": false,
                "unsupported": true
            ]
        }
    }

    // MARK: - Select and Save (present list, save selection, close)
    @MainActor
    static func selectAndSaveApps(
        appGroupIdentifier: String? = nil,
        selectionStorageKey: String = "screenTimeSelection"
    ) async -> [String: Any]? {
        if #available(iOS 16.0, *) {
            do {
                let center = AuthorizationCenter.shared
                if center.authorizationStatus != .approved {
                    try await center.requestAuthorization(for: .individual)
                }

                // 1) 读取已保存的选择（不进行限制应用，仅用于作为初始选中值）
                let defaults = getDefaults(appGroupIdentifier: appGroupIdentifier)
                let initialSelection = loadSelection(from: defaults, key: selectionStorageKey) ?? FamilyActivitySelection()

                struct PickerContainer: View {
                    @Environment(\.dismiss) var dismiss
                    @State var selection: FamilyActivitySelection
                    let onDone: (FamilyActivitySelection) -> Void
                    var body: some View {
                        NavigationView {
                            FamilyActivityPicker(selection: $selection)
                                .navigationTitle("选择应用/分类")
                                .toolbar {
                                    // ToolbarItem(placement: .cancellationAction) {
                                    //     Button("取消") { onDone(FamilyActivitySelection()); dismiss() }
                                    // }
                                    ToolbarItem(placement: .confirmationAction) {
                                        Button("完成") { onDone(selection); dismiss() }
                                    }
                                }
                        }
                    }
                }

                return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<[String: Any]?, Error>) in
                    guard let root = UIApplication.shared.connectedScenes
                        .compactMap({ ($0 as? UIWindowScene)?.keyWindow })
                        .first?.rootViewController else {
                        continuation.resume(returning: ["success": false, "error": "no_root_view_controller"])
                        return
                    }

                    let hosting = UIHostingController(rootView: PickerContainer(selection: initialSelection) { selection in
                        // 保存选择
                        _ = saveSelection(selection, to: defaults, key: selectionStorageKey)
                        // 同步写入选择数量，供宿主 App 读取
                        if #available(iOS 16.0, *) {
                            writeSelectionCounts(selection, appGroupIdentifier: appGroupIdentifier, selectionStorageKey: selectionStorageKey)
                        }
                        continuation.resume(returning: [
                            "success": true,
                            "appCount": selection.applicationTokens.count,
                            "categoryCount": selection.categoryTokens.count,
                            "totalSelected": selection.applicationTokens.count + selection.categoryTokens.count
                        ])
                    })
                    root.present(hosting, animated: true)
                }
            } catch {
                return ["success": false, "error": String(describing: error)]
            }
        } else {
            return ["success": false, "unsupported": true]
        }
    }

    // MARK: - Apply saved selection (read and apply restriction)
    static func applySavedSelection(
        appGroupIdentifier: String? = nil,
        selectionStorageKey: String = "screenTimeSelection",
        shieldTitle: String? = nil,
        shieldSubtitle: String? = nil,
        shieldSubtitleColor: String? = nil,
        shieldButtonLabel: String? = nil,
        shieldButtonColor: String? = nil,
        shieldButtonTextColor: String? = nil,
        shieldIconName: String? = nil,
        shieldIconAsset: String? = nil,
        endTime: Date? = nil
    ) async -> [String: Any] {
        if #available(iOS 16.0, *) {
            let defaults = getDefaults(appGroupIdentifier: appGroupIdentifier)
            guard let selection = loadSelection(from: defaults, key: selectionStorageKey) else {
                return ["success": false, "error": "no_saved_selection"]
            }

            let store = ManagedSettingsStore()
            let appTokens = selection.applicationTokens
            let categoryTokens = selection.categoryTokens
            store.shield.applications = appTokens.isEmpty ? nil : appTokens
            if categoryTokens.isEmpty { store.shield.applicationCategories = nil }
            else { store.shield.applicationCategories = .specific(categoryTokens) }

            // 保存屏蔽页配置到 UserDefaults（供 Shield Configuration Extension 读取）
            let userDefaults = defaults
            if let title = shieldTitle { userDefaults.set(title, forKey: "shield_config_title") }
            if let subtitle = shieldSubtitle { userDefaults.set(subtitle, forKey: "shield_config_subtitle") }
            if let subtitleColor = shieldSubtitleColor { userDefaults.set(subtitleColor, forKey: "shield_config_subtitle_color") }
            if let buttonLabel = shieldButtonLabel { userDefaults.set(buttonLabel, forKey: "shield_config_button_label") }
            if let buttonColor = shieldButtonColor { userDefaults.set(buttonColor, forKey: "shield_config_button_color") }
            if let buttonTextColor = shieldButtonTextColor { userDefaults.set(buttonTextColor, forKey: "shield_config_button_text_color") }
            if let iconName = shieldIconName { userDefaults.set(iconName, forKey: "shield_config_icon_name") }
            if let iconAsset = shieldIconAsset { userDefaults.set(iconAsset, forKey: "shield_config_icon_asset") }
            if let end = endTime, end > Date() {
                userDefaults.set(end.timeIntervalSince1970, forKey: "shield_config_end_time")
            } else {
                userDefaults.removeObject(forKey: "shield_config_end_time")
            }
            userDefaults.synchronize()

            // 可选：创建 DeviceActivity 定时任务（同 pickApps）
            if let end = endTime {
                let now = Date()
                let minimumInterval: TimeInterval = 5 * 60
                if end.timeIntervalSince(now) < minimumInterval {
                    let errorMsg = "intervalTooShort(minimum: 300 seconds)"
                    print("❌ Failed to create DeviceActivity schedule: \(errorMsg)")
                    userDefaults.set(errorMsg, forKey: "last_schedule_error")
                    userDefaults.synchronize()
                    return [
                        "success": false,
                        "error": "interval_too_short",
                        "minIntervalSeconds": Int(minimumInterval)
                    ]
                }
            }
            if let end = endTime, end > Date() {
                do {
                    let scheduleId = "applySaved_\(UUID().uuidString)"
                    let calendar = Calendar.current
                    let now = Date()
                    let startComponents = calendar.dateComponents([.hour, .minute], from: now)
                    let endComponents = calendar.dateComponents([.hour, .minute], from: end)
                    let schedule = DeviceActivitySchedule(
                        intervalStart: DateComponents(hour: startComponents.hour, minute: startComponents.minute),
                        intervalEnd: DateComponents(hour: endComponents.hour, minute: endComponents.minute),
                        repeats: false,
                        warningTime: nil
                    )
                    let activityName = DeviceActivityName(scheduleId)
                    let activityCenter = DeviceActivityCenter()
                    try activityCenter.startMonitoring(activityName, during: schedule)
                    userDefaults.set(scheduleId, forKey: "current_schedule_id")
                    userDefaults.set(end.timeIntervalSince1970, forKey: "current_schedule_end_time")
                    userDefaults.synchronize()
                    userDefaults.removeObject(forKey: "last_schedule_error")
                    userDefaults.synchronize()
                    
                    print("✅ DeviceActivity Schedule 创建成功: \(scheduleId), 结束时间: \(end)")
                    
                } catch {
                    let errorMsg = "Failed to create DeviceActivity schedule: \(error)"
                    print("❌ \(errorMsg)")
                    userDefaults.set(errorMsg, forKey: "last_schedule_error")
                    userDefaults.synchronize()
                }
            }

            return [
                "success": true,
                "appliedApps": appTokens.count,
                "appliedCategories": categoryTokens.count,
                "totalApplied": appTokens.count + categoryTokens.count
            ]
        } else {
            return ["success": false, "unsupported": true]
        }
    }

    // pickAppsSelection 已移除
    
    // applyShields 已移除

    // MARK: - Clear all shields (apps, categories, web domains)
    static func clearAllShields() async -> Bool {
        if #available(iOS 16.0, *) {
            let store = ManagedSettingsStore()
            store.shield.applications = nil
            store.shield.applicationCategories = nil
            store.shield.webDomains = nil
            return true
        } else {
            return false
        }
    }
}
