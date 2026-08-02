# apks-installer

Android 端 APKS 安装器 — 从 CDN 下载最新 APKS，并通过 `PackageInstaller.Session` API 安装其中的 split APK。

## 用户安装流程

```
启动 App
  │
  ├─ [预检] 校验存储空间 + 已安装版本
  │
  ├─ 已安装版本 → [启动游戏] / [重新安装]
  ├─ 存储不足   → 显示所需/可用空间大小 + [重新检查]
  │
  └─ 预检通过
       ├─ [下载] 从 CDN 获取 APKS + 速度 + 进度 + ETA
       ├─ [校验] 检查 APKS 容器及 APK 分片
       ├─ [确认] 显示大小、文件数（升级场景显示新旧版本号）
       ├─ [权限] Android 8+ 跳系统设置授权
       ├─ [安装] 进度条 + 当前分片名 + ETA 倒计时
       ├─ [确认] 系统弹出安装确认后自动继续
       └─ 结果
            ├─ 成功 → [启动游戏] + [卸载安装器]
            └─ 失败 → [重试] + [查看详情]
```

## 项目结构

```
cafe-apks-installer/
├── build.gradle.kts                    # 根构建 (AGP 8.7)
├── settings.gradle.kts
├── gradle.properties                   # 游戏包名配置
├── gradle/wrapper/
├── README.md
├── LICENSE
└── app/
    ├── build.gradle.kts                # compileSdk 35, minSdk 21
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/cafe/bluearchive/installer/
        │   └── InstallerActivity.java  # 下载与安装状态机
        └── res/
            ├── drawable/               # 启动图标、M2 surface、状态图标与导航图标
            ├── layout/                  # compact 底部导航布局 + 复用内容布局
            │   ├── activity_installer.xml
            │   ├── content_install.xml
            │   ├── content_help.xml
            │   └── content_settings.xml
            ├── layout-w600dp/           # medium 宽度 Navigation Rail 布局
            ├── layout-w1240dp/          # expanded 宽度持久侧栏布局
            ├── menu/                    # 响应式导航目的地菜单
            ├── mipmap-anydpi-v26/      # 自适应启动图标
            ├── values/                 # M2 色彩、排版、组件与文案
            └── values-night/           # 暗色主题语义色与主题覆盖
```

## 构建

### 事前准备

1. 安装 Android SDK (API 35) 和 Build-Tools 34.0.0+
2. 安装 JDK 17+

### 配置目标游戏

在 `gradle.properties` 中修改：

```properties
GAME_PACKAGE_NAME=com.YostarJP.BlueArchive
GAME_ACTIVITY_NAME=com.yostarjp.bluearchive.MxUnityPlayerActivity
APKS_DOWNLOAD_URL=https://download.bluearchive.cafe/android/latest
```

或通过命令行参数覆盖：

```bash
./gradlew assembleRelease -PGAME_PACKAGE_NAME=com.example.game -PAPKS_DOWNLOAD_URL=https://example.com/latest.apks
```

### 下载来源

安装器启动后会从 `APKS_DOWNLOAD_URL` 配置的 CDN 地址下载最新 APKS。下载完成后，安装器直接读取 ZIP 容器中的 APK 分片，无需手动向 `assets/` 放置文件。

如需切换下载端点，请修改 `gradle.properties` 中的 `APKS_DOWNLOAD_URL`，或通过 `-PAPKS_DOWNLOAD_URL=...` 在构建时覆盖，并确保服务返回有效的 APKS/ZIP 内容。

### 构建 APK

```bash
# Debug
./gradlew assembleDebug

# Release（启用 R8 混淆 + 资源压缩）
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/`。

### Release 签名

Release 构建必须使用 Android 签名密钥；仓库不会保存 keystore 或密码。`assembleRelease` / `Build-Distribution.ps1` 会在缺少签名输入时失败，避免发布 unsigned APK。

本地发布可通过环境变量或用户级 `~/.gradle/gradle.properties` 提供以下值：

```properties
ANDROID_SIGNING_KEYSTORE_FILE=/absolute/path/release.jks
ANDROID_SIGNING_STORE_PASSWORD=...
ANDROID_SIGNING_KEY_ALIAS=...
ANDROID_SIGNING_KEY_PASSWORD=...
# 可选：用于 Build-Distribution.ps1 校验签名证书指纹
ANDROID_SIGNING_CERT_SHA256=0123...abcd
```

GitHub Actions 的 `Release` workflow 期望在 `release` environment 中配置：

- Secret: `ANDROID_SIGNING_KEYSTORE_BASE64`（keystore 文件的 Base64 内容）
- Secret: `ANDROID_SIGNING_STORE_PASSWORD`
- Secret: `ANDROID_SIGNING_KEY_ALIAS`
- Secret: `ANDROID_SIGNING_KEY_PASSWORD`
- Variable（可选）: `ANDROID_SIGNING_CERT_SHA256`

生成 Base64 keystore 示例：

```bash
base64 -w 0 release.jks > release.jks.base64
```

