# Direct client file sharing

Phone, TV and Quest clients can select multiple files and multiple online recipients from Settings → Send files. Each recipient sees the sender, filenames and total size and must accept or reject the request. Android request notifications have Accept/Reject actions. Phone and TV also show an in-app prompt; Quest shows a Unity prompt, so system notification visibility is not required while the store is open.

The common Files screen uses Android's document picker and supports D-pad focus. It opens as a native Android activity on Quest; the incoming request prompt remains available in the immersive Unity interface. The Android document picker handles general file selection. Devices without a document provider can use Choose received files to re-share files in the store’s own Downloads folder. Selected documents are read through URI grants, without making sender-side copies. Persisted source grants are released when no active offer or current selection uses them.

## Transport

The Mac introduces clients and holds only offer metadata, decisions and status in memory. File bytes travel over a direct HTTP connection from receiver to sender, with per-file random bearer capabilities. The sender's endpoint refuses bytes while an offer is pending, rejected or cancelled. Clients stop serving when their Mac authorization snapshot is stale. Resumable downloads verify size and SHA-256 before publishing to Downloads/Sibi Store (app external Downloads on Android 8–9). Received APKs are ordinary files and never trigger installation.

Both clients must be directly reachable. Being connected to the same Mac does not bypass guest Wi-Fi isolation, VPN routing or firewall rules. There is no relay fallback. The foreground file-sharing service keeps request polling and the sender endpoint alive when opening a picker or leaving the store; its notification provides Stop sharing. Android can still terminate it or impose foreground-service time limits; reopening the app re-enables sharing. Process restart can resume accepted transfers if the Mac still has the offer and source document access remains available. A Mac restart clears peer offers; send a new request afterward.

## Versioned signaling

All requests use X-Device-Id, X-Device-Token, X-Device-Platform, X-Device-Name and the peers-v1 capability.

- POST /api/v1/peers/register: listener port; the server derives the address from the connection.
- GET /api/v1/peers: online recipients.
- POST /api/v1/peer-transfers: recipientId, port, files and clientRequestId. Repeating the same batch key recovers the existing request without duplication.
- GET /api/v1/peer-transfers: caller's offers; pending recipients receive no download capabilities.
- POST /api/v1/peer-transfers/:id/decision: accept true/false, recipient only.
- POST /api/v1/peer-transfers/:id/status: sender cancellation or recipient completion/failure.

Pending requests expire after 15 minutes, accepted requests after 24 hours. Limits: 100 files per offer, 1,000 file/recipient pairs per client send operation and 500 active server offers. Retained status is transfer history, not a permanent archive of downloaded files.

## Verification

Automated coverage includes signaling authorization, acceptance/rejection, hidden capabilities before acceptance, expiry, cancellation, endpoint updates, idempotent retries and bounded queues. Android socket tests exercise the real direct file server: approval and token enforcement, path isolation, range validation, resumable byte transfer and final SHA-256 integrity. Shared core tests and phone/TV/VR bridge lint must pass; Unity EditMode tests check integration compilation and existing pointer behavior.

Physical-device QA remains required before claiming end-to-end validation:

1. Update Mac and both selected clients; open file sharing, allow notifications and confirm discovery.
2. Select multiple documents, including an empty file and duplicate filenames; select two recipients.
3. Reject on one recipient and accept on the other. Confirm no incoming bytes or saved files before acceptance or after rejection.
4. Verify saved bytes and names, completion notifications and sender status. Confirm APK files do not open an installer.
5. Interrupt Wi-Fi during a large file, reconnect, and verify resume/integrity or an explicit failure. Cancel during transfer and confirm no unverified file is published.
6. Check notification actions with the store in the background and prompts with notifications disabled. On Quest test hand and controller input; on TV test D-pad focus through progress updates.
7. Verify TV cold-launch visuals with `bash tv/scripts/visual-check.sh <QA-device-serial>` on a populated QA library. Do not claim visual parity or device behavior from build results alone.
