# Sibi Store VR — Quest 3

Independent Unity 6 Android client using Meta XR Core 205.0.0 and OpenXR. The Mac supplies only the VR catalog to this client. The Android bridge reuses the existing store core for discovery, resume, SHA-256 verification, certificate/version checks, system installation results, and download storage settings.

The spatial UI has independent hand and controller pointers. Runtime multimodal support is explicitly requested; tracking loss cancels a press and requires release before another press. Press/drag ownership prevents two sources fighting over the same control. No HMD interaction is considered verified by a successful desktop build.

Hold the title bar using pinch or controller trigger to move the window; release to place it. While holding it, the controller thumbstick adjusts distance. Recenter places the panel along the current gaze. The Android quality profiles use 4x MSAA, with increased text density and eye-buffer resolution for the flat UI. The client uses an explicit network security configuration permitting the home-LAN HTTP server.

## Development

- `bash vr/scripts/setup-unity.sh` installs Unity Hub, the pinned ARM64 Editor, and Android modules.
- Sign in and activate your eligible Unity license through Unity Hub.
- `bash vr/scripts/setup-meta.sh` fetches and verifies the pinned Meta package from its official registry. It is embedded locally and excluded from Git.
- `bash vr/scripts/bridge.sh` builds and exports Android libraries.
- `bash vr/scripts/prepare.sh` generates the scene and XR settings.
- `bash vr/scripts/verify-config.sh` verifies the persisted OpenXR configuration in a fresh Editor process.
- `bash vr/scripts/test.sh` runs Editor pointer lifecycle tests.
- `bash vr/scripts/visual-check.sh` renders offline Editor fixtures for layout inspection; fixtures are excluded from the Android player.
- `bash vr/scripts/setup-android.sh 'ndk;27.2.12479018'` installs the exact NDK required by this Editor. Builds reuse the Android SDK and require Java 17 (`SIBI_VR_JDK` can select an existing JDK).
- `bash vr/scripts/release.sh` builds/signs the APK and replaces `sibi-store-vr.apk` on Desktop. It does not publish to the Mac library.
- `bash vr/scripts/verify-apk.sh` checks signing, alignment, manifest metadata, ARM64 libraries, bridge retention, and ZIP integrity.
- `bash vr/scripts/install-release.sh <Quest-serial>` verifies and installs the signed Release APK while preserving app data, then cold-launches the client. Debuggable APKs are rejected.
- `bash vr/scripts/run.sh` opens the project.

The Unity package registry uses Unity's official `.cn` mirror because the `.com` endpoint returns HTTP 403 on the development network. Meta comes directly from `npm.developer.oculus.com`. No signing keys or personal paths are stored in the project.

## Headset acceptance (required)

On Quest 3 with hand tracking enabled, verify: controller-only launch; hands-only launch; free left hand plus right controller; free right hand plus left controller; pinch selection and drag scrolling; tracking loss while pressed; two sources targeting one button; recenter; suspend/resume; auto-discovery; download interruption/resume; install permission and user confirmation; successful/failed/cancelled installs; opening an installed VR app; update compatibility and signatures; storage cleanup toggle and manual deletion. Repeat after a cold launch. Confirm the runtime input status shows multimodal active.

The production catalog is empty when no VR APKs are available. Test fixtures must remain isolated from LAN discovery.