PowerShell 示例：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Content release.jks.base64 -NoNewline
```

`ANDROID_SIGNING_CERT_SHA256` 可用 `apksigner verify --verbose --print-certs <apk>` 输出中的 `SHA-256 digest` 获取。直接分发 APK 时，同一应用后续升级必须始终使用同一签名证书；更换证书会导致 Android 拒绝覆盖安装。

注意：这里的 APK 签名密钥不同于 `RELEASE_MANIFEST_PUBLIC_KEY`。后者是安装器运行时校验下载 manifest 的公钥，不是 Android APK 的 keystore。

## 发布版本

`scripts/Build-Distribution.ps1` 用于准备正式发布产物，流程参考桌面端项目：读取 `app/build.gradle.kts` 中的 `versionName` / `versionCode`，校验发布 tag，构建 Release APK，并将产物整理到 `artifacts/distribution/`。

```powershell
# 使用 app/build.gradle.kts 中的 versionName 生成默认 tag (v<versionName>)
pwsh ./scripts/Build-Distribution.ps1

# 显式指定 tag；必须精确等于 v<versionName>
pwsh ./scripts/Build-Distribution.ps1 -Tag v1.0.0
```

生成的文件包括：

- `artifacts/distribution/Cafe.Launcher.Android_<tag>_apk.apk`
- `artifacts/distribution/Cafe.Launcher.Android_<tag>_apk.apk.sha256`

发布说明可通过 Git 历史生成：

```powershell
pwsh ./scripts/New-ReleaseChangelog.ps1 -PreviousTag v0.9.0 -OutputPath changelog.md
```

脚本只准备本地产物和发布说明，不会自动创建 tag、push 或上传 CDN。推送 `v*` tag 时，GitHub Actions 会运行 Release workflow，构建相同的分发产物并创建 GitHub Release。

## 部署到设备

`scripts/deploy.ps1`（Windows / PowerShell）和 `scripts/deploy.sh`（macOS / Linux / Git Bash）自动完成**构建 → ADB 设备检测 → 安装 → 可选启动**的全流程。

### 前置条件

- ADB 已安装并在 PATH 中
- 设备已通过 USB 连接，USB 调试已开启
- 设备已授权此计算机（`adb devices` 显示 `device`）

### 快速使用

```powershell
# Windows (PowerShell)
.\scripts\deploy.ps1                     # Debug 构建 → 安装
.\scripts\deploy.ps1 -BuildType release  # Release 构建 → 安装
.\scripts\deploy.ps1 -Launch             # 安装后自动启动游戏
.\scripts\deploy.ps1 -Clean              # 先卸载游戏再安装
.\scripts\deploy.ps1 -NoBuild            # 跳过构建，直接安装已有 APK

# macOS / Linux / Git Bash
./scripts/deploy.sh                      # Debug 构建 → 安装
./scripts/deploy.sh release launch       # Release 构建 → 安装 → 启动
./scripts/deploy.sh debug clean          # Debug 构建 → 先卸载 → 安装
./scripts/deploy.sh --no-build           # 跳过构建直接安装
```

### 多设备 / 指定设备

```powershell
# PowerShell
.\scripts\deploy.ps1 -Device emulator-5554
.\scripts\deploy.ps1 -Device RF8M30XXXXX  -BuildType release -Launch

# Bash
./scripts/deploy.sh -d emulator-5554
./scripts/deploy.sh -d RF8M30XXXXX release launch
```

### 脚本行为

| 步骤 | 说明 |
|------|------|
| 1. Gradle 构建 | `assembleDebug` 或 `assembleRelease`（`--no-build` 跳过） |
| 2. APK 验证 | 检查产物是否存在，显示文件大小 |
| 3. ADB 检测 | 自动发现连接设备，多设备时报错提示指定 `-Device` |
| 4. 卸载（可选）| `-Clean` 先执行 `adb uninstall` |
| 5. 安装 | `adb install -r`（Debug 额外加 `-t`） |
| 6. 启动（可选）| `-Launch` 执行 `adb shell am start -n` |

常见安装失败自动识别并给出中文提示（签名不一致、存储空间不足等）。

## 特性

- **安装前检查**：验证文件完整性、存储空间、已安装版本
- **安装进度**：分片级进度 + 总体百分比 + 预计剩余时间
- **版本识别**：检测已安装版本，支持全新安装和升级
- **Material Design 2**：M2 色彩、排版、4dp shape、surface/elevation 与主次操作层级
- **响应式导航**：compact 使用底部导航，`w600dp` 使用 Navigation Rail，`w1240dp` 使用持久侧栏
- **明暗主题**：语义色和组件状态在 `values-night/` 中独立适配
- **系统栏适配**：状态栏和导航栏保持可靠对比，避免内容被系统栏遮挡
- **无障碍**：TalkBack 状态公告、动态进度语义、48dp 触摸目标与可滚动错误详情
- **自卸载**：安装成功后提供"卸载安装器"入口
- **详细错误**：失败时显示完整异常信息，支持展开/收起

## 源于

本项目代码逻辑提取自 [bluearchive-cafe/launcher-pkg-ba-jp](https://github.com/bluearchive-cafe/launcher-pkg-ba-jp)，重构为标准 Gradle Android 项目并大幅优化用户体验。

## 许可

MIT License
