# Android bootstrap

`AGENTS.md` owns repository doctrine. This document records the Android module and build baseline.

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

## Application baseline

The launcher starts a dark semantic Material 3 composition. `SecondPassApplication` establishes Hilt, `MainActivity` remains the Android host, and `SecondPassApp` switches between connection states and the permanent authenticated shell.

`:spl-client` exposes SPL discovery, pairing, one-time credential consumption, authenticated capabilities, and EPUB downloads. `:app` owns Android lifecycle, secure persistence, account-local storage, native feature UI, and the Readium Reader adapter. See [SPL connection architecture](connection-architecture.md), [Account application shell](app-shell.md), and [Offline and cached product contract](offline-product-contract.md).

Authenticated users enter the tablet-first top-app-bar/drawer shell described in [Authenticated application shell](app-shell.md). Project-owned semantic icons and bundled Material Symbols are documented in [Icon vocabulary](icon-vocabulary.md).

## Verification

Run the normal repository gate from the root:

```powershell
.\gradlew.bat check assembleDebug
```

The exact checks, focused commands, instrumentation split, and generated-asset workflows are documented in [Repository tooling](repository-tooling.md). Android Lint treats warnings as errors. Detekt uses defaults plus one narrow allowance for PascalCase `@Composable` functions. Ktlint owns formatting and has the equivalent Compose naming exception in `.editorconfig`; detekt formatting rules are not enabled.

`staticHygiene` performs non-mutating UTF-8, mojibake-marker, trailing-whitespace, and `git diff --check` validation.

The application icon reuses the Second Pass web favicon inside an Android adaptive-icon wrapper. Android's resource grammar requires the wrapper in a `v26` directory, so the version-qualifier lint rule is ignored only there.

Local alpha release signing is documented in [Repository tooling](repository-tooling.md). Distribution policy and a light theme remain outside this baseline.

The alpha release keeps R8 minification and resource shrinking disabled. Readium's renderer and JavaScript bridge, generated serializers, and dependency reflection have not been exercised as an optimized release artifact. Enabling these requires a separate artifact and device validation pass; broad keep rules would hide missing contracts rather than establish safety.
