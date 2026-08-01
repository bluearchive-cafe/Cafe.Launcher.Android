## v1.0.0-beta.1

首个 Android APKS 安装器 beta 版本，提供从 CDN 下载 APKS、校验安装包、选择安装方式并安装游戏的完整流程。

### 新增功能

- 构建 Android APKS 安装器，支持下载 APKS/ZIP 容器并通过 Android 安装流程写入 split APK。
- 新增安装前检查：存储空间、已安装版本、安装包完整性与版本信息。
- 新增多安装后端：系统安装器、Shizuku / Sui、Root。
- 新增下载安装进度展示：总进度、当前分片、下载速度与预计剩余时间。
- 新增安装完成页操作：启动游戏，以及返回按钮。
- 新增“关于”和“设置”页面，支持主题、语言与安装方式配置。
- 新增资源面板，用于管理游戏文字、主线中配、图像影片本地化资源。
- 新增 Material 风格图标、状态图标和导航图标。
- 新增 Android CI 与 Release workflow，支持 tag 发布时构建产物并生成 GitHub Release。
- 新增本地发布脚本：构建 release APK、整理 `artifacts/distribution` 产物并生成 SHA-256。

### 改进

- 优化安装流程反馈，区分检查、下载、校验、确认、安装、完成和失败状态。
- 优化特权安装模式状态提示，并在 Shizuku 文案中补充 Sui。
- 优化安装页状态语义和无障碍公告。
- 应用全局主题，改进明暗模式一致性。
- 调整底部导航顺序：安装 → 资源面板 → 帮助 → 设置。
- 修复 Android 构建中的 deprecation warnings。
- 修正依赖许可证信息。
- 升级 GitHub Actions 使用的 action 版本，避免 Node.js 20 弃用警告。

### 发布产物

- `Cafe.Launcher.Android_v1.0.0-beta.1_apk.apk`
- `Cafe.Launcher.Android_v1.0.0-beta.1_apk.apk.sha256`
