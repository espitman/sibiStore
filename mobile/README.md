# Sibi Store for Android phones

Native English Android client. Approved visual reference: `design/reference.png`.
The `core/` module is shared with TV: DNS-SD discovery, cached catalog, installed-package inspection, resumable WorkManager downloads and Android's system installer.

## Run and release

Install Android SDK 35, Build Tools and JDK 17+ (Android Studio's bundled JDK is detected). Use the checked-in scripts from any working directory:

```bash
bash mobile/scripts/build.sh
bash mobile/scripts/test.sh
bash mobile/scripts/run.sh <device-serial>
bash mobile/scripts/release.sh
```

`run.sh` requires an explicit serial so it never takes over another project's emulator. It builds, installs the debug APK and opens Sibi Store. `release.sh` runs tests and lint, builds and signs `mobile/release/sibi-store-mobile.apk`. The signing key and password are generated outside the repository in `~/.local/share/sibi-store/signing`; back up this directory to preserve update compatibility. Never publish it. Debug and release certificates differ, so switching between them requires removing the old test installation first.

For isolated, headless QA emulators on Apple Silicon, install the Android 36 Google APIs Play Store image, then run `bash mobile/scripts/emulator.sh` in a separate terminal. Its serial is `emulator-5580`; its disposable AVD lives in ignored `test-results/avds`. An emulator can connect to the Mac using `10.0.2.2:8743`. This is an emulator-only alias, not a real-device address.

## On your phone

Connect to the same home LAN as the Mac. Select the discovered Mac, or enter the address shown in the Mac server's Settings. Open Library, then tap Install or Update. Downloads continue in the background; ready files are verified before the system installation confirmation appears. Android asks once to allow installation from Sibi Store. This app does not bypass that permission or silently install packages.

Version names are display-only. Comparisons use Android's 64-bit version code after checking minimum SDK, CPU ABI and signing certificate. The current implementation accepts standalone APKs, not split APK bundles or signing-key rotation.

## QA helpers

`fixture.sh 1` and `fixture.sh 2` build two versions of the isolated `com.sibi.store.fixture` app. Copy the resulting files from `test-results/fixtures/` into a test server's library to exercise install/update without touching personal apps. `device.sh <serial> screenshot <absolute-path>` captures the actual screen; `ui.sh <serial> list` inspects controls. All generated APKs, captures and AVD state stay ignored.
