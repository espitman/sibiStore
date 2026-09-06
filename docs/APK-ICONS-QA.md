# APK icons

Mac 0.1.1 fixes missing icons for APKs whose manifest references compiled vector
or adaptive XML. Previously only PNG/WebP/JPEG entries were considered, so valid
Android icons became initial-letter placeholders.

The server resolves drawable/color resources with aapt2, converts supported
vector/adaptive layers to an internally generated SVG and rasterizes it to PNG
in a hidden sandboxed Electron renderer. Rendering permits only data images,
uses escaped values, and exposes no Node APIs to the renderer. Bitmap icons keep
their original format. Unsupported drawable structures produce an extraction
error and retain the fallback rather than rejecting an otherwise valid APK.

On a subsequent scan, existing records with missing icons are repaired in place.
APK hashes, versions, signer pins, artifact URLs and installation history are
unchanged. No APK needs to be copied into the library again.

Phone 0.1.6 and TV 0.1.3 also stop drawing the fallback letter underneath an
available icon, so transparent artwork no longer reveals a second symbol.

Verification on 2026-09-06:

- `bash mac/scripts/test.sh --icons <library> <temporary-output>` extracted icons
  from all 20 existing APK files. Vector, adaptive bitmap layers, resource colors
  and Android transparent-color references were exercised. Representative PNGs
  were inspected, including Axon, dwPlayer, BlueBeat, Mirook Reader and Sibi Store.
- Unit tests cover compiled XML parsing, ARGB conversion, XML icon discovery and
  persistent icon repair without changing the existing artifact identity.
- Mac release and packaged-launch checks passed. The installed Mac app was
  replaced and restarted; the live API then returned icons for all 14 catalog
  apps, with the same persistent server ID. Bonjour verification passed.
- The existing phone client displayed the newly extracted icons after refresh.
- Both Android release scripts passed unit tests, lint and signature checks.
  Upgrade verification confirmed the same signer and increasing version codes;
  the signed releases installed on the isolated phone and TV emulators.
- Phone screenshots confirmed the real icons and removal of the initial-letter
  underlay beneath Mirook's transparent artwork. The TV populated-library visual
  check passed after its connection refreshed following the Mac restart. All six
  TV apps displayed real icons. Cold-launch and action-focus PNGs were inspected,
  with the library image compared to the reference at 1672 x 941; catalog artwork
  differs from the design fixture.

The Mac release is installed and also on Desktop. New Android artifacts are
delivered on Desktop; adding them to the Mac library requires an explicit request.
