# iOS 屏蔽页自定义配置指南

## 可自定义的内容

通过 `pickApps()` 方法传入参数，结合 Shield Configuration Extension，可以自定义以下屏蔽页内容：

### 1. **标题 (Title)** ✅

- **参数名**: `shieldTitle`
- **类型**: `String?`
- **说明**: 屏蔽页的主标题文字
- **示例**: `"学习模式"`, `"应用已被限制"`

### 2. **副标题 (Subtitle)** ✅

- **参数名**: `shieldSubtitle`
- **类型**: `String?`
- **说明**: 屏蔽页的副标题/说明文字，显示在标题下方
- **示例**: `"该应用在当前时段不可使用"`, `"请专注于学习"`

### 3. **按钮标签 (Primary Button Label)** ✅

- **参数名**: `shieldButtonLabel`
- **类型**: `String?`
- **说明**: 屏蔽页主按钮的文字标签
- **示例**: `"我知道了"`, `"返回"`, `"申请更多时间"`

### 4. **按钮背景颜色 (Primary Button Background Color)** ✅

- **参数名**: `shieldButtonColor`
- **类型**: `String?`
- **格式**: 十六进制颜色字符串
- **说明**: 屏蔽页主按钮的背景颜色
- **示例**:
  - `"#FF0000"` - 红色
  - `"#00FF00"` - 绿色
  - `"#0000FF"` - 蓝色
  - `"#FF8800"` - 橙色

### 5. **按钮文字颜色 (Primary Button Text Color)** ✅

- **参数名**: `shieldButtonTextColor`
- **类型**: `String?`
- **格式**: 十六进制颜色字符串
- **说明**: 屏蔽页主按钮上的文字颜色
- **示例**:
  - `"#FFFFFF"` - 白色（适合深色背景按钮）
  - `"#000000"` - 黑色（适合浅色背景按钮）
  - `"#FFEB3B"` - 黄色
  - `"#E1F5FE"` - 浅蓝色

### 6. **图标 (Icon)** ✅

- **参数名**: `shieldIconName`
- **类型**: `String?`
- **说明**: 屏蔽页显示的图标，使用系统图标名称
- **常用图标示例**:
  - `"lock.fill"` - 锁定图标
  - `"hand.raised.fill"` - 举手图标
  - `"exclamationmark.triangle.fill"` - 警告图标
  - `"clock.fill"` - 时钟图标
  - `"book.fill"` - 书本图标
  - `"gamecontroller.fill"` - 游戏手柄图标
- **注意**: 图标名称必须是有效的 SF Symbols 系统图标名称

## 使用方法

### Dart 端调用示例

```dart
final result = await ScreenTime().pickApps(
  shieldTitle: '学习模式',
  shieldSubtitle: '该应用在当前时段不可使用，请专注于学习',
  shieldButtonLabel: '我知道了',
  shieldButtonColor: '#FF0000',        // 按钮背景色
  shieldButtonTextColor: '#FFFFFF',   // 按钮文字颜色
  shieldIconName: 'lock.fill',
  appGroupIdentifier: 'group.your.app.identifier', // 可选
);
```

### 完整配置示例

```dart
// 学习模式配置
await ScreenTime().pickApps(
  shieldTitle: '学习时间',
  shieldSubtitle: '当前是学习时间，该应用暂不可用',
  shieldButtonLabel: '返回',
  shieldButtonColor: '#4CAF50',      // 绿色背景
  shieldButtonTextColor: '#FFFFFF',  // 白色文字
  shieldIconName: 'book.fill',
);

// 游戏限制配置
await ScreenTime().pickApps(
  shieldTitle: '游戏时间限制',
  shieldSubtitle: '今日游戏时间已用完，请明天再试',
  shieldButtonLabel: '我知道了',
  shieldButtonColor: '#FF5722',     // 红色背景
  shieldButtonTextColor: '#FFFFFF', // 白色文字
  shieldIconName: 'gamecontroller.fill',
);

// 社交应用限制配置
await ScreenTime().pickApps(
  shieldTitle: '专注模式',
  shieldSubtitle: '为了保持专注，社交应用已被限制',
  shieldButtonLabel: '继续专注',
  shieldButtonColor: '#2196F3',     // 蓝色背景
  shieldButtonTextColor: '#FFFFFF', // 白色文字
  shieldIconName: 'hand.raised.fill',
);
```

## 前提条件

⚠️ **重要**: 要使自定义屏蔽页生效，必须在宿主 App 中：

1. **创建 Shield Configuration Extension Target**

   - Xcode → File → New → Target
   - 选择 iOS → Managed Settings UI → Shield Configuration Extension

2. **实现 ShieldConfigurationDataSource**

   - 参考 `ShieldConfigurationExtension_Example.swift` 示例代码
   - 从 UserDefaults 读取配置并返回 `ShieldConfiguration`

3. **配置 App Group (可选但推荐)**
   - 在主 App 和 Extension 中启用 App Group 能力
   - 使用相同的 Group ID 以共享 UserDefaults 数据
   - 如果未配置 App Group，Extension 将无法读取配置（显示默认屏蔽页）

## 系统限制

### 不可自定义的内容

以下内容由系统控制，无法自定义：

- ❌ 屏蔽页的整体布局结构
- ❌ 按钮的位置和大小
- ❌ 文字字体和大小（系统自动适配）
- ❌ 背景颜色（系统固定）
- ❌ 动画效果
- ❌ 按钮的点击行为（固定为返回）

### 版本要求

- **最低要求**: iOS 16.0+
- **iOS 14/15**: 不支持，会显示默认屏蔽页或功能不可用

## 常用系统图标参考

| 图标名称                        | 说明     | 适用场景 |
| ------------------------------- | -------- | -------- |
| `lock.fill`                     | 锁定     | 通用限制 |
| `hand.raised.fill`              | 举手     | 专注模式 |
| `book.fill`                     | 书本     | 学习时间 |
| `gamecontroller.fill`           | 游戏手柄 | 游戏限制 |
| `clock.fill`                    | 时钟     | 时间限制 |
| `exclamationmark.triangle.fill` | 警告     | 违规提醒 |
| `eye.slash.fill`                | 眼睛斜线 | 内容限制 |
| `shield.fill`                   | 盾牌     | 保护模式 |

更多图标可在 [SF Symbols App](https://developer.apple.com/sf-symbols/) 中查找。

## 注意事项

1. **动态配置**: 每次调用 `pickApps()` 时传入的配置会覆盖之前的设置
2. **默认值**: 如果某个参数未传入，Extension 中应提供默认值
3. **App Group**: 强烈建议配置 App Group 以确保 Extension 能读取配置
4. **测试**: 创建 Extension 后，需要在真机上测试（模拟器可能不支持）
