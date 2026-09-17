# Development test server

This test-environment account is available for manual Android client verification. Its credentials are intentionally repository-visible and must never be reused for production or personal accounts.

```text
Server:   https://secondpasslibrary.zcaprica.duckdns.org/
Username: incididunt
Password: [REMOVED TEST CREDENTIAL]
```

Do not embed these values in production application source, build configuration, logs, screenshots, or release artifacts. The debug harness below is the sole automated-login exception: it reads this development-only document at runtime, while the Android client still discovers the server and obtains its bearer credential through the normal pairing flow.

## Debug Android pairing harness

The debug APK exposes a pairing activity that drives the normal SPL-client discovery, pairing, credential consumption, and secure persistence path. It is declared only in `app/src/debug` and is absent from release builds. The harness is Windows PowerShell tooling and requires `adb`, a running emulator, an installed debug APK, and network access to the test server.

Run:

```powershell
.\gradlew.bat :app:installDebug
.\tools\pair-debug-reader.ps1
```

The script reads the test values above, quotes intent extras for Windows `adb`, starts the debug-only activity, approves the short-lived request through an authenticated web session, and waits until the app has persisted and verified the issued credential. Each run uses a sortable, collision-resistant client name such as `Second Pass Android debug 20260831-A3F2`. Parameters can override the documented server, account, device serial, client name, and timeout.

To revoke every currently active client pairing owned by the development account through the server's browser-session-only test seam, run:

```powershell
.\tools\pair-debug-reader.ps1 -ClearClientCredentials
```

This intentionally leaves Android's locally stored credential untouched so authentication-loss and recovery behavior can be exercised from outside the app. The endpoint is idempotent and does not revoke the authenticated browser session.

Live-server instrumentation commands and their mutation scope are listed in [Repository tooling](repository-tooling.md). They are opt-in and are not part of ordinary connected tests.
