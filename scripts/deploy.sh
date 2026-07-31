#!/usr/bin/env bash
#
# deploy.sh — 构建并推送 APK 到 ADB 连接的 Android 设备
#
# 用法:
#   ./scripts/deploy.sh              # 构建 Debug APK 并安装
#   ./scripts/deploy.sh release      # 构建 Release APK 并安装
#   ./scripts/deploy.sh debug launch # 安装后自动启动游戏
#   ./scripts/deploy.sh debug clean  # 先卸载再安装
#   ./scripts/deploy.sh --no-build   # 跳过构建，直接安装已有 APK
#   ./scripts/deploy.sh -d emulator-5554  # 安装到指定设备
#
# 环境变量:
#   GAME_PACKAGE_NAME    目标游戏包名（默认 com.YostarJP.BlueArchive）
#   GAME_ACTIVITY_NAME   目标游戏启动 Activity

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
GAME_PACKAGE="${GAME_PACKAGE_NAME:-com.YostarJP.BlueArchive}"
GAME_ACTIVITY="${GAME_ACTIVITY_NAME:-com.yostarjp.bluearchive.MxUnityPlayerActivity}"

# ── parse args ─────────────────────────────────────────────────

BUILD_TYPE="debug"
DO_LAUNCH=false
DO_CLEAN=false
NO_BUILD=false
TARGET_DEVICE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        debug|release) BUILD_TYPE="$1"; shift ;;
        launch)        DO_LAUNCH=true; shift ;;
        clean)         DO_CLEAN=true; shift ;;
        --no-build)    NO_BUILD=true; shift ;;
        -d|--device)   TARGET_DEVICE="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,12p' "$0"
            exit 0
            ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

APK_DIR="$PROJECT_DIR/app/build/outputs/apk/$BUILD_TYPE"
APK_PATH="$APK_DIR/app-${BUILD_TYPE}.apk"

# ── helpers ────────────────────────────────────────────────────

_step()  { echo "› $*"; }
_ok()    { echo "  ✓ $*"; }
_err()   { echo "  ✗ $*" >&2; }

_adb() {
    if [[ -n "$TARGET_DEVICE" ]]; then
        adb -s "$TARGET_DEVICE" "$@"
    else
        adb "$@"
    fi
}

_get_device() {
    if [[ -n "$TARGET_DEVICE" ]]; then
        if ! adb devices 2>/dev/null | grep -q "^$TARGET_DEVICE"; then
            echo "未找到指定设备: $TARGET_DEVICE" >&2
            exit 1
        fi
        echo "$TARGET_DEVICE"
        return
    fi

    local count
    count=$(adb devices 2>/dev/null | grep -c 'device$' || true)

    if [[ "$count" -eq 0 ]]; then
        echo "未检测到 ADB 设备。请确保：" >&2
        echo "  1. 设备已通过 USB 连接并开启 USB 调试" >&2
        echo "  2. 设备已授权此计算机的调试请求" >&2
        echo "  3. 执行 'adb devices' 确认设备列表" >&2
        exit 1
    fi

    if [[ "$count" -gt 1 ]]; then
        echo "检测到多个设备:" >&2
        adb devices | grep 'device$' | sed 's/device.*//' >&2
        echo "请使用 -d <serial> 指定目标设备" >&2
        exit 1
    fi

    adb devices 2>/dev/null | grep 'device$' | awk '{print $1}'
}

# ── main ───────────────────────────────────────────────────────

# 1. Build
if [[ "$NO_BUILD" != "true" ]]; then
    _step "构建 APK ($BUILD_TYPE)"
    gradle_task="assemble$(tr '[:lower:]' '[:upper:]' <<< "${BUILD_TYPE:0:1}")${BUILD_TYPE:1}"
    cd "$PROJECT_DIR"

    if [[ -f "$PROJECT_DIR/gradlew" ]]; then
        "$PROJECT_DIR/gradlew" "$gradle_task" -q --no-daemon
    else
        echo "未找到 Gradle Wrapper，请先运行 'gradle wrapper'" >&2
        exit 1
    fi
    _ok "构建完成"
else
    _step "跳过构建 (--no-build)"
fi

# 2. Verify APK
if [[ ! -f "$APK_PATH" ]]; then
    echo "未找到 APK: $APK_PATH" >&2
    echo "请确认构建成功且 APK 已生成。" >&2
    exit 1
fi

apk_size=$(du -h "$APK_PATH" | cut -f1)
_ok "APK: $APK_PATH ($apk_size)"

# 3. Check ADB
_step "检查 ADB 连接"
device=$(_get_device)
_ok "已连接: $device"

# 4. Optionally uninstall
if [[ "$DO_CLEAN" == "true" ]]; then
    _step "卸载目标游戏: $GAME_PACKAGE"
    _adb uninstall "$GAME_PACKAGE" 2>/dev/null || _err "卸载失败（可能未安装）"
fi

# 5. Install
_step "安装 APK 到设备"
install_args=("install" "-r")
if [[ "$BUILD_TYPE" == "debug" ]]; then
    install_args+=("-t")
fi
install_args+=("$APK_PATH")

install_output=$(_adb "${install_args[@]}" 2>&1) || {
    if echo "$install_output" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
        echo "安装失败：签名不一致。请先卸载已安装版本（使用 clean 参数）。" >&2
    elif echo "$install_output" | grep -q "INSTALL_FAILED_INSUFFICIENT_STORAGE"; then
        echo "安装失败：设备存储空间不足。" >&2
    else
        echo "安装失败: $install_output" >&2
    fi
    exit 1
}
_ok "安装成功"
echo "  $install_output"

# 6. Optionally launch
if [[ "$DO_LAUNCH" == "true" ]]; then
    _step "启动游戏: $GAME_PACKAGE/$GAME_ACTIVITY"
    _adb shell am start -n "$GAME_PACKAGE/$GAME_ACTIVITY"
    _ok "已启动"
fi

echo ""
echo "✓ 完成"
