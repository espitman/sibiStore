# Phone and TV catalogs

Phone 0.1.5 (6) and TV 0.1.2 (3) partition the existing API v1 catalog using
the release's `tv` metadata. The Mac derives that flag from an APK's Leanback
launcher. Releases with a TV launcher belong to TV; other releases belong to
phone. This is based on manifest metadata, not APK filenames or app titles.

Each client filters releases before exposing apps to Library, Search, Updates,
installed-app lookup and automatic installation selection. Packages with no
matching releases disappear. A package with releases for both platforms keeps
only the matching releases, preventing a higher version for the other platform
from becoming an update candidate. The same filter runs when loading the raw
offline cache. The Mac retains the complete library and requires no upgrade.

Verification:

- Both release scripts passed shared-core unit tests, platform lint, release
  builds and APK signing verification. The Desktop copies match the artifacts.
- New tests cover mixed catalogs, empty results, mixed-platform versions of one
  package, update selection and parsing the raw catalog used by cache/network.
- Upgrade checks confirm increasing version codes and unchanged signing
  certificates for both clients; both installed on the isolated QA emulators.
- The current real catalog contains eight phone apps and six TV apps. The phone
  shows phone entries and searching for TV returns No matching apps.
- TV shows all six TV entries. The populated-library visual check passed,
  including cold-launch D-pad focus, complete status labels and inspector focus.
  Both output PNGs were inspected and the library capture compared with the
  reference at 1672 x 941. Actual catalog content differs from the artwork.
- A Persian title exposed an existing status-label overflow. The icon/title gap
  now takes remaining space so taller fallback fonts leave version/status text
  inside the card. The QA script enters D-pad mode before launch to avoid false
  focus failures after a touch-based system installer.

Both artifacts are on Desktop. Following the user's explicit request, phone
0.1.5 was also copied into the active Mac library; API versionCode 6, phone
classification and SHA-256 were verified against the signed artifact. TV remains
on Desktop only.
