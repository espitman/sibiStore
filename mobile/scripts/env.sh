#!/usr/bin/env bash
set -euo pipefail
SIBI_ANDROID_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [[ -z "${JAVA_HOME:-}" ]] || [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
  for SIBI_JAVA in "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home" "/Applications/Android Studio.app/Contents/jbr/Contents/Home" /opt/homebrew/opt/openjdk@17 "$HOME/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home"; do
    if [[ -x "$SIBI_JAVA/bin/javac" ]]; then export JAVA_HOME="$SIBI_JAVA"; break; fi
  done
fi
if [[ ! -x "${JAVA_HOME:-}/bin/javac" ]]; then echo 'A Java 17+ JDK is required. Set JAVA_HOME.'; exit 1; fi
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
