import Foundation
import DeviceActivity
import ManagedSettings

/// DeviceActivityMonitor Extension 示例
/// 用于在 DeviceActivity schedule 结束时自动清除屏蔽
///
/// 配置步骤：
/// 1. 在 Xcode 中创建 DeviceActivityMonitor Extension Target
/// 2. 将此类添加到 Extension Target
/// 3. 在 Extension 的 Info.plist 中配置 NSExtensionPrincipalClass 为此类名
/// 4. 确保 Extension 和主 App 使用相同的 App Group（如果使用）
class DeviceActivityMonitorExtension: DeviceActivityMonitor {
    
    // App Group 标识符（需要在 Xcode 中配置 App Group 能力）
    // 如果未配置 App Group，设置为 nil 将使用 UserDefaults.standard（但 Extension 和主 App 无法共享）
    // 请将 "group.com.yourcompany.yourapp" 替换为你的实际 App Group ID
    // 重要：主 App 调用 pickApps() 或 applySavedSelection() 时传入的 appGroupIdentifier 必须与此一致
    private let appGroupIdentifier: String? = nil  // 如果未配置 App Group，保持为 nil
    // 如果已配置 App Group，改为：private let appGroupIdentifier = "group.com.yourcompany.yourapp"
    
    private var userDefaults: UserDefaults {
        if let appGroupId = appGroupIdentifier, !appGroupId.isEmpty,
           let sharedDefaults = UserDefaults(suiteName: appGroupId) {
            return sharedDefaults
        }
        return UserDefaults.standard
    }
    
    /// 当 DeviceActivity schedule 开始时调用
    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        print("DeviceActivityMonitor: intervalDidStart for \(activity.rawValue)")
    }
    
    /// 当 DeviceActivity schedule 结束时调用
    /// 这是自动清除屏蔽的关键方法
    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        print("DeviceActivityMonitor: intervalDidEnd for \(activity.rawValue)")
        
        // 读取保存的 scheduleId，确认是否需要清除
        let savedScheduleId = userDefaults.string(forKey: "current_schedule_id")
        let savedEndTime = userDefaults.double(forKey: "current_schedule_end_time")
        
        // 验证 scheduleId 是否匹配，以及是否已到结束时间
        if let scheduleId = savedScheduleId, scheduleId == activity.rawValue {
            let endTime = Date(timeIntervalSince1970: savedEndTime)
            let now = Date()
            
            // 如果已到结束时间（允许 1 分钟误差），清除屏蔽
            if now >= endTime || abs(now.timeIntervalSince(endTime)) < 60 {
                print("DeviceActivityMonitor: Clearing shields for schedule \(scheduleId)")
                clearAllShields()
                
                // 清除保存的 schedule 信息
                userDefaults.removeObject(forKey: "current_schedule_id")
                userDefaults.removeObject(forKey: "current_schedule_end_time")
                userDefaults.synchronize()
            } else {
                print("DeviceActivityMonitor: Schedule ended but time not reached yet. End time: \(endTime), Now: \(now)")
            }
        } else {
            print("DeviceActivityMonitor: Schedule ID mismatch or not found. Saved: \(savedScheduleId ?? "nil"), Current: \(activity.rawValue)")
        }
    }
    
    /// 当需要发出警告时调用（如果 schedule 配置了 warningTime）
    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        super.eventDidReachThreshold(event, activity: activity)
        print("DeviceActivityMonitor: eventDidReachThreshold for \(activity.rawValue)")
    }
    
    /// 清除所有屏蔽（应用、类别、域名）
    private func clearAllShields() {
        let store = ManagedSettingsStore()
        store.shield.applications = nil
        store.shield.applicationCategories = nil
        store.shield.webDomains = nil
        print("DeviceActivityMonitor: All shields cleared")
    }
}

