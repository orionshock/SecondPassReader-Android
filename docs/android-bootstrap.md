# Android bootstrap

The repository doctrine remains in `AGENTS.md`. This document records the concrete foundation selected when the Android project was created.

## Module topology

```text
:app  --->  :spl-client
```

`:app` owns the Android process, Compose UI, theme, and Android Hilt integration. `:spl-client` is a pure Kotlin/JVM library with no Android, Compose, Hilt Android, application storage, or renderer dependency. The boundary is treated as third-party-consumable so protocol behavior can later be tested and reused without an Android runtime. Reverse dependencies are prohibited.

## Platform and build baseline

- `minSdk 31` (Android 12) is a deliberate modern floor for a private tablet-first client. It avoids legacy compatibility work while retaining support for modern tablets older than the reference device.
- `compileSdk 37` and `targetSdk 37` use the current stable Android 17 SDK. No preview SDK is selected.
- AGP 9 built-in Kotlin for `:app`; the Compose compiler plugin follows the catalog's Kotlin version.
- The current Compose BOM and AndroidX Activity, Lifecycle, and Core dependencies come from the version catalog.
- Hilt uses KSP; coroutine and code-generation versions also come from the version catalog.
- AndroidX Navigation 3 owns Compose-first authenticated routing.
- Coil 3 owns Compose cover loading plus normal memory/disk caching for public cover URLs.
- Room 3 with KSP owns the app-local Home resilience projection and exported schema history.
- detekt and ktlint remain the repository's static-analysis and formatting tools.

`gradle/libs.versions.toml` is the sole source for exact dependency and plugin versions. The committed Gradle wrapper separately owns the exact Gradle distribution. Gradle runs on Android Studio's JBR 25, while project bytecode targets Java 17; a separate JDK 17 installation is not required. The verified workstation toolchain is recorded in [Development environment](development-environment.md).

## Current application baseline

The launcher starts a dark semantic Material 3 composition. `SecondPassApplication` establishes Hilt, `MainActivity` remains the Android host, and `SecondPassApp` switches between connection states and the permanent authenticated shell.

The initial scaffold identity has been replaced by the first real vertical slice. `:spl-client` now exposes SPL discovery, pairing, one-time credential consumption, and authenticated context capabilities; `:app` owns their Android lifecycle, secure persistence, and native connection UI. See [SPL connection architecture](connection-architecture.md).

Authenticated users enter the tablet-first top-app-bar/drawer shell described in [Authenticated application shell](app-shell.md). Project-owned semantic icons and bundled Material Symbols are documented in [Icon vocabulary](icon-vocabulary.md).

## Verification

From the repository root:

```powershell
.\gradlew.bat projects
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat detekt
.\gradlew.bat ktlintCheck
.\gradlew.bat lint
.\gradlew.bat check
```

Android Lint treats warnings as errors. Detekt uses defaults plus one narrow allowance for PascalCase `@Composable` functions. Ktlint owns formatting and has the equivalent Compose naming exception in `.editorconfig`; detekt formatting rules are not enabled.

`staticHygiene` performs non-mutating UTF-8, mojibake-marker, trailing-whitespace, and `git diff --check` validation. The source Python fixer was not retained because its punctuation normalization could alter valid text. The web-only decorative HTML-entity check was not applicable to this client.

The application icon reuses the Second Pass web favicon unchanged inside an Android adaptive-icon wrapper. Android's resource grammar requires that wrapper in a `v26` directory, so the otherwise obsolete version-qualifier lint rule is ignored only for that directory.

## Deliberately deferred

- SPL HTTP/API implementation, pairing, and authentication
- EPUB renderer selection and reading implementation
- caching, persistence, and offline behavior
- additional Gradle modules
- nested feature navigation and complete feature/package topology
- final application versioning, release signing, and distribution policy
- complete design system and light theme
