#!/usr/bin/env bash
# 在无头模拟器里安装一次并截图（备用方案）
#
# 3.5 起只打 arm64-v8a，x86_64 模拟器装不上这个包。
# 真机验证请用：python3 ui_test.py <adb 序列号>
# 如果一定要用模拟器，先把 app/build.gradle 的 splits 加回 'x86_64' 再构建。
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export ANDROID_SDK="${ANDROID_SDK:-$HOME/projects/toolchain/sdk}"
export ANDROID_HOME="$ANDROID_SDK"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
export PATH="$ANDROID_SDK/platform-tools:$ANDROID_SDK/emulator:$ANDROID_SDK/cmdline-tools/latest/bin:$PATH"
ADB="$ANDROID_SDK/platform-tools/adb"
APK="${1:-$HERE/app/build/outputs/apk/release/app-arm64-v8a-release.apk}"
SHOTS="${SHOTS:-$HERE/screenshots}"
mkdir -p "$SHOTS"

if ! "$ADB" devices | grep -q "emulator-.*device"; then
  if [ ! -f "$ANDROID_AVD_HOME/test34.avd/config.ini" ]; then
    echo no | avdmanager create avd -n test34 \
      -k "system-images;android-34;default;x86_64" --force >/dev/null
  fi
  nohup emulator -avd test34 -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect -memory 1024 -cores 2 \
    > /tmp/opencode/emulator.log 2>&1 &
fi

echo "等待模拟器启动..."
"$ADB" wait-for-device
for i in $(seq 1 120); do
  [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 2
done
"$ADB" shell settings put global window_animation_scale 0 || true
"$ADB" shell settings put global transition_animation_scale 0 || true
"$ADB" shell settings put global animator_duration_scale 0 || true

echo "安装 APK..."
"$ADB" install -r "$APK"
"$ADB" shell am force-stop com.k.moneybook || true
"$ADB" shell pm clear com.k.moneybook >/dev/null || true
"$ADB" shell am start -W -n com.k.moneybook/.MainActivity

sleep 2
"$ADB" exec-out screencap -p > "$SHOTS/01-main-empty.png"
"$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null
"$ADB" pull /sdcard/ui.xml "$SHOTS/ui-main.xml" >/dev/null

echo "截图完成，见 $SHOTS"
