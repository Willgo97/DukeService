#!/usr/bin/env bash
# Build DUKE Service without needing Android Studio.
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

cd "$(dirname "$0")"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

case "${1:-release}" in
  debug)
    ./gradlew assembleDebug
    echo "-> app/build/outputs/apk/debug/app-debug.apk"
    ;;
  install)
    ./gradlew assembleRelease
    cp -f app/build/outputs/apk/release/app-release.apk ../DukeService.apk
    [ -d ../DukeService-share ] && cp -f ../DukeService.apk ../DukeService-share/DukeService.apk
    adb install -r app/build/outputs/apk/release/app-release.apk
    ;;
  clean)
    ./gradlew clean
    ;;
  *)
    ./gradlew assembleRelease
    cp -f app/build/outputs/apk/release/app-release.apk ../DukeService.apk
    # Keep the phone-install folder in step, so the QR never hands out a stale build.
    [ -d ../DukeService-share ] && cp -f ../DukeService.apk ../DukeService-share/DukeService.apk
    ls -lh ../DukeService.apk
    ;;
esac
