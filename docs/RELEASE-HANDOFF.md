# 0.1.0 local release handoff

The implementation is delivered as three local release artifacts. Physical-phone testing is assigned to the user after release, per their explicit request. No further USB installation is part of this handoff.

## Requirement audit — 2026-09-05

| Requirement | Implementation and evidence |
| --- | --- |
| Three platform roots, Mac first | `mac/`, `mobile/`, `tv/`; Electron/React server and separate Kotlin/Compose clients, with shared Android core. |
| Manually copied APK library | Mac folder scanner, official manifest/signature tools, SQLite index and immutable artifact copies; server tests cover persistence and rejected conflicts. |
| Version comparison | Android `Long` and server `BigInt` version codes; SDK/ABI compatibility and certificate checks. Three Android version tests and current/legacy manifest tests pass. Display version names are not used for ordering. |
| Local discovery and transfer | `_sibistore._tcp`, persistent server ID and versioned HTTP API; native Mac discovery passed again. HTTP Range/ETag, integrity verification and resumable transfer tests pass. Android physical-LAN discovery is assigned to user validation, not reported as passed. |
| Download, install and update | Shared downloader and PackageInstaller verify size, hash, package, version and certificates. Emulator install/update and real pause/resume checks are recorded in `QA.md`; installation remains an Android-confirmed action. |
| Reference-based English interfaces | Rendered Mac, phone and TV screens reviewed against the saved references; navigation glyph refinements, TV proportions and focus correction are documented in `DESIGN-REVIEW.md`. Live metadata, OS chrome and font rasterization differ from generated mockup data; this is not a pixel-identity claim. |
| Requested TV focus style | Focused primary action: black background, yellow icon/text and larger 16 sp label. Inspector footer remains visible; remote scrolling has a dedicated check. |
| Bash workflows and rules | Each platform has run/test/release entry points; `AGENTS.md` requires using them. The TV release also runs its 14 screenshot-audit tests. |
| Public source and incremental pushes | Source is on `espitman/sibiStore`; local and remote main matched at audit. No APKs, keystores or passwords are tracked. |
| Release deliverables | Phone and TV signed APKs with verified v2/v3 signatures; Apple Silicon Mac app built and packaged-launch tested. Mac is ad-hoc signed, not notarized. No GitHub Release was published. |

## Local outputs

- `mobile/release/sibi-store-mobile.apk`
- `tv/release/sibi-store-tv.apk`
- `mac/release/mac-arm64/Sibi Store.app`

The final server suite was run with the newly built phone APK as `SIBI_TEST_APK`: 5 passed, none skipped. Shared Android XML results contain 8 passing tests; TV lint and all 14 screenshot-audit tests passed. The latest Mac UI suite and packaged launch also passed. Full test scope and repeatable physical-LAN steps are in `QA.md`.

Keep the server on the private home LAN. Do not expose its unauthenticated endpoints to the internet. Android SDK Build Tools/Java are still required by the Mac APK verifier; the server application itself is Electron. Back up the private release signing key separately so future updates can use the same certificate.
