#!/usr/bin/env bash
set -euo pipefail

DEVICE="${DEVICE:-}"
OUT="${OUT:-/tmp/s22i-media-probe-$(date +%Y%m%d-%H%M%S)}"
PKG="org.fitzdircon.tools.mediaprobe"
ACTIVITY="$PKG/.MediaProbeActivity"
APK="tools/s22i-media-probe/build/outputs/apk/debug/s22i-media-probe-debug.apk"

adb_cmd() {
  if [[ -n "$DEVICE" ]]; then
    adb -s "$DEVICE" "$@"
  else
    adb "$@"
  fi
}

capture() {
  local name="$1"
  shift
  echo "== $name =="
  "$@" > "$OUT/$name.txt" 2>&1 || true
}

mkdir -p "$OUT"
echo "Writing probe artifacts to $OUT"

capture pm-features adb_cmd shell pm list features
capture ifit-rivendell-package adb_cmd shell dumpsys package com.ifit.rivendell
capture ifit-glassos-package adb_cmd shell dumpsys package com.ifit.glassos_service
capture media-camera adb_cmd shell dumpsys media.camera
capture audio adb_cmd shell dumpsys audio
capture audio-flinger adb_cmd shell dumpsys media.audio_flinger

if [[ ! -f "$APK" ]]; then
  echo "APK not found at $APK"
  echo "Build it first with: ./gradlew :tools:s22i-media-probe:assembleDebug"
  exit 1
fi

adb_cmd install -r "$APK"
adb_cmd logcat -c || true
adb_cmd shell am start -n "$ACTIVITY"

echo "Grant the CAMERA and RECORD_AUDIO prompts on the console, then tap Run Probe if it does not run automatically."
echo "Collecting logcat for 20 seconds..."
adb_cmd logcat -v time -s S22iMediaProbe > "$OUT/probe-logcat.txt" &
LOGCAT_PID=$!
sleep 20
kill "$LOGCAT_PID" 2>/dev/null || true
wait "$LOGCAT_PID" 2>/dev/null || true

echo
echo "Summary hints:"
grep -E "android.hardware.(camera|microphone)|Camera IDs reported|Microphone capture|unavailable|failed|denied|open succeeded" "$OUT"/*.txt || true
echo
echo "Artifacts saved in $OUT"
