#!/usr/bin/env pwsh
<#
.SYNOPSIS
    构建并推送 APK 到 ADB 连接的 Android 设备。

.DESCRIPTION
    自动完成 Gradle 构建 → ADB 设备检测 → 安装 → 可选启动游戏的全流程。
    支持 Debug/Release 构建、覆盖安装、安装后自动启动等选项。

.PARAMETER BuildType
    构建类型: debug (默认) 或 release。

.PARAMETER Launch
    安装成功后自动启动已安装的游戏。

.PARAMETER Clean
    安装前先卸载目标游戏。

.PARAMETER NoBuild
    跳过 Gradle 构建，直接使用已有 APK 安装。

.PARAMETER Device
    指定目标设备序列号（多设备时必须指定）。

.PARAMETER GamePackage
    覆盖 gradle.properties 中的目标游戏包名。

.INPUTS
    无

.OUTPUTS
    无

.EXAMPLE
    .\scripts\deploy.ps1
    构建 Debug APK 并安装到连接的设备。

.EXAMPLE
    .\scripts\deploy.ps1 -BuildType release -Launch
    构建 Release APK，安装后自动启动游戏。

.EXAMPLE
    .\scripts\deploy.ps1 -Clean
    先卸载游戏再安装。

.EXAMPLE
    .\scripts\deploy.ps1 -NoBuild -Device emulator-5554
    跳过构建，直接安装已有 APK 到指定设备。

.NOTES
    需要：ADB (Android Debug Bridge)、JDK 17+、Gradle Wrapper
#>

param(
    [ValidateSet("debug", "release")]
    [string]$BuildType = "debug",

    [switch]$Launch,

    [switch]$Clean,

    [switch]$NoBuild,

    [string]$Device,

    [string]$GamePackage
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Resolve-Path "$scriptDir/.."

# ── config ─────────────────────────────────────────────────────

$gamePackage = if ($GamePackage) { $GamePackage }
               else { "com.YostarJP.BlueArchive" }

$gameActivity = "com.yostarjp.bluearchive.MxUnityPlayerActivity"

$apkDir = "$projectDir/app/build/outputs/apk/$BuildType"
$apkPath = "$apkDir/app-$BuildType.apk"

# Capitalize first letter for Gradle task name
$gradleTask = "assemble" + $BuildType.Substring(0, 1).ToUpper() + $BuildType.Substring(1)

# ── helper functions ───────────────────────────────────────────

function Write-Step {
    param([string]$Message)
    Write-Host "› $Message" -ForegroundColor Cyan
}

function Write-OK {
    param([string]$Message)
    Write-Host "  ✓ $Message" -ForegroundColor Green
}

function Write-Err {
    param([string]$Message)
    Write-Host "  ✗ $Message" -ForegroundColor Red
}

function Invoke-Cmd {
    param([string]$Exe, [string[]]$CmdArgs)
    if ($Exe.EndsWith(".bat") -or $Exe.EndsWith(".cmd")) {
        & $Exe $CmdArgs
        if ($LASTEXITCODE -ne 0) {
            throw "$Exe exited with code $LASTEXITCODE"
        }
    } else {
        $proc = Start-Process -FilePath $Exe -ArgumentList $CmdArgs -NoNewWindow -Wait -PassThru
        if ($proc.ExitCode -ne 0) {
            throw "$Exe exited with code $($proc.ExitCode)"
        }
    }
}

# ── adb helpers ────────────────────────────────────────────────

function Get-AdbDevice {
    if ($Device) {
        $output = & adb devices 2>$null | Select-String -Pattern $Device
        if (-not $output) {
            throw "未找到指定设备: $Device"
        }
        return $Device
    }

    $devices = & adb devices 2>$null |
        Select-String -Pattern '^\S+\s+device$' |
        ForEach-Object { $_.Line -replace '\s+device.*', '' }

    if (-not $devices) {
        throw "未检测到 ADB 设备。请确保：`n" +
              "  1. 设备已通过 USB 连接并开启 USB 调试`n" +
              "  2. 设备已授权此计算机的调试请求`n" +
              "  3. 执行 'adb devices' 确认设备列表"
    }

    if ($devices.Count -gt 1) {
        Write-Host "检测到多个设备:" -ForegroundColor Yellow
        $devices | ForEach-Object { Write-Host "  · $_" -ForegroundColor Yellow }
        throw "请使用 -Device 参数指定目标设备"
    }

    return $devices[0]
}

function Invoke-Adb {
    param([string[]]$Args)
    if ($Device) {
        & adb -s $Device $Args
    } else {
        & adb $Args
    }
    if ($LASTEXITCODE -ne 0) {
        throw "adb $Args 执行失败 (exit code $LASTEXITCODE)"
    }
}

# ── main ───────────────────────────────────────────────────────

try {
    # 1. Build
    if (-not $NoBuild) {
        Write-Step "构建 APK ($BuildType)"
        Push-Location $projectDir
        try {
            if (Test-Path "$projectDir\gradlew.bat") {
                Invoke-Cmd "$projectDir\gradlew.bat" @($gradleTask, "-q", "--no-daemon")
            } else {
                throw "未找到 Gradle Wrapper，请先运行 'gradle wrapper'"
            }
        } finally {
            Pop-Location
        }
        Write-OK "构建完成"
    } else {
        Write-Step "跳过构建 (--no-build)"
    }

    # 2. Verify APK
    if (-not (Test-Path $apkPath)) {
        throw "未找到 APK: $apkPath`n请确认构建成功且 APK 已生成。"
    }
    $apkSize = [math]::Round((Get-Item $apkPath).Length / 1MB, 1)
    Write-OK "APK: $apkPath ($apkSize MB)"

    # 3. Check ADB
    Write-Step "检查 ADB 连接"
    $adbDevice = Get-AdbDevice
    Write-OK "已连接: $adbDevice"

    # 4. Optionally uninstall existing game
    if ($Clean) {
        Write-Step "卸载目标游戏: $gamePackage"
        try {
            Invoke-Adb @("uninstall", $gamePackage)
            Write-OK "已卸载"
        } catch {
            Write-Err "卸载失败（设备上可能未安装此应用）"
        }
    }

    # 5. Install APK
    Write-Step "安装 APK 到设备"
    $installArgs = @("install", "-r")
    if ($BuildType -eq "debug") {
        $installArgs += "-t"  # allow test packages
    }
    $installArgs += $apkPath

    $installOutput = & {
        if ($Device) { adb -s $Device $installArgs 2>&1 }
        else         { adb $installArgs 2>&1 }
    }
    if ($LASTEXITCODE -ne 0) {
        # Check for common error patterns
        if ($installOutput -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
            throw "安装失败：签名不一致。请先卸载已安装版本（使用 -Clean 参数）。"
        } elseif ($installOutput -match "INSTALL_FAILED_INSUFFICIENT_STORAGE") {
            throw "安装失败：设备存储空间不足。"
        } elseif ($installOutput -match "INSTALL_FAILED") {
            throw "安装失败: $installOutput"
        } else {
            throw "安装失败: $installOutput"
        }
    }
    Write-OK "安装成功"
    Write-Host "  $installOutput"

    # 6. Optionally launch
    if ($Launch) {
        Write-Step "启动游戏: $gamePackage/$gameActivity"
        Invoke-Adb @("shell", "am", "start", "-n", "$gamePackage/$gameActivity")
        Write-OK "已启动"
    }

    Write-Host ""
    Write-Host "✓ 完成" -ForegroundColor Green

} catch {
    Write-Host ""
    Write-Host "✗ 错误: $_" -ForegroundColor Red
    exit 1
}
