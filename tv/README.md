# Sibi Store for Android TV

Native English TV client with D-pad focus navigation. Approved reference: `design/reference.png`.
Reuses `mobile/core` for discovery, downloads, version checks and the system installer. The UI is a separate landscape Compose application with focusable app cards, sidebar navigation and a version inspector.

```bash
bash tv/scripts/build.sh
bash tv/scripts/test.sh
bash tv/scripts/run.sh <device-serial>
bash tv/scripts/release.sh
```

The signed output is `tv/release/sibi-store-tv.apk`. SDK setup, signing-key backup and APK compatibility rules are documented in [the Android guide](../mobile/README.md).

Use the remote's arrows to navigate, OK on an app card to focus its action, and Back to leave Settings/Updates/search. Downloads require only a reachable Mac, not internet access. Android TV still requires installation permission and a system confirmation. On some TVs, the permission screen lists all apps; select Sibi Store and allow it, then return and choose Install.

For isolated QA on Apple Silicon, install the Android 36 Google TV ARM64 system image and run `bash tv/scripts/emulator.sh` separately. Use serial `emulator-5582` and server address `10.0.2.2:8743`. This does not modify existing user AVDs.

To open a visible emulator window instead of headless QA, run `SIBI_EMULATOR_WINDOW=1 bash tv/scripts/emulator.sh`. The emulator must be stopped first if it is already running headlessly. Additional emulator arguments are forwarded by the script.
