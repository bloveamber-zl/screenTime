import Flutter
import UIKit
import Foundation

public class ScreenTimePlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "screen_time", binaryMessenger: registrar.messenger())
    let instance = ScreenTimePlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
        case MethodName.pickApps:
            let args = call.arguments as? [String : Any] ?? [:]
            let shieldTitle = args[Argument.shieldTitle] as? String
            let shieldSubtitle = args[Argument.shieldSubtitle] as? String
            let shieldButtonLabel = args[Argument.shieldButtonLabel] as? String
            let shieldSubtitleColor = args[Argument.shieldSubtitleColor] as? String
            let shieldButtonColor = args[Argument.shieldButtonColor] as? String
            let shieldButtonTextColor = args[Argument.shieldButtonTextColor] as? String
            let shieldIconName = args[Argument.shieldIconName] as? String
            let shieldIconAsset = args[Argument.shieldIconAsset] as? String
            let appGroupIdentifier = args[Argument.appGroupIdentifier] as? String
            let endTimeMs = args[Argument.endTime] as? Int64
            let endTime = endTimeMs != nil ? Date(timeIntervalSince1970: TimeInterval(endTimeMs!) / 1000.0) : nil
            
            Task {
                let selected = await ScreenTimeMethod.pickApps(
                    shieldTitle: shieldTitle,
                    shieldSubtitle: shieldSubtitle,
                    shieldSubtitleColor: shieldSubtitleColor,
                    shieldButtonLabel: shieldButtonLabel,
                    shieldButtonColor: shieldButtonColor,
                    shieldButtonTextColor: shieldButtonTextColor,
                    shieldIconName: shieldIconName,
                    shieldIconAsset: shieldIconAsset,
                    appGroupIdentifier: appGroupIdentifier,
                    endTime: endTime
                )
                if let selected = selected {
                    let jsonData = try! JSONSerialization.data(withJSONObject: selected)
                    let jsonString = String(data: jsonData, encoding: .utf8)!
                    result(jsonString)
                } else {
                    result(nil)
                }
            }
        case MethodName.selectAndSaveApps:
            let args = call.arguments as? [String : Any] ?? [:]
            let appGroupIdentifier = args[Argument.appGroupIdentifier] as? String
            let selectionStorageKey = args[Argument.selectionStorageKey] as? String ?? "screenTimeSelection"
            Task {
                let resultDict = await ScreenTimeMethod.selectAndSaveApps(
                    appGroupIdentifier: appGroupIdentifier,
                    selectionStorageKey: selectionStorageKey
                )
                if let resultDict = resultDict {
                    let jsonData = try! JSONSerialization.data(withJSONObject: resultDict)
                    let jsonString = String(data: jsonData, encoding: .utf8)!
                    result(jsonString)
                } else {
                    result(nil)
                }
            }
        case MethodName.applySavedSelection:
            let args2 = call.arguments as? [String : Any] ?? [:]
            let appGroupIdentifier2 = args2[Argument.appGroupIdentifier] as? String
            let selectionStorageKey2 = args2[Argument.selectionStorageKey] as? String ?? "screenTimeSelection"
            let shieldTitle = args2[Argument.shieldTitle] as? String
            let shieldSubtitle = args2[Argument.shieldSubtitle] as? String
            let shieldButtonLabel = args2[Argument.shieldButtonLabel] as? String
            let shieldSubtitleColor2 = args2[Argument.shieldSubtitleColor] as? String
            let shieldButtonColor = args2[Argument.shieldButtonColor] as? String
            let shieldButtonTextColor = args2[Argument.shieldButtonTextColor] as? String
            let shieldIconName = args2[Argument.shieldIconName] as? String
            let shieldIconAsset = args2[Argument.shieldIconAsset] as? String
            let endTimeMs = args2[Argument.endTime] as? Int64
            let endTime = endTimeMs != nil ? Date(timeIntervalSince1970: TimeInterval(endTimeMs!) / 1000.0) : nil
            Task {
                let dict = await ScreenTimeMethod.applySavedSelection(
                    appGroupIdentifier: appGroupIdentifier2,
                    selectionStorageKey: selectionStorageKey2,
                    shieldTitle: shieldTitle,
                    shieldSubtitle: shieldSubtitle,
                    shieldSubtitleColor: shieldSubtitleColor2,
                    shieldButtonLabel: shieldButtonLabel,
                    shieldButtonColor: shieldButtonColor,
                    shieldButtonTextColor: shieldButtonTextColor,
                    shieldIconName: shieldIconName,
                    shieldIconAsset: shieldIconAsset,
                    endTime: endTime
                )
                let jsonData = try! JSONSerialization.data(withJSONObject: dict)
                let jsonString = String(data: jsonData, encoding: .utf8)!
                result(jsonString)
            }
        case MethodName.clearAllShields:
            Task {
                let ok = await ScreenTimeMethod.clearAllShields()
                result(ok)
            }
        case MethodName.readSelectionCounts:
            let args = call.arguments as? [String : Any] ?? [:]
            let appGroupIdentifier = args[Argument.appGroupIdentifier] as? String
            let selectionStorageKey = args[Argument.selectionStorageKey] as? String ?? "screenTimeSelection"
            let dict = ScreenTimeMethod.readSelectionCounts(
                appGroupIdentifier: appGroupIdentifier,
                selectionStorageKey: selectionStorageKey
            )
            let jsonData = try! JSONSerialization.data(withJSONObject: dict)
            let jsonString = String(data: jsonData, encoding: .utf8)!
            result(jsonString)
        case MethodName.getFamilyControlsAuthorizationStatus:
            if #available(iOS 16.0, *) {
                let dict = ScreenTimeMethod.getFamilyControlsAuthorizationStatus()
                let jsonData = try! JSONSerialization.data(withJSONObject: dict)
                let jsonString = String(data: jsonData, encoding: .utf8)!
                result(jsonString)
            } else {
                result(FlutterError(code: "UNSUPPORTED", message: "iOS 16.0+ required", details: nil))
            }
        case MethodName.requestFamilyControlsAuthorization:
            if #available(iOS 16.0, *) {
                Task {
                    let dict = await ScreenTimeMethod.requestFamilyControlsAuthorization()
                    let jsonData = try! JSONSerialization.data(withJSONObject: dict)
                    let jsonString = String(data: jsonData, encoding: .utf8)!
                    result(jsonString)
                }
            } else {
                result(FlutterError(code: "UNSUPPORTED", message: "iOS 16.0+ required", details: nil))
            }
        case MethodName.getExtensionDebugInfo:
            let args = call.arguments as? [String : Any] ?? [:]
            let appGroupIdentifier = args[Argument.appGroupIdentifier] as? String
            let dict = ScreenTimeMethod.getExtensionDebugInfo(appGroupIdentifier: appGroupIdentifier)
            let jsonData = try! JSONSerialization.data(withJSONObject: dict)
            let jsonString = String(data: jsonData, encoding: .utf8)!
            result(jsonString)
        default:
          result(FlutterMethodNotImplemented)
    }
  }
}
