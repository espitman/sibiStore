#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
UNITY_VERSION="6000.0.83f1"
UNITY_CHANGESET="dacc44548933"
UNITY_HUB_URL="https://public-cdn.cloud.unity3d.com/hub/prod/UnityHubSetup-arm64.dmg"
UNITY_CLI_INSTALLER_URL="https://public-cdn.cloud.unity3d.com/hub/prod/cli/install.sh"
UNITY_CLI="$HOME/.unity/bin/unity"
UNITY_HUB_APP="$HOME/Applications/Unity Hub.app"
UNITY_EDITOR_ROOT="$HOME/Unity/Hub/Editor/$UNITY_VERSION"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/sibi-unity.XXXXXX")"
MOUNT_POINT="$TEMP_DIR/hub"
MOUNTED=false

cleanup() {
    if [[ "$MOUNTED" == true ]]; then
        hdiutil detach "$MOUNT_POINT" -quiet || true
    fi
    rm -rf -- "$TEMP_DIR"
}
trap cleanup EXIT

if [[ "$(uname -s)" != "Darwin" || "$(uname -m)" != "arm64" ]]; then
    echo "Unity setup requires an Apple Silicon Mac." >&2
    exit 1
fi

install_hub() {
    if [[ -d "$UNITY_HUB_APP" ]]; then
        echo "Unity Hub is already installed at $UNITY_HUB_APP"
        return
    fi

    local dmg="$TEMP_DIR/UnityHubSetup-arm64.dmg"
    mkdir -p "$MOUNT_POINT" "$HOME/Applications"
    echo "Downloading Unity Hub from Unity's official CDN..."
    curl --fail --location --retry 3 --progress-bar "$UNITY_HUB_URL" --output "$dmg"
    hdiutil attach "$dmg" -nobrowse -readonly -mountpoint "$MOUNT_POINT" -quiet
    MOUNTED=true
    local source_app="$MOUNT_POINT/Unity Hub.app"
    [[ -d "$source_app" ]] || { echo "Unity Hub app was not found in the disk image." >&2; exit 1; }
    codesign --verify --deep --strict --verbose=2 "$source_app"
    ditto "$source_app" "$UNITY_HUB_APP"
    hdiutil detach "$MOUNT_POINT" -quiet
    MOUNTED=false
    codesign --verify --deep --strict --verbose=2 "$UNITY_HUB_APP"
    echo "Installed Unity Hub at $UNITY_HUB_APP"
}

install_cli() {
    if [[ -x "$UNITY_CLI" ]]; then
        echo "Unity CLI is already installed at $UNITY_CLI"
        return
    fi

    local installer="$TEMP_DIR/install-unity-cli.sh"
    echo "Downloading Unity's official CLI installer..."
    curl --fail --location --retry 3 --silent --show-error "$UNITY_CLI_INSTALLER_URL" --output "$installer"
    grep -q '^# Unity CLI Installer' "$installer"
    grep -q 'verify_sha256' "$installer"
    UNITY_CLI_CHANNEL=beta bash "$installer"
    [[ -x "$UNITY_CLI" ]] || { echo "Unity CLI installation did not create $UNITY_CLI" >&2; exit 1; }
}

install_editor() {
    UNITY_EDITOR_ROOT="$("$UNITY_CLI" install-path --get)/$UNITY_VERSION"
    if [[ ! -x "$UNITY_EDITOR_ROOT/Unity.app/Contents/MacOS/Unity" ]]; then
        echo "Installing Unity $UNITY_VERSION ($UNITY_CHANGESET) with Android Build Support..."
        "$UNITY_CLI" install "$UNITY_VERSION" -a arm64 -m android --cm --resume "$@"
    else
        echo "Unity $UNITY_VERSION is already installed. Ensuring Android Build Support is present..."
        "$UNITY_CLI" install "$UNITY_VERSION" -a arm64 -m android --cm --resume "$@"
    fi
}

verify_installation() {
    # The CLI can place macOS modules alongside Unity.app; expose that exact module to the Editor.
    local external_android="$UNITY_EDITOR_ROOT/PlaybackEngines/AndroidPlayer"
    local embedded_android="$UNITY_EDITOR_ROOT/Unity.app/Contents/PlaybackEngines/AndroidPlayer"
    if [[ -d "$external_android" && ! -e "$embedded_android" ]]; then
        ln -s ../../../PlaybackEngines/AndroidPlayer "$embedded_android"
    fi
    local editor="$UNITY_EDITOR_ROOT/Unity.app/Contents/MacOS/Unity"
    local android="$UNITY_EDITOR_ROOT/Unity.app/Contents/PlaybackEngines/AndroidPlayer"
    [[ -x "$editor" ]] || { echo "Unity Editor executable is missing: $editor" >&2; exit 1; }
    [[ -d "$android" ]] || { echo "Android Build Support is missing: $android" >&2; exit 1; }
    source "$SCRIPT_DIR/../../mobile/scripts/env.sh"
    bash "$SCRIPT_DIR/setup-android.sh" 'ndk;27.2.12479018'
    if [[ ! -e "$android/SDK" && -d "$ANDROID_HOME" ]]; then ln -s "$ANDROID_HOME" "$android/SDK"; fi
    local jdk17="${SIBI_VR_JDK:-$HOME/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home}"
    if [[ -x "$jdk17/bin/javac" && ( ! -e "$android/OpenJDK" || -L "$android/OpenJDK" ) ]]; then ln -sfn "$jdk17" "$android/OpenJDK"; fi
    local ndk="$ANDROID_HOME/ndk/27.2.12479018"
    if [[ -d "$ndk" && ( ! -e "$android/NDK" || -L "$android/NDK" ) ]]; then ln -sfn "$ndk" "$android/NDK"; fi
    [[ -d "$android/SDK" ]] || { echo "Unity Android SDK is missing." >&2; exit 1; }
    [[ -d "$android/NDK" ]] || { echo "Unity Android NDK is missing." >&2; exit 1; }
    [[ -d "$android/OpenJDK" ]] || { echo "Unity OpenJDK is missing." >&2; exit 1; }
    "$UNITY_CLI" editors list
    echo "Unity $UNITY_VERSION and its Android SDK, NDK, and OpenJDK are ready."
    echo "Repository: $REPO_ROOT"
}

install_hub
install_cli
install_editor "$@"
verify_installation
