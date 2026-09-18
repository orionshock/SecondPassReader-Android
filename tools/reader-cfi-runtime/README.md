# Reader CFI runtime

TypeScript is the authoritative source for the browser runtime injected by the Android Reader.
Android ships a readable development asset from `app/src/main/assets` and a minified,
same-path override from `app/src/release/assets`. Runtime builds do not require Node.

Regeneration requires Node 24 LTS and npm 11. Run these commands from this directory on Windows:

```powershell
npm.cmd ci
npm.cmd run build
npm.cmd run check
```

`protocol.json` owns only the Kotlin/JavaScript wire contract: the existing runtime version,
global, methods and positional arguments, result fields/discriminators, error codes, and shared
text limits. Calls remain positional JavaScript calls; there is no request envelope. Kotlin
provides named arguments and the bridge orders them using the generated method declaration.
The response remains `{ ok: true, value }` or `{ ok: false, error: { code } }`.
Evaluation/install deadlines and envelope-size enforcement are Kotlin bridge policy, not protocol
error codes. CFI algorithms remain in TypeScript; Readium lifecycle stays in the Android adapter.

`build` regenerates committed Kotlin/TypeScript declarations, checks the entrypoint signatures
and TypeScript result shapes, then produces the readable dev and minified release IIFE assets.
`check` rejects stale declarations, typechecks, compares both assets byte-for-byte, and checks
their installed method surface/version and error envelope in Node.

The root Gradle `readerCfiRuntimeCheck` and `releaseReaderCfiRuntimeCheck` run before Android
`preBuild`. One explicit verifier configuration supplies the manifest, Kotlin and TypeScript
declarations, TypeScript entrypoint, Kotlin adapter, digest sources, and each variant's generated
asset. The checks never invoke Node or generate runtime code. Edit the manifest and adapters
together, then run `npm.cmd run build` and `npm.cmd run check`; hand-edited or stale declarations
fail Gradle. Root `check` runs the buildSrc negative fixtures; use `gradlew.bat -p buildSrc test`
for the same focused suite.

Protocol declarations stay inside the Readium CFI adapter/runtime; Reader domain and UI do not
consume them. The two small declaration renderers (Node regeneration and JVM verification) are
intentionally independent: disagreement fails the checks rather than changing shipped output.

## Pinned Colibrio bundle

`app/src/main/assets/reader/cfi/colibrio-epubcfi-1.1.0.min.js` is a separate pinned third-party dependency, not output from the Reader runtime build. Normal builds verify its SHA-256 and packaged MIT license through `colibrioBundleCheck`.

To reproduce it during dependency maintenance, obtain the exact source revision named by the script and run from the repository root:

```powershell
.\tools\reader-cfi\rebuild-colibrio-bundle.ps1 -SourceCheckout C:\path\to\colibrio-web-epubcfi
.\gradlew.bat colibrioBundleCheck
```

The rebuild script is Windows-only, installs the external checkout's locked npm dependencies, rejects any other revision, and rejects output that does not match the pinned hash.
