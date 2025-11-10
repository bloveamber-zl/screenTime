# iOS ApplicationToken 简化实现

## 概述

这是一个简化的 ApplicationToken 实现，专门用于阻止传入的 Bundle ID 对应的应用。

## 核心文件

### ApplicationTokenService.swift

简化的服务类，直接使用 Bundle ID 创建 ApplicationToken。

**主要功能：**

- 直接使用 Bundle ID 创建 ApplicationToken
- 基本的错误处理和日志记录
- 支持 iOS 16+ 的 FamilyControls 框架

### ApplicationTokenError.swift

错误处理和日志系统。

## 使用方法

### 基本使用

```swift
// 1. 检查权限
let authorizationCenter = AuthorizationCenter.shared
guard authorizationCenter.authorizationStatus == .approved else {
    try await authorizationCenter.requestAuthorization(for: .individual)
}

// 2. 创建 ApplicationToken（直接使用 Bundle ID）
let bundleIds = ["com.facebook.Facebook", "com.twitter.twitter"]
let tokens = await ApplicationTokenService.shared.createApplicationTokens(for: bundleIds)

// 3. 应用阻止
let managedSettingsStore = ManagedSettingsStore()
managedSettingsStore.shield.applications = tokens
```

### 在 AppBlockingService 中的使用

```swift
// 阻止应用
let tokens = await ApplicationTokenService.shared.createApplicationTokens(for: packageNames)
if !tokens.isEmpty {
    let managedSettingsStore = ManagedSettingsStore()
    managedSettingsStore.shield.applications = tokens
}
```

## 关键改进

**之前的占位符实现：**

```swift
let tokens: Set<ApplicationToken> = [] // 空集合，无法阻止
managedSettingsStore.shield.applications = tokens
```

**现在的实现：**

```swift
let tokens = await ApplicationTokenService.shared.createApplicationTokens(for: bundleIds)
managedSettingsStore.shield.applications = tokens // 真正的阻止
```

## 注意事项

1. **Bundle ID 格式** - 确保传入的是正确的 iOS Bundle ID（如 `com.facebook.Facebook`）
2. **权限要求** - 需要 FamilyControls 权限
3. **iOS 版本** - 需要 iOS 16.0 或更高版本
4. **应用安装** - 目标应用必须安装在设备上

## 错误处理

```swift
do {
    let tokens = await ApplicationTokenService.shared.createApplicationTokens(for: bundleIds)
    if tokens.isEmpty {
        print("No tokens created - check bundle IDs")
    }
} catch {
    print("Error: \(error.localizedDescription)")
}
```

这个简化实现专注于核心功能：直接使用 Bundle ID 创建 ApplicationToken 并阻止应用，去除了不必要的复杂映射和缓存机制。
