#!/bin/bash
set -euo pipefail

# Only run in remote Claude Code on the web environments
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

ANDROID_HOME_DIR="/opt/android-sdk"

# ── Install Android SDK command-line tools ───────────────────────────────────
if [ ! -f "$ANDROID_HOME_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Installing Android SDK command-line tools..."
  mkdir -p "$ANDROID_HOME_DIR/cmdline-tools"
  curl -fsSL \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
    -o /tmp/cmdline-tools.zip
  unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extract
  mv /tmp/cmdline-tools-extract/cmdline-tools "$ANDROID_HOME_DIR/cmdline-tools/latest"
  rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-extract
fi

export ANDROID_HOME="$ANDROID_HOME_DIR"
export ANDROID_SDK_ROOT="$ANDROID_HOME_DIR"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# ── Install SDK components ───────────────────────────────────────────────────
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager \
  "platforms;android-34" \
  "build-tools;34.0.0" \
  "platform-tools"

# ── Persist environment variables for this session ───────────────────────────
cat >> "$CLAUDE_ENV_FILE" << 'ENVEOF'
export ANDROID_HOME="/opt/android-sdk"
export ANDROID_SDK_ROOT="/opt/android-sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
ENVEOF

# ── Pre-warm Gradle dependency cache ─────────────────────────────────────────
# Downloads all Maven artifacts so builds run offline in subsequent steps.
cd "$CLAUDE_PROJECT_DIR"
./gradlew assembleDebug --quiet 2>&1 | tail -10 || true
