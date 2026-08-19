# 抢券精灵（CouponSnatcher）

一个 **Android 悬浮窗自动抢券工具**：在任意 App 之上显示可拖拽控制面板，设定
**目标日期时间（精确到毫秒）** 与 **点击坐标**，到点自动模拟点击。无需 root 即可工作
（基于无障碍服务 `AccessibilityService.dispatchGesture`）。

---

## ⚡ 如何拿到可安装的 APK（重点）

> **你之前报错的原因**：一键脚本需要从国外服务器（adoptium / google / gradle）下载约 600MB 的
> JDK + Android SDK 工具链，而你的网络访问这些国外源被限制/屏蔽，所以卡在下载步骤。
> 下面**方式一「GitHub 云端编译」完全不在你本机下载任何东西**——由 GitHub 的服务器
> （网络不受限）帮你编译，你只下载成品 APK。这是最稳、最省事的一条路，**强烈推荐**。

### 方式一：GitHub 云端编译（本机零下载，推荐）

不需要安装 Android Studio / JDK / Gradle，也不需要你电脑能连国外网站。

1. 打开 https://github.com ，注册或登录后点右上角 **＋ → New repository**，
   Repository name 填 `coupon-snatcher`，选 **Public**，其余默认，点 **Create repository**。
2. 在仓库页面点 **“uploading an existing file”**（或把文件拖到虚线框），
   把你电脑上
   `C:\Users\GY-002\WorkBuddy\2026-08-18-21-28-58\CouponSnatcher`
   **文件夹里的全部内容**（settings.gradle.kts、app、.github 等）拖进去，然后点 **Commit changes**。
3. 进入仓库顶部的 **Actions** 标签，找到 **Build APK** 工作流，点 **Run workflow** 运行
   （如果你第 2 步的提交已自动触发运行，可跳过这步）。
4. 等待约 5–10 分钟，状态变绿 ✅ 后点进该次运行，在底部 **Artifacts → app-debug** 下载。
5. 解压得到 `app-debug.apk`。

> 说明：GitHub 免费账户即可，Actions 对公开仓库免费。成品 APK 会保留约 90 天。

### 方式二：Android Studio（本机有正常宽带时最稳）

用 **Android Studio** 打开本目录 → 菜单 `Build → Build Bundle(s) / APK(s) → Build APK(s)`。
首次打开会自动补齐 Gradle Wrapper。产物在 `app/build/outputs/apk/debug/app-debug.apk`。

### 方式三：本机一键脚本（仅在你的网络能正常连国外源时可用）

> ⚠️ 你的网络此前连不上 adoptium，本方式很可能仍会失败（Android SDK 组件仍需连 google）。
> 脚本已改为优先走国内镜像（清华 JDK / 华为云 Gradle），但 SDK 部分仍依赖 google；
> 若仍报「无法连接到远程服务器」，请直接改用上面的**方式一**。

- **Windows**：双击 `build_apk.bat`。
- **macOS / Linux**：终端运行 `bash build_apk.sh`。

> 脚本会把工具自动装进项目内的 `build-tools/` 目录，不污染你系统里的其他环境。

### 装到华为 Mate 70

1. 手机「设置 → 安全 → 更多安全设置 → 安装未知应用」里允许浏览器/文件管理安装。
2. 把 `app-debug.apk` 传到手机（微信文件传输 / USB / 网盘均可），点击安装。
3. 打开 App，依次完成：授予悬浮窗权限 → 开启无障碍服务 → 点「华为/鸿蒙 适配设置」
   （按提示把本应用加入自启动/后台白名单、关闭电池优化）→ 启动悬浮窗。

> ⚠️ **关键：系统版本**
> - **HarmonyOS 4.x（Mate 70 出厂默认）**：可正常安装并运行本 Android APK。
> - **HarmonyOS NEXT（纯鸿蒙 5.0）**：**不再兼容 Android APK**，本安装包无法安装，
>   需要单独的鸿蒙原生（ArkTS）版本 —— 如你的 Mate 70 已升级到纯鸿蒙，请告诉我，
>   我可另出一版鸿蒙 App。

---

## 功能

- 🪟 **悬浮窗形式**：覆盖在其他 App 之上，可自由拖动，不依赖常驻 Activity。
- ⏱️ **毫秒级定时**：支持设置 `yyyy-MM-dd HH:mm:ss.SSS`，到点触发。
- 📍 **指定坐标点击**：手动输入 X/Y，或用「选坐标」一键在屏幕上点取。
- 🔁 **连点次数**：可设置单次或连续多次点击（间隔 50ms）。
- 🧪 **测试点击**：立即在当前坐标点一下，验证位置是否正确。
- 🔓 **无需 root**：核心走无障碍服务；已 root 设备额外提供 `input tap` 兜底。

