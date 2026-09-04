# Verification record

## Verified on 2026-09-05

- Mac Node tests: metadata parsing (legacy and current aapt2 output), 64-bit version ordering, immutable APK storage, version conflicts, signature rejection, persistence, ETags and HTTP byte ranges.
- Real APK indexing through Android SDK `aapt2` and `apksigner`.
- Electron preview UI: library, search, empty results, settings and server start/stop.
- Packaged ad-hoc-signed Apple Silicon macOS application launches with bundled runtime, SQLite and IPC.
- Mac Bonjour announcement discovered and resolved through independent native `dns-sd`; TXT server ID matched `/api/v1/info`.
- Android shared unit tests: compatible release selection by SDK/ABI, install/update/current/newer states, signature mismatch and 64-bit precision. Phone and TV lint passed.
- Real HTTP catalog and APK downloads via emulator host networking, without requiring validated internet connectivity.
- Phone: uninstalled fixture → version code 2, with unknown-source permission and Android's Install confirmation; PackageManager reported version code 2 afterward.
- TV: fixture version code 1 → 2 through Sibi Store's download and Android's Update confirmation; PackageManager reported version code 2 afterward.
- Both clients return to the library and show installation success and the Open action.
- Signed phone and TV APK builds passed, including v2/v3 signature verification. Keys are stored privately outside the repository.
- Production Android transfer code exercised against a real local HTTP socket: interrupted response preserves partial bytes, pause preserves bytes, resume sends the exact Range and If-Range headers, HTTP 200 safely restarts, invalid ranges cannot publish a file, and SHA-256 failure discards corrupted content. Five transfer tests passed alongside the version tests and phone lint.

## Still required before calling the entire implementation complete

- Final screenshot comparison against all three approved references after visual fixes, including phone details/updates and TV D-pad focus/scrolling.
- Signed Android release launch verification.
- Device-level WorkManager pause/resume lifecycle check (the shared production transfer path is socket-tested).
- Physical-device LAN mDNS discovery (manual connection and the Mac announcement are verified, not a substitute for this check).

Test APKs, screenshots, isolated AVDs and runtime databases are under ignored `test-results` directories. No private APK or signing key is committed. Existing emulators used by other projects are not part of this test environment.
