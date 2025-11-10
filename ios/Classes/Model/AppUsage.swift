//
//  AppUsage.swift
//  screen_time
//
//  Created by Assistant on 2024
//

import Foundation

struct AppUsage: Codable {
    let appName: String
    let packageName: String
    let usageTime: Int64
    let launchCount: Int
    let lastTimeUsed: Int64
    
    init(appName: String, packageName: String, usageTime: Int64, launchCount: Int, lastTimeUsed: Int64) {
        self.appName = appName
        self.packageName = packageName
        self.usageTime = usageTime
        self.launchCount = launchCount
        self.lastTimeUsed = lastTimeUsed
    }
}
