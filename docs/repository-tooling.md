# Repository tooling

This is the operational index for repository checks, generated assets, device tests, and development harnesses. Commands assume Windows PowerShell at the repository root.

## Normal validation

Run before handing back a normal code change:

```powershell
.\gradlew.bat check assembleDebug
```

`check` runs both modules' JVM tests, detekt, ktlint, Android Lint, text hygiene, architecture-boundary enforcement, deterministic buildSrc checker tests, the pinned Colibrio hash/license check, and both Node-free CFI protocol/runtime checks. `assembleDebug` proves debug packaging and runs the packaged CFI and Colibrio checks through `preBuild`; Gradle reuses those task results when `check` and assembly run together.

This gate does not run instrumentation, a device or emulator, Node, the development server, or release signing. Run `assembleRelease` when release asset selection or release resources change; signing and distribution are not configured repository tooling.

Use focused JVM tests while developing:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.secondpasslibrary.reader.package.ClassName"
.\gradlew.bat :spl-client:test --tests "com.secondpasslibrary.client.package.ClassName"
```

## Tool inventory

| Tool | Class | Owner and purpose | Invocation | Automatic use |
| --- | --- | --- | --- | --- |
| Gradle Wrapper | A | Repository build entry point; pins Gradle for Windows and other hosts | `.\gradlew.bat <tasks>` | All documented Gradle commands |
| `test`, `detekt`, `ktlintCheck` | A | Root lifecycle tasks aggregate the corresponding module checks | `.\gradlew.bat test detekt ktlintCheck` | Included by root `check` |
| `staticHygiene` | A | `buildSrc/StaticHygieneTask`; checks UTF-8, mojibake markers, trailing whitespace, the cancellation-safe capture idiom, and `git diff --check` | `.\gradlew.bat staticHygiene` | Included by root `check` |
| `architectureBoundaryCheck` | B | Pure `ArchitectureBoundaryEvaluator` rules adapted by `ArchitectureBoundaryCheckTask`; protects Reader/Readium/CFI, Connection and Library ownership, and SDK transport seams | `.\gradlew.bat architectureBoundaryCheck` | Included by root `check` |
| `readerCfiRuntimeCheck` | A, B | `buildSrc/ReaderCfiRuntimeCheckTask`; verifies the protocol declarations, adapter method arguments, development asset protocol/version, and source digest without Node | `.\gradlew.bat readerCfiRuntimeCheck` | Root `check` and Android `preBuild` |
| `releaseReaderCfiRuntimeCheck` | A, E | Same verification for the minified release override | `.\gradlew.bat releaseReaderCfiRuntimeCheck` | Root `check` and Android `preBuild` |
| `colibrioBundleCheck` | A, E | `buildSrc/ColibrioBundleCheckTask`; verifies the pinned browser bundle hash and packaged MIT license | `.\gradlew.bat colibrioBundleCheck` | Root `check` and Android `preBuild` |
| Build-logic tests | A | buildSrc tests prove architecture violations and CFI declaration, argument, version, envelope, and generated-constant drift are rejected | `.\gradlew.bat -p buildSrc test` | Included by root `check` through `buildLogicTest` |
| CFI npm scripts | A, E | `tools/reader-cfi-runtime`; `typecheck` checks TypeScript, `build` generates declarations plus readable/minified assets, and `check` performs Node-side type/runtime/staleness checks | `npm.cmd run typecheck`, `npm.cmd run build`, or `npm.cmd run check` in that directory | Manual; Node is not used by Gradle |
| Colibrio rebuild script | E | `tools/reader-cfi/rebuild-colibrio-bundle.ps1`; rebuilds the pinned third-party browser bundle from an exact external checkout revision | See the CFI runtime README | Manual dependency maintenance only |
| Daily Git log | C | `scripts/daily-git-log.ps1`; prints commits and line totals for today or a requested number of prior days | `.\scripts\daily-git-log.ps1 [-PreviousDays N]` | Manual; no build or validation role |
| Debug pairing harness | D | `tools/pair-debug-reader.ps1` plus debug-only `DebugPairingActivity`; pairs an installed debug APK through the public Connection flow | See [Development test server](development-test-server.md) | Manual, Windows-only, network/device dependent |
| Readium CFI test host | D | Debug-only `ReadiumCfiTestActivity`; gives instrumentation tests a real Readium navigator lifecycle | Invoked by Reader CFI instrumentation tests | Built only into debug variants |
| `LiveServerTest` marker | D | Marks instrumentation that contacts or mutates the development server | Select or exclude with instrumentation runner arguments | Live tests also require a test-specific opt-in argument before any server work |

`readerBoundaryCheck` remains a compatibility alias for `architectureBoundaryCheck`; new tooling and
documentation use the broader name.

Test fixtures and support classes under `src/test` and `src/androidTest` are not standalone tools. Their owning suites construct them directly.

There is no separate emulator launcher or device wrapper. Use the SDK commands in [Development environment](development-environment.md), Gradle instrumentation tasks, and `adb` directly. The repository does not provide Unix-script copies of its Windows-only development harnesses.

## Instrumentation

Start the documented emulator, then install or test the debug variant. Exclude live-server tests from ordinary connected runs:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.secondpasslibrary.reader.live.LiveServerTest"
```

Run the non-live Reader package baseline with:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.package=com.secondpasslibrary.reader.reader" "-Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.secondpasslibrary.reader.live.LiveServerTest"
```

For one instrumentation class, replace the package argument with:

```powershell
"-Pandroid.testInstrumentationRunnerArguments.class=com.secondpasslibrary.reader.package.ClassName"
```

Live proofs require an explicitly paired debug installation and a class-specific opt-in. Run only the proof needed:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.secondpasslibrary.reader.reader.readium.cfi.RealSplEpubCfiInteropTest" "-Pandroid.testInstrumentationRunnerArguments.reader.realSplCfiInterop=true"
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.secondpasslibrary.reader.reader.asset.RealSplEpubIntegrityTest" "-Pandroid.testInstrumentationRunnerArguments.reader.realSplIntegrity=true"
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.secondpasslibrary.reader.connection.RealSplConnectionLifecycleTest" "-Pandroid.testInstrumentationRunnerArguments.connection.realSplLifecycle=true"
```

The CFI interoperability proof is read-only. The integrity proof deliberately corrupts and replaces its local EPUB copy. The connection lifecycle proof revokes the active debug client session and should run last; pair or Repair afterward.

## CFI assets

The complete regeneration and verification procedure lives in [Reader CFI runtime](../tools/reader-cfi-runtime/README.md). The short form is:

```powershell
Push-Location tools\reader-cfi-runtime
npm.cmd run build
npm.cmd run check
Pop-Location
.\gradlew.bat readerCfiRuntimeCheck releaseReaderCfiRuntimeCheck
```

Development packages use the readable asset from `app/src/main/assets`. Release packages override the same asset path from `app/src/release/assets` with deterministic minified output.

## Debug pairing

Build and install the debug APK, start an emulator, then run the Windows-only pairing script:

```powershell
.\gradlew.bat :app:installDebug
.\tools\pair-debug-reader.ps1
```

The script reads the development account from [Development test server](development-test-server.md), approves a short-lived pairing through the server's browser-session endpoint, and waits for encrypted credential persistence and authenticated verification. It is not part of Gradle validation.
