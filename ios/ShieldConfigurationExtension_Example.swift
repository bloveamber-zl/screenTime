import Foundation
import ManagedSettings
import ManagedSettingsUI
import UIKit

// Override the functions below to customize the shields used in various situations.
// The system provides a default appearance for any methods that your subclass doesn't override.
// Make sure that your class name matches the NSExtensionPrincipalClass in your Info.plist.
class ShieldConfigurationExtension: ShieldConfigurationDataSource {
    
    // App Group 标识符（需要在 Xcode 中配置 App Group 能力）
    // 如果未配置 App Group，设置为 nil 将使用 UserDefaults.standard（但 Extension 和主 App 无法共享）
    // 请将 "group.com.yourcompany.yourapp" 替换为你的实际 App Group ID
    // 重要：主 App 调用 pickApps() 时传入的 appGroupIdentifier 必须与此一致
    private let appGroupIdentifier: String? = nil  // 如果未配置 App Group，保持为 nil
    // 如果已配置 App Group，改为：private let appGroupIdentifier = "group.com.yourcompany.yourapp"
    
    private var userDefaults: UserDefaults {
        if let appGroupId = appGroupIdentifier, !appGroupId.isEmpty,
           let sharedDefaults = UserDefaults(suiteName: appGroupId) {
            return sharedDefaults
        }
        return UserDefaults.standard
    }
    
    // 从 UserDefaults 读取配置并创建 ShieldConfiguration
    private func createShieldConfiguration() -> ShieldConfiguration {
        // 从 UserDefaults 读取配置（由 pickApps 方法保存）
        let title = userDefaults.string(forKey: "shield_config_title") ?? "应用已被限制"
        let subtitle = userDefaults.string(forKey: "shield_config_subtitle") ?? "此应用当前不可使用"
        let buttonLabel = userDefaults.string(forKey: "shield_config_button_label") ?? "我知道了"
        
        // 读取按钮背景颜色（十六进制字符串，如 "#FF0000"）
        var buttonColor: UIColor = .systemRed
        if let colorHex = userDefaults.string(forKey: "shield_config_button_color") {
            buttonColor = UIColor(hexString: colorHex) ?? .systemRed
        }
        
        // 读取文字颜色（可选，用于标题、副标题、按钮文字）
        var textColor: UIColor = .label
        if let textColorHex = userDefaults.string(forKey: "shield_config_text_color") {
            textColor = UIColor(hexString: textColorHex) ?? .label
        }
        
        // 读取按钮文字颜色（如果单独配置）
        var buttonTextColor: UIColor = .white
        if let buttonTextColorHex = userDefaults.string(forKey: "shield_config_button_text_color") {
            buttonTextColor = UIColor(hexString: buttonTextColorHex) ?? .white
        }
        
        // 读取图标（优先使用自定义资源，否则使用系统图标）
        var icon: UIImage? = nil
        
        // 优先读取自定义资源图标（shieldIconAsset）
        if let iconAssetName = userDefaults.string(forKey: "shield_config_icon_asset") {
            icon = UIImage(named: iconAssetName)
        }
        
        // 如果自定义资源未设置或加载失败，回退到系统图标（shieldIconName）
        if icon == nil, let iconName = userDefaults.string(forKey: "shield_config_icon_name") {
            icon = UIImage(systemName: iconName)
        }
        
        // 构建 ShieldConfiguration
        // ShieldConfiguration.Label 需要 text 和 color 两个参数
        let titleLabel = ShieldConfiguration.Label(text: title, color: textColor)
        let subtitleLabel = ShieldConfiguration.Label(text: subtitle, color: textColor)
        let buttonLabelConfig = ShieldConfiguration.Label(text: buttonLabel, color: buttonTextColor)
        
        return ShieldConfiguration(
            title: titleLabel,
            subtitle: subtitleLabel,
            primaryButtonLabel: buttonLabelConfig,
            primaryButtonBackgroundColor: buttonColor,
            icon: icon
        )
    }
    
    override func configuration(shielding application: Application) -> ShieldConfiguration {
        // Customize the shield as needed for applications.
        return createShieldConfiguration()
    }
    
    override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration {
        // Customize the shield as needed for applications shielded because of their category.
        return createShieldConfiguration()
    }
    
    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        // Customize the shield as needed for web domains.
        return createShieldConfiguration()
    }
    
    override func configuration(shielding webDomain: WebDomain, in category: ActivityCategory) -> ShieldConfiguration {
        // Customize the shield as needed for web domains shielded because of their category.
        return createShieldConfiguration()
    }
}

// UIColor 扩展：将十六进制字符串转换为 UIColor
extension UIColor {
    convenience init?(hexString: String) {
        let hex = hexString.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            return nil
        }
        self.init(
            red: CGFloat(r) / 255,
            green: CGFloat(g) / 255,
            blue: CGFloat(b) / 255,
            alpha: CGFloat(a) / 255
        )
    }
}