---

## 架构

```
MainActivity                 权限引导 + 启动悬浮窗
   │
FloatingWindowService        TYPE_APPLICATION_OVERLAY 悬浮面板（拖动/输入/倒计时）
   ├─ PreciseScheduler       高优先级线程：分段 sleep + 末段忙等，毫秒级触发
   └─ TapAccessibilityService.dispatchGesture(x, y)   真正执行点击（无需 root）
```

| 文件 | 作用 |
| --- | --- |
| `MainActivity.kt` | 申请悬浮窗权限、引导开启无障碍服务、启动服务 |
| `FloatingWindowService.kt` | 悬浮窗 UI、坐标拾取、调度触发、点击分发 |
| `PreciseScheduler.kt` | 毫秒级定时触发器 |
| `TapAccessibilityService.kt` | 无障碍点击服务（全局坐标点击） |
| `res/layout/floating_window.xml` | 悬浮窗面板布局 |
| `res/xml/accessibility_config.xml` | 无障碍服务配置（`canPerformGestures=true`） |

---

## 构建与运行

1. 用 **Android Studio**（Hedgehog / Iguana 及以上）打开本目录（Gradle 8.9 / AGP 8.5.2）。
2. 连接一部 **Android 8.0+（API 26+）** 真机（模拟器无真实触控、且无障碍手势受限，建议真机）。
3. 点击 **Run**（或 `Build → Build Bundle(s) / APK(s)`）。首次打开 AS 会自动补齐 Gradle Wrapper。
4. 安装后按下面步骤配置权限。

> 最低要求：`minSdk 26`（因使用 `java.time` 与 `dispatchGesture`）。
> 已用依赖：`androidx`、`material:1.12.0`、`constraintlayout`。

---

## 使用步骤

1. 打开 App，先点 **「授予悬浮窗权限」** → 在系统设置里允许本应用「显示在其他应用上层」。
2. 点 **「开启无障碍服务」** → 在系统「无障碍」列表里找到 **抢券精灵** 并开启。
3. 两项就绪后 **「启动悬浮窗」**，App 会退到后台，屏幕上出现悬浮控制面板。
4. 在面板上：
   - 点 **「选坐标」**，然后点一下屏幕上优惠券按钮的位置（自动填入 X/Y）；
   - 填写 **日期 / 时间 / 毫秒**（如 `2026-08-18 21:30:00` + 毫秒 `500`）；
   - 设置 **重复次数**（1 = 单次）；
   - 点 **「开始」**，面板显示倒计时 `mm:ss.mmm`。
5. 到点自动点击。可随时点 **「停止」**。

> 提示：抢券前请先手动进入对应 App 并翻到券的位置，让目标按钮落在你拾取的坐标上。

---

## 关于「毫秒级精度」的说明（重要）

- **触发判定精度**：`PreciseScheduler` 把线程优先级设为 `URGENT_DISPLAY`，在最后 5ms
  进入 **忙等（busy spin）**，对自身「何时到达目标时刻」的判断可达 **±1ms 以内**。
- **实际点击落地精度**：点击通过 `dispatchGesture` 提交给系统，系统仍会在下一个
  输入调度窗口执行，通常额外引入 **几毫秒 ~ 十几毫秒** 的系统延迟；已 root 的 `input tap`
  也类似。因此「从目标时刻到真正按下」的端到端误差一般为 **数毫秒到十几毫秒**，
  这已是**无 root 方案下的理论极限**。
- 若对极致精度有要求（<5ms 端到端），需要 **root + 内核层注入** 或设备厂商调试接口，
  超出本方案范围。

---

## 常见问题

- **无障碍服务开了但点不动？** 确认在系统无障碍里「抢券精灵」已启用，且 `accessibility_config.xml`
  中 `canPerformGestures=true`（已配置）。部分国产 ROM 需在「自启动/后台管理」里放白名单。
- **坐标不准？** 不同分辨率/缩放下坐标不同；用「选坐标」在当前设备当前界面拾取最准。
- **Android 13+ 收不到前台通知？** 已申请 `POST_NOTIFICATIONS`，首次会弹窗请允许。
- **只想单次点击？** 重复次数填 `1` 即可。

---

## 免责声明

本工具为通用自动化辅助软件，请遵守相关平台规则与当地法律法规，勿用于作弊、刷单等违规场景。
使用者须自行承担使用后果。
