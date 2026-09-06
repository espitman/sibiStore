# Automatic discovery verification

## Mobile 0.1.2

The primary discovery protocol remains `_sibistore._tcp` over Android NSD,
matching the native DNS-SD approach used by dwPlayer/dwShare. A Wi-Fi multicast
lock is held while discovery is active and released when the app pauses.

When multicast does not arrive, foreground discovery also checks the Sibi info
endpoint on port 8743 within connected private IPv4 Wi-Fi/Ethernet subnets
(prefixes /20 through /30). It excludes the device, network and broadcast
addresses, public networks and VPN interfaces. Requests use 24 workers,
bounded timeouts, no redirects, and responses limited to 4 KiB. A result must
identify Sibi Store, protocol 1 and a nonempty server ID. Normal connection then
checks that identity against the catalog. NSD continues to support server ports
other than 8743 and networks outside that fallback range.

Validation on 2026-09-06:

- `bash mobile/scripts/release.sh`: unit tests and lint passed; release signature
  verified; APK copied directly to Desktop and compared with the release file.
- Added tests cover /23 subnet boundaries, excluded address ranges, invalid or
  oversized info responses, unsupported protocols and redirect rejection.
- Installed the release on the isolated phone QA emulator. Cleared only that
  emulator's Sibi app data, then used `check-launcher.sh` for a cold launcher start.
- Without entering an address, LAN fallback found the running Mac server. The
  discovered name appeared in the connection panel; selecting it displayed
  `Mac connected` and the real catalog.
- `dumpsys wifi` showed the app multicast lock in the foreground and no app lock
  after returning Home. Installed version was 0.1.2, versionCode 3.

This verifies the release and automatic fallback on the emulator. Discovery on
the user's physical phone and Wi-Fi remains awaiting confirmation.
