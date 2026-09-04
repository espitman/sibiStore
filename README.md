# Sibi Store

A personal Android app library for your home network. Copy APKs into a folder on your Mac, then browse, download and update them from your phone or Android TV.

| Platform | Directory | Interface |
| --- | --- | --- |
| Mac server | `mac/` | Electron + React + TypeScript |
| Android phone | `mobile/` | Kotlin + Compose |
| Android TV | `tv/` | Kotlin + Compose, remote navigation |

Approved design references are saved in each platform's `design/` directory. The UI is English, with a black, charcoal and gold palette.

## Development

Use the Bash scripts, not raw package-manager/build commands. See [project rules](AGENTS.md).

```bash
bash mac/scripts/setup.sh
bash mac/scripts/run.sh
bash mac/scripts/test.sh
bash mac/scripts/release.sh
```

The Mac scanner currently requires Android SDK Build Tools and Java 17 for the official APK signature verifier. The Electron interface/server itself runs in JavaScript, not Java. The SDK can be selected in Settings. APKs are verified, snapshotted into immutable private storage, and indexed in SQLite. The input folder may then be cleaned without invalidating a published download.

Discovery uses mDNS (`_sibistore._tcp`), followed by HTTP on port 8743. `/api/v1/info`, `/api/v1/catalog`, and `/artifacts/<sha256>.apk` are read-only LAN endpoints. Downloads support ETag and byte ranges. The current personal-LAN mode does not require pairing and must not be exposed to the public internet.

Phone and TV build/run/release instructions are in [mobile](mobile/README.md) and [TV](tv/README.md). Allow the Mac app through the local-network/firewall prompt. Devices must share a LAN without client isolation; guest Wi-Fi commonly blocks discovery or HTTP. If multicast is blocked, enter the Mac's address manually. No router port forwarding is required or recommended.

## Verification

`bash mac/scripts/test.sh --ui` checks the Electron screens. `--packaged` checks the built macOS application, and `--discovery` checks the Bonjour announcement with the native DNS-SD client while the server is running. Android test scripts run the shared version/compatibility unit tests and platform lint.

Real end-to-end QA has verified a Mac-hosted test APK download and system-confirmed install on the isolated Android phone, plus a system-confirmed version-code 1 → 2 update on Android TV. Android discovery on physical home-network hardware remains a separate check: emulator NAT does not reproduce LAN multicast. The native Mac Bonjour service was independently discovered and resolved. See [QA record](docs/QA.md) for scope and remaining checks.

Design-preview fixture data is never part of the live library. This is a personal LAN tool, not a hardened public package repository.
