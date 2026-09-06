# Download storage controls — mobile 0.1.4

Settings now contains a persistent Delete after installation switch (on by
default), downloaded APK/partial-file space and file count, and Clear downloaded
files. This manages private downloaded files, not installed apps or the Mac
library. Manual cleanup skips unfinished WorkManager tasks and pending install
sessions; per-file coroutine locks also prevent races with download and install
file access.

Install sessions persist the target hash, package, version, prior installed
version and signing identity before commit. Successful installation removes its
APK only when automatic deletion is enabled. Cancelled/failed installs retain
the APK. On restart, completed updates can be reconciled with PackageManager.
MY_PACKAGE_REPLACED also cleans a matching self-update APK downloaded by an older
Sibi Store version that did not persist session metadata. Legacy cleanup requires
an OS success result and matching installed package, version and certificate.

Verification on 2026-09-06:

- `bash mobile/scripts/release.sh`: unit tests, phone lint, build and signing
  verification passed; the Desktop APK was replaced and compared by the script.
- `bash tv/scripts/test.sh`: shared-core compilation and TV lint passed. No TV
  visual changes or TV release were made for this phone request.
- `bash mobile/scripts/verify-update.sh <previous.apk> <release.apk>`: 0.1.3 (4)
  to 0.1.4 (5), same package and signing certificate.
- Storage unit tests verify totals, protected/locked files, no deletion outside
  the downloads directory, and version/signature checks for install recovery.
- On the isolated phone QA emulator, an actual system-confirmed app install with
  auto-delete enabled returned storage to zero. With the switch off, another
  successful install retained its APK and the Settings space/count reflected it.
- The off preference survived a cold launcher start. Manual cleanup removed the
  retained APK, reduced the count/space to zero, and the installed package stayed.
- Cancelling a third app in Android's installer produced INSTALL_FAILED_ABORTED;
  its downloaded APK remained (5 MB displayed), despite auto-delete being on.
- Installed the prior 0.1.3 release on the QA emulator, discovered the Mac,
  downloaded 0.1.4 through the Updates screen and confirmed the Android update.
  PackageManager reported versionCode 5/versionName 0.1.4; after reopening the
  app, Settings reported zero downloaded files and zero space. This exercises
  the actual self-update process replacement and legacy download cleanup.
- The Settings screenshot was inspected; controls and explanatory text fit the
  phone layout. The footer now reads the actual build version.

The final signed APK is in the active Mac library for in-app update. Physical
phone verification remains with the user.

## TV 0.1.1

The TV release includes the same shared storage behavior with native TV Settings
controls, neutral gray/yellow styling and D-pad focus borders.

- TV release script: core tests, TV lint, build and APK signature verification
  passed. Desktop copy matched the final artifact.
- Upgrade verification passed from TV 0.1.0 (1) to 0.1.1 (2), with the same
  package and signing certificate; installed successfully on the QA TV emulator.
- `bash tv/scripts/visual-check.sh emulator-5582` passed with a populated library
  after reconnecting the emulator's stale QA server identity to the discovered
  Mac. Both cold-launch and focused-action PNGs were inspected. The cold-launch
  image was resized to the reference's 1672 x 941 dimensions for comparison.
  Layout and TV styling are retained; actual catalog content differs.
- Settings showed the existing partial download as 15 MB/one file. The default
  was On; switching Off survived cold launch. D-pad navigation reached the clear
  button and switch; OK toggled On and cleared the partial file to zero bytes
  and zero files. All Settings text and controls fit the screen.
- Downloaded BlueBeat TV through the store on the isolated TV emulator and
  confirmed Android's installer. The store reported Installation complete and
  storage returned to zero with automatic deletion enabled.
- The TV APK is delivered on Desktop only. Adding it to the Mac library requires
  an explicit user request. Physical TV verification remains with the user.
