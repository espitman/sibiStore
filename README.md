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

Work in progress: see the Git history for verified milestones. Design-preview fixture data is never part of the live library.
