# Direct APK source folder — Mac 0.1.3

The selected folder is the sole APK store. The Mac keeps settings, signer pins,
icons and a SQLite metadata index, but never creates an APK snapshot. Automatic
scans reconcile the complete index with the files currently in that folder:
deletions disappear, renames update source paths, and replacements are verified
before being indexed. Duplicate bytes in the selected folder count as one version.

Download URLs remain compatible with existing clients. Before responding, the
server opens the source, verifies its size and SHA-256, and streams the requested
range from that same file descriptor. A deleted or changed source returns an error
and requests a scan instead of serving new bytes under an old hash. Edits during an
already running transfer may interrupt it or fail the client's integrity check;
the server does not keep a second copy to preserve such a transfer.

Startup migrates old metadata to source paths and removes generated legacy APK
copies and partial snapshots. It preserves unrelated files and never cleans a
legacy cache directory selected as, or overlapping, the user's library folder.

Verification:

- Unit/integration tests pass for automatic deletion detection, rename,
  same-path replacement, stale downloads before a scan, byte ranges, signer
  rejection, conflicts, persistent metadata and legacy cache cleanup.
- UI, release signing and packaged-launch checks passed. Updated Settings text
  describes source-only storage and deletion behavior.
- Live verification checked all 17 indexed versions against their original
  files and compared HTTP byte ranges with those originals. There were no APK
  copies left in app-managed storage.
- Before/after hashes confirmed all 18 source APKs were unchanged. Migration
  removed 22 internal copies totaling approximately 501.3 MiB.
- Bonjour, the persistent server identity and the live catalog passed after
  installing and restarting Mac 0.1.3. A Desktop release was also produced.
