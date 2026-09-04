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
- TV inspector alignment and spacing were checked in a fresh emulator screenshot after a full rebuild. The entire default details panel, including From your Mac, is visible. D-pad OK moved focus from the app card to its install button, confirmed through the actual accessibility hierarchy. Shared tests and TV lint passed.
- Rebuilt and installed both signed release APKs on isolated phone/TV emulators. Both launched and connected to the real Mac catalog. PackageManager flags confirmed that neither installed build is debuggable.
- Signed phone release, real WorkManager lifecycle: downloaded through the loopback QA throttle, pressed Pause, force-stopped and relaunched Sibi Store, observed Paused with retained bytes, then pressed Resume. The proxy recorded `Range=bytes=673184-` and HTTP 206. The remaining 23,799,660 bytes completed, integrity verification passed and Android's unknown-source permission screen opened. No private application was installed during this test. The normal server address and full network speed were restored afterward.
- Signed phone release cancellation: while fixture version 3 was downloading, the Updates screen displayed percentage, progress, Pause and Cancel controls. Cancel stopped the request; after force-stop/relaunch, the action was Update rather than Resume, no download restarted and PackageManager still reported installed fixture version 2. Explicitly choosing Update again resumed from byte 6144 (HTTP 206), completed verification and opened the system permission screen. The QA proxy was stopped and the client restored to the normal server afterward.

## Still required before calling the entire implementation complete

- Final screenshot comparison against all three approved references after visual fixes, including phone details/updates and TV D-pad focus/scrolling.
- Physical-device LAN mDNS discovery (manual connection and the Mac announcement are verified, not a substitute for this check).

Test APKs, screenshots, isolated AVDs and runtime databases are under ignored `test-results` directories. No private APK or signing key is committed. Existing emulators used by other projects are not part of this test environment.

## Physical LAN check

1. Build the signed client with its `scripts/release.sh`, then use `scripts/install-release.sh <explicit-serial>` to install on the intended phone/TV. A differently signed debug build is never automatically removed by the script.
2. Keep the Mac server running, allow local network access, and connect both devices to the same non-guest Wi-Fi/LAN. Do not add a manual server address for the discovery check.
3. In Settings, choose Search again. Verify that the Mac appears, select it and confirm that the actual library loads.
4. Change the Mac's LAN address or reconnect it, keeping its stored server ID. Confirm that the saved client reconnects to the same advertised server without selecting a new address.
5. Stop the server. Within one foreground refresh interval plus the request timeout, the client should show Mac offline while preserving its cached library. Start the server and confirm recovery.
6. Record the OS versions, whether guest/client isolation was disabled, and the observed discovery/reconnect results. A manual-address success alone does not pass multicast discovery.

If the local JDK cannot open sockets (`Can't assign requested address`), run the same Bash command with `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true`. This affects the build/test JVM, not Android's networking behavior. Kotlin compilation uses the Gradle process and full module metadata generation to avoid stale metadata after interrupted compiler-daemon runs.

## Repeating the WorkManager lifecycle test

Start the normal Mac test library on port 8743, then run `bash mac/scripts/slow-network.sh` in a separate terminal. The helper binds only to loopback port 8744 and is never advertised. Connect the emulator to `10.0.2.2:8744`, download a sufficiently large APK, pause it, force-stop/relaunch the client and resume. The helper logs the real Range header and response status. Send SIGUSR1 to the exact PID printed by the helper to remove throttling and finish. Restore the client address to port 8743 and stop the helper when done. Do not run this helper as a public-facing proxy.

For small fixture APKs, prefix the helper command with `SIBI_QA_CHUNK_BYTES=64` to make the progress controls easy to test before download completion.
