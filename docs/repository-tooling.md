# Repository tooling

This is the operational index for repository checks, generated assets, device tests, and development harnesses. Commands assume Windows PowerShell at the repository root.

## CI validation lanes

Gitea Actions is the preferred source of complete build artifacts and repository-wide validation. Local checks are for focused development feedback; avoid rebuilding an APK locally when a successful CI artifact already exists.

Every normal branch push runs `.github/workflows/android-push-check.yml`. Its target is under ten minutes on the Gitea runner. It runs the Node CFI typecheck/generated-asset comparison followed by:

```powershell
.\gradlew.bat check assembleDebug
```

`check` runs both modules' JVM tests, detekt, ktlint, Android Lint, text hygiene, architecture-boundary enforcement, deterministic buildSrc checker tests, the pinned Colibrio hash/license check, and both Node-free CFI protocol/runtime checks. `assembleDebug` proves debug packaging and runs the packaged CFI and Colibrio checks through `preBuild`; Gradle reuses those task results when `check` and assembly run together.

The push lane does not reconstruct signing material, assemble a release APK, publish artifacts, run instrumentation or physical-device suites, or create a Gitea Release. It uses `actions/cache@v4.1.2` for downloaded Gradle modules and wrapper distributions only.

Manually dispatch `.github/workflows/android-signed-dev-build.yml` for a signed dogfood build. The signed lane runs the CFI check and full Gradle `check assembleRelease`, reconstructs the permanent keystore only in a private temporary directory, and verifies the APK package, declared version, permanent signer, and checksum. It uploads the versioned APK and checksum as workflow artifacts and never creates a Gitea Release.

A pushed `v*` tag runs `.github/workflows/android-release-build.yml`. It uses the same signed build helper, additionally requires the tag to match the declared `versionName`, and publishes the existing Gitea prerelease with the APK and checksum. It refuses to overwrite an existing release.

The version policy is deliberately simple: a developer changes `versionCode` and `versionName` together when intentionally advancing the app version. All manual dogfood builds for that declared version use the same identity, and the matching tag publishes that identity. CI does not derive versions from run numbers.

## Local alpha release signing

The permanent application ID is `com.secondpasslibrary.reader`. Set `versionCode` and `versionName` together in `app/build.gradle.kts` when intentionally advancing the distributed app version. The first alpha is code `1`, name `0.1.0-alpha.1`; later manual dogfood builds may retain that identity until the next intentional version change. The release build is non-debuggable, with minification and resource shrinking disabled.

Generate a release key on a trusted machine using `keytool` from the JDK (choose a strong password when prompted):

```powershell
keytool -genkeypair -v -storetype PKCS12 -keystore SecondPassReader-release.p12 -alias secondpassreader -keyalg RSA -keysize 4096 -validity 10000
```

Store the keystore securely outside the repository. Configure these names as environment variables or in the local user Gradle properties file (`$HOME/.gradle/gradle.properties`), without committing their values:

| Name | Value |
| --- | --- |
| `SECOND_PASS_RELEASE_STORE_FILE` | Absolute path to the release keystore; a relative path resolves from the repository root. In `gradle.properties` on Windows, use forward slashes (for example, `C:/secrets/SecondPassReader-release.p12`) because single backslashes are property escapes. |
| `SECOND_PASS_RELEASE_STORE_PASSWORD` | Keystore password |
| `SECOND_PASS_RELEASE_KEY_ALIAS` | Key alias, `secondpassreader` for the command above |
| `SECOND_PASS_RELEASE_KEY_PASSWORD` | Key password |

Build with `.\gradlew.bat :app:assembleRelease`. Missing signing settings fail the release build. The APK uses AGP's standard `app-release.apk` name under `app/build/outputs/apk/release/`. Preserve the keystore and passwords: future APK upgrades for this application ID require the same signing key.

For a distribution-ready local artifact, run `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\release\build-release.ps1` from the repository root. It runs the CFI Node check and Gradle `check assembleRelease`, verifies the APK against the permanent release certificate, and writes `build/release-artifacts/SecondPassReader-<versionName>.apk` plus its `.apk.sha256` file. It reads the same user-local Gradle signing properties; no base64 step is needed locally. The permanent release key currently uses alias `secondpass-reader-release`, which differs from the example key-generation command above.

The release APK includes `app/src/main/assets/licenses/open_source_licenses.txt`, exposed from the main About destination. It covers the resolved runtime families and bundled assets, including Readium Toolkit/CSS BSD 3-Clause, Colibrio/jsoup/SLF4J MIT, the Readium font terms, Material Symbols and other Apache 2.0 dependencies, Jakarta's notice, and the desugared JDK library GPL 2 with Classpath exception on designated files. Recheck the notice inventory when release dependencies or bundled reader assets change.

## Gitea signed build

Manually dispatch `.github/workflows/android-signed-dev-build.yml` in Gitea Actions for a signed dogfood build. It uses the same `com.secondpasslibrary.reader` application ID, declared Gradle version, and permanent release key as a tagged release, so its APK can update an installed release build without clearing application data. It runs the full gate, signs and verifies the APK, and retains workflow artifacts without creating a Gitea Release. The job uses the `ubuntu-latest` runner label, JDK 25, Node 24, Android SDK platform `android-37.0`, and build-tools `37.0.0`. It caches only downloaded Gradle dependencies and the wrapper distribution. The runner needs network access to fetch build dependencies and setup actions.

Configure these Gitea repository secrets: `ANDROID_RELEASE_KEYSTORE_B64`, `SECOND_PASS_RELEASE_STORE_PASSWORD`, `SECOND_PASS_RELEASE_KEY_ALIAS`, and `SECOND_PASS_RELEASE_KEY_PASSWORD`. The base64 secret contains the existing permanent PKCS12 keystore. The job decodes it to a private temporary file outside the repository, passes that path as `SECOND_PASS_RELEASE_STORE_FILE`, and removes the file after packaging. Do not create a `SECOND_PASS_RELEASE_STORE_FILE` repository secret. Release publication uses Gitea's built-in job token with code read and releases write permission; no additional API token is configured.

Both paths reject an APK unless `apksigner` verifies it and the signer SHA-256 certificate fingerprint equals `56:E8:DE:B6:C1:C9:F2:3E:00:71:F9:91:7C:21:D0:C8:8E:28:EC:D0:AA:CF:6C:FF:8A:97:B5:C3:7D:B1:58:A8`. The packaging check reads the application ID, version code, and version name from the built APK and uses the version name for artifact naming.

To publish an alpha, first set the intended `versionName` and `versionCode` in `app/build.gradle.kts` (increase the code for each later APK), run the local helper or a manual workflow build, and commit/push the release-ready changes. Then create and push a tag matching the APK versionName exactly:

```powershell
git tag v<versionName>
git push origin v<versionName>
```

The tag-triggered workflow builds and verifies the same signed APK, rejects a tag/versionName mismatch, retains the two workflow artifacts, and publishes a Gitea prerelease titled `Second Pass Reader <versionName>` with the APK and its `.apk.sha256` file attached. Release notes contain the APK version/code, minimum Android API level, install/update guidance, checksum filename, and SPL server requirement. A run for a tag with an existing release fails instead of replacing it. Do not move or reuse a published release tag for a different APK.

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
