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
    ls app/build/outputs/apk/debug/*.apk
    ;;
  install)
    ./gradlew assembleRelease
    cp -f app/build/outputs/apk/release/app-arm64-v8a-release.apk ../DukeService.apk
    [ -d ../DukeService-share ] && cp -f ../DukeService.apk ../DukeService-share/DukeService.apk
    # the emulator is x86_64; a phone gets the arm64 build above
    adb install -r app/build/outputs/apk/release/app-x86_64-release.apk
    ;;
  clean)
    ./gradlew clean
    ;;
  *)
    ./gradlew assembleRelease
    # Phones are arm64; that is the build the QR hands out.
    cp -f app/build/outputs/apk/release/app-arm64-v8a-release.apk ../DukeService.apk
    [ -d ../DukeService-share ] && cp -f ../DukeService.apk ../DukeService-share/DukeService.apk
    ls -lh ../DukeService.apk
    ;;
esac
