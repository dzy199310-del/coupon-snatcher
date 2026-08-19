#!/usr/bin/env bash
# 一键在 macOS / Linux 上编译「抢券精灵」APK（无需 Android Studio）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$ROOT/build-tools"
mkdir -p "$TOOLS"

OS="$(uname -s)"
ARCH="$(uname -m)"

# 根据平台选 JDK 下载地址（Adoptium Temurin 17）
if [ "$OS" = "Darwin" ]; then
  if [ "$ARCH" = "arm64" ]; then JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jdk/hotspot/normal/eclipse"; fi
  if [ "$ARCH" = "x86_64" ]; then JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/mac/x64/jdk/hotspot/normal/eclipse"; fi
  CMDTOOLS_URL="https://dl.google.com/android/repository/commandline-tools-mac-11076708_latest.zip"
elif [ "$OS" = "Linux" ]; then
  JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  CMDTOOLS_URL="https://dl.google.com/android/repository/commandline-tools-linux-11076708_latest.zip"
else
  echo "不支持的系统: $OS"; exit 1
fi

# ---------- 1) JDK 17 ----------
JDK_DIR="$TOOLS/jdk"
if [ ! -x "$JDK_DIR/bin/javac" ]; then
  echo "[下载] JDK 17 ..."
  curl -L -o "$TOOLS/jdk.zip" "$JDK_URL"
  echo "[解压] JDK ..."
  rm -rf "$TOOLS/jdk-tmp"; mkdir -p "$TOOLS/jdk-tmp"
  unzip -q "$TOOLS/jdk.zip" -d "$TOOLS/jdk-tmp"
  EXTRACTED=$(find "$TOOLS/jdk-tmp" -maxdepth 1 -type d -name 'jdk-*' | head -1)
  rm -rf "$JDK_DIR"; mv "$EXTRACTED" "$JDK_DIR"
  rm -rf "$TOOLS/jdk-tmp" "$TOOLS/jdk.zip"
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JDK_DIR/bin:$PATH"
echo "[OK] JAVA_HOME = $JDK_DIR"

# ---------- 2) Android SDK ----------
SDK_ROOT="$TOOLS/sdk"
CMDLINE_DIR="$SDK_ROOT/cmdline-tools/latest"
if [ ! -x "$CMDLINE_DIR/bin/sdkmanager" ]; then
  echo "[下载] Android cmdline-tools ..."
  curl -L -o "$TOOLS/cmdtools.zip" "$CMDTOOLS_URL"
  echo "[解压] cmdline-tools ..."
  rm -rf "$TOOLS/cmdline-tmp"; mkdir -p "$TOOLS/cmdline-tmp"
  unzip -q "$TOOLS/cmdtools.zip" -d "$TOOLS/cmdline-tmp"
  mkdir -p "$CMDLINE_DIR"
  mv "$TOOLS/cmdline-tmp/cmdline-tools/"* "$CMDLINE_DIR/"
  rm -rf "$TOOLS/cmdline-tmp" "$TOOLS/cmdtools.zip"
fi
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
SDKMANAGER="$CMDLINE_DIR/bin/sdkmanager"

mkdir -p "$SDK_ROOT/licenses"
echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$SDK_ROOT/licenses/android-sdk-license"
echo "84831b9409646a918e30573bab4c9c91346d8abd" > "$SDK_ROOT/licenses/android-sdk-preview-license"

for p in "platforms;android-34" "build-tools;34.0.0" "platform-tools"; do
  echo "[安装 SDK] $p ..."
  yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" "$p" >/dev/null
done

printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$ROOT/local.properties"
echo "[OK] ANDROID_HOME = $SDK_ROOT"

# ---------- 3) Gradle 8.9 ----------
GRADLE_DIR="$TOOLS/gradle"
if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  echo "[下载] Gradle 8.9 ..."
  curl -L -o "$TOOLS/gradle.zip" "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
  echo "[解压] Gradle ..."
  rm -rf "$TOOLS/gradle-tmp"; mkdir -p "$TOOLS/gradle-tmp"
  unzip -q "$TOOLS/gradle.zip" -d "$TOOLS/gradle-tmp"
  EXTRACTED=$(find "$TOOLS/gradle-tmp" -maxdepth 1 -type d -name 'gradle-8.9*' | head -1)
  rm -rf "$GRADLE_DIR"; mv "$EXTRACTED" "$GRADLE_DIR"
  rm -rf "$TOOLS/gradle-tmp" "$TOOLS/gradle.zip"
fi
GRADLE="$GRADLE_DIR/bin/gradle"

# ---------- 4) 编译 ----------
echo ""
echo "========== 开始编译 app-debug.apk =========="
"$GRADLE" -p "$ROOT" assembleDebug --no-daemon

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo "==========================================="
  echo "  编译成功！APK 已生成："
  echo "  $APK"
  echo "==========================================="
else
  echo "!! 编译失败，请检查上方 gradle 报错信息。"
  exit 1
fi
