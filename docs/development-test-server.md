# Development test server

Use a disposable SPL test account for device pairing and opt-in live-server tests.
Keep account credentials outside the repository.

## Debug Android pairing harness

`tools/pair-debug-reader.ps1` pairs an installed debug APK through the public
Connection flow, approves its short-lived pairing using a browser session, and
waits for encrypted credential persistence and authenticated verification.

Supply these environment variables from your local secret storage or shell:

- `SPL_TEST_SERVER_URL`: absolute HTTP(S) URL without embedded credentials.
- `SPL_TEST_USERNAME`: test account username.
- `SPL_TEST_PASSWORD`: test account password.

Alternatively, supply `-ServerUrl`, `-Username`, and `-Password`. Explicit
parameters override environment variables. Missing inputs fail before network
or device operations. The script does not print the password. Prefer environment
variables supplied privately over literal passwords in shell command history.

```powershell
.\gradlew.bat :app:installDebug
.\tools\pair-debug-reader.ps1
```

`adb` must be on PATH. Use `-Serial` when multiple devices are connected. The
optional `-ClientName` selects the debug pairing label; otherwise a fresh label
is generated. `-TimeoutSeconds` controls the wait for device pairing status.

To revoke all active client pairings belonging to the supplied test account:

```powershell
.\tools\pair-debug-reader.ps1 -ClearClientCredentials
```

This revokes every client pairing for that account, not just the selected device.
Use a disposable test account. The helper is manual, Windows-only, and depends
on SPL's browser login/CSRF and pairing-approval endpoints. It is not part of
ordinary Gradle validation.

Live-server instrumentation commands and their mutation scope are listed in
[Repository tooling](repository-tooling.md). They are opt-in and are not part of
ordinary connected tests. Capture device diagnostics with `adb logcat`; keep
local captures out of version control.
