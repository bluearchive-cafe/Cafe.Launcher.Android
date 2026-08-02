## v1.0.0-beta.2

第二个 Android APKS 安装器 beta 版本，重点提升安装生命周期可靠性、APKS 校验安全性、Release 签名发布流程，以及平板设备上的 Material 2 显示体验。

### 新增功能

- 新增 Release APK 签名配置，Release 构建缺少 keystore 或密码时会直接失败，避免生成 unsigned APK。
- 新增 GitHub Actions Release 环境签名支持，可从 `release` environment secret 还原 keystore 并构建签名产物。
- 新增发布产物签名校验，发布脚本会通过 `apksigner` 校验 APK 签名，并可选校验证书 SHA-256 指纹。
- 新增平板专用布局：中/大屏使用顶部 AppBar + 左侧常驻导航栏，替代手机底部导航。
- 新增大平板资源面板双列布局，UID 配置与资源开关列表分栏展示。

### 改进

- 加强 APKS 接受校验，安装前验证 split APK 元数据、大小、哈希与兼容性，减少无效安装包进入安装流程。
- 优化安装取消与失败后的清理逻辑，避免残留临时文件或继续执行已取消的安装任务。
- 优化系统、Shizuku/Sui 与 Root 安装后端的生命周期处理和失败反馈。
- 优化资源面板状态刷新、保存与可访问性反馈。
- 优化平板下安装、帮助、设置、资源面板页面的宽度、高度和滚动安全区域，避免内容溢出到左侧导航栏或底部系统导航栏。
- 统一“关于”和“许可证”页面 AppBar 样式，并修正返回箭头图标视觉对齐。
- 将 Release 签名配置、GitHub Actions secrets/variables 与本地发布方式补充到 README。

### 修复

- 修复 `0dp` match-constraint 视图在应用系统栏 inset 时被错误压缩，导致平板左侧导航栏空白的问题。
- 修复安装取消后状态回退、通知和临时文件清理不一致的问题。
- 修复 APKS 解析与下载阶段部分异常未被准确映射的问题。
- 修复特权安装服务连接、shell 会话和安装进度回调中的若干边界问题。

### 发布产物

- `Cafe.Launcher.Android_v1.0.0-beta.2_apk.apk`
- `Cafe.Launcher.Android_v1.0.0-beta.2_apk.apk.sha256`
