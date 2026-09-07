#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/unity-env.sh"
source "$SCRIPT_DIR/../../mobile/scripts/env.sh"
export SIBI_VR_JDK="${SIBI_VR_JDK:-$HOME/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home}"
export SIBI_VR_NDK="${SIBI_VR_NDK:-$ANDROID_HOME/ndk/27.2.12479018}"
[[ -x "$SIBI_VR_JDK/bin/javac" ]] || { echo 'Set SIBI_VR_JDK to a Java 17 JDK directory.'; exit 1; }
[[ -f "$SIBI_VR_NDK/source.properties" ]] || { echo 'Run bash vr/scripts/setup-android.sh "ndk;27.2.12479018" first.'; exit 1; }
bash "$SCRIPT_DIR/setup-meta.sh"
bash "$SCRIPT_DIR/bridge.sh"
mkdir -p "$SIBI_VR_ROOT/Logs"
"$SIBI_UNITY" -batchmode -nographics -quit -projectPath "$SIBI_VR_ROOT" -buildTarget Android -executeMethod SibiBuild.Build -logFile "$SIBI_VR_ROOT/Logs/build.log" "$@"
[[ -f "$SIBI_VR_ROOT/Builds/sibi-store-vr-unsigned.apk" ]] || { echo 'Unity did not produce an APK'; exit 1; }
