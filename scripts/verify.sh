#!/usr/bin/env bash
# End-to-end verification on a running emulator. Installs the debug APK, grants special
# permissions, seeds demo data, walks every screen taking screenshots into verification/,
# and checks that opening Chrome produces the KEPT lock screen.
set -u
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
ADB="$SDK/platform-tools/adb.exe"
PKG=com.example.kept
OUT="$(dirname "$0")/../verification"
mkdir -p "$OUT"

shot() { sleep "${2:-1.2}"; "$ADB" exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
tap() { "$ADB" shell input tap "$1" "$2"; sleep "${3:-0.8}"; }
resumed() { "$ADB" shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity" | head -1; }

"$ADB" wait-for-device
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0

echo "== install"
"$ADB" install -r -g app/build/outputs/apk/debug/app-debug.apk >/dev/null
"$ADB" shell appops set $PKG GET_USAGE_STATS allow
"$ADB" shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
"$ADB" shell dumpsys deviceidle whitelist +$PKG >/dev/null

echo "== fresh onboarding"
"$ADB" shell pm clear $PKG >/dev/null
"$ADB" shell appops set $PKG GET_USAGE_STATS allow
"$ADB" shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
"$ADB" shell am start -W -n $PKG/.MainActivity >/dev/null
shot 01_onboarding_habits 2.5

echo "== seeded home"
"$ADB" shell pm clear $PKG >/dev/null
"$ADB" shell appops set $PKG GET_USAGE_STATS allow
"$ADB" shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
"$ADB" shell am start -W -n $PKG/.MainActivity --ez seed true >/dev/null
shot 03_home 3

echo "== chrome lock test"
"$ADB" shell am start -n com.android.chrome/com.google.android.apps.chrome.Main >/dev/null 2>&1 || \
  "$ADB" shell monkey -p com.android.chrome -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 4
TOP="$(resumed)"
echo "$TOP"
if echo "$TOP" | grep -q "$PKG/.feature.lock.LockActivity"; then echo "LOCK OK"; else echo "LOCK FAILED"; fi
shot 05_lock_intercept 1
