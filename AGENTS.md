# Sibi Store project rules

- This is a personal home-LAN APK store. Platform roots are `mac/`, `mobile/`, and `tv/`.
- Implement in order: Electron Mac server, Android phone, Android TV. Keep each platform's `design/reference.png` as the visual source of truth. All product UI is English, left-to-right, black/charcoal with #FFC107 accents.
- The user manually copies APKs into the Mac library folder. Do not introduce cloud services, build watchers, external release crawling, or remote install controls.
- **Always use the checked-in Bash scripts for setup, run, build, test, and release**, including during agent work. Do not bypass them with direct npm, Gradle, or packaging commands. Add or repair the appropriate script when a workflow is missing. Scripts must locate the repository relative to their own location, use `set -euo pipefail`, and forward explicit arguments.
- Script entry points live in each platform's `scripts/`. Common Android helpers live under `mobile/scripts/` and may be called by `tv/scripts/`.
- Never commit private APKs, keystores, passwords, tokens, local settings, absolute personal paths, or build outputs. Release signing material lives outside this public repository.
- The user authorized a public GitHub repository on their account and incremental commits/pushes. Commit coherent milestones after relevant checks and push them. Do not publish GitHub Releases unless requested.
- Keep the real app empty when the library is empty. Design fixtures must be explicitly isolated in preview/test mode and never advertised on LAN.
- Verify manifest metadata, signatures, file integrity, version compatibility, resumable downloads, and Android system-install results. Do not treat build success alone as proof of end-to-end operation.
- Keep backend API versioned at `/api/v1`; use `_sibistore._tcp` discovery and a persistent server ID.
