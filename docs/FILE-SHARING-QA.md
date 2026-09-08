# Mac file sharing

The Mac **Send files** page accepts native file drops anywhere in the window or a multi-file picker. Select one or more recently connected clients, then Send. Each file/device pair has independent progress, cancellation, retry, and receiver acknowledgement. Folders are rejected explicitly; selecting up to 100 files and 1,000 pairs per batch is supported. The active queue is capped at 1,000 pairs.

The Mac reads the selected original files directly. It stores only queue metadata under its private application data directory, including source paths and a hash of each recipient credential. Moving or changing a source prevents delivery. Pending jobs survive server/application restarts; originals must remain available. The APK library is neither modified nor used as a file-transfer cache.

Updated phone, TV, and VR clients advertise `files-v1` with their persistent random device ID and token. Older clients remain visible but cannot be selected for file delivery until updated. The receiver polls while its store is active, then WorkManager continues accepted jobs in the background. The inbox and file/status endpoints require the selected recipient's credentials; queue creation exists only in the local Electron IPC interface. File metadata contains a SHA-256 digest. Receivers verify it before publishing to Downloads/Sibi Store on Android 10+, or the app's external Downloads/Sibi Store directory on Android 8–9. Received APKs are ordinary files and never trigger installation.

## Automated checks

- `bash mac/scripts/test.sh`: multi-file/multi-device isolation, token checks, Range resumes, invalid acknowledgements, empty payloads, source changes, cancellation, persisted queue, and no extra source copies.
- `bash mac/scripts/test.sh --ui`: isolated localhost Electron preview; native-backed file drag/drop, multi-file picker, two recipient selections, four queue rows, one simulated receiver completion, independent cancellation, screenshots.
- `bash mobile/scripts/test.sh`: shared Android parsing, filename safety, transfer and catalog regression tests.
- `bash tv/scripts/test.sh`: shared core tests and TV lint; no TV layout changes.
- `bash vr/scripts/bridge.sh :core:testDebugUnitTest :vrbridge:lintDebug`: Android/Unity bridge compilation and lint without release packaging or installation.

The Electron receivers are test clients, not real hardware. Physical phone/TV/Quest checks remain required after authorized releases: two same-name files, files larger than available free space, disconnect/resume, process restart before acknowledgement, notification behavior, and presence of the verified file in the target Downloads folder. No claim of physical end-to-end validation is made by the automated checks.
