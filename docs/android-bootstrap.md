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
- Gradle `9.7.0`, Android Gradle Plugin `9.3.1`, and Kotlin `2.4.10`.
- AGP 9 built-in Kotlin for `:app`; Compose compiler plugin `2.4.10` matches Kotlin.
- Compose BOM `2026.08.00`, Activity Compose `1.13.0`, Lifecycle `2.11.0`, Core KTX `1.19.0`.
- Hilt `2.60.1` with KSP `2.3.10`; coroutines `1.11.0`.
- AndroidX Navigation 3 `1.1.6` owns Compose-first authenticated routing.
- detekt `2.0.0-alpha.6`, ktlint Gradle plugin `14.2.0`, and ktlint `1.8.0`.

Gradle runs on Android Studio's JBR 25.0.2. Gradle 9.7 supports that runtime; project bytecode targets Java 17. A separate JDK 17 installation is not required.

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
