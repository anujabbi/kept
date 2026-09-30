#!/usr/bin/env bash
# Make sure an emulator is up before dev or qa installs a build.
#
#   emulator.sh ensure [avd]   start the AVD headless if no device is attached, wait for boot
#   emulator.sh stop           kill the running emulator
#
# Default AVD is kept_api35 (set AVD to override). Uses the same SDK lookup as verify.sh.
set -euo pipefail

SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
ADB="$SDK/platform-tools/adb.exe"
EMU="$SDK/emulator/emulator.exe"
AVD="${2:-${AVD:-kept_api35}}"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-180}"

device_up() { "$ADB" devices | awk 'NR>1 && $2=="device"' | grep -q .; }
booted() { [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; }

cmd_ensure() {
  if device_up && booted; then echo "emulator: already running"; return; fi
  "$EMU" -list-avds | tr -d '\r' | grep -qx "$AVD" || { echo "no AVD named $AVD; have: $("$EMU" -list-avds | tr '\n' ' ')" >&2; exit 1; }
  echo "emulator: starting $AVD headless"
  # Detached so the calling agent's shell can return. Logs go next to the repo.
  nohup "$EMU" -avd "$AVD" -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect \
    > "../kept-emulator.log" 2>&1 &
  "$ADB" wait-for-device
  local waited=0
  until booted; do
    sleep 3; waited=$((waited + 3))
    [ "$waited" -lt "$BOOT_TIMEOUT" ] || { echo "emulator: not booted after ${BOOT_TIMEOUT}s, see ../kept-emulator.log" >&2; exit 1; }
  done
  "$ADB" shell settings put global window_animation_scale 0
  "$ADB" shell settings put global transition_animation_scale 0
  "$ADB" shell settings put global animator_duration_scale 0
  echo "emulator: booted after ${waited}s"
}

cmd_stop() { "$ADB" emu kill >/dev/null 2>&1 && echo "emulator: stopped" || echo "emulator: nothing running"; }

case "${1:-}" in
  ensure) cmd_ensure ;;
  stop) cmd_stop ;;
  *) sed -n '2,7p' "$0"; exit 2 ;;
esac
