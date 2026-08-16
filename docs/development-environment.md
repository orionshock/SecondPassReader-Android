# Development environment

Verified on Windows 10 Pro x64 on 2026-08-16. The project uses `minSdk 31`, `compileSdk 37`, and `targetSdk 37`; see [Android bootstrap](android-bootstrap.md) for rationale and build versions.

## Installed toolchain

| Component | Version | Location |
| --- | --- | --- |
| Android Studio Quail 3 | 2026.1.3, build `AI-261.26222.65.2613.15948027` | `C:\Program Files\Android\Android Studio` |
| JetBrains Runtime/JDK | OpenJDK `25.0.2` | `C:\Program Files\Android\Android Studio\jbr` |
| Android SDK | API 37.0 | `C:\Users\orion\AppData\Local\Android\Sdk` |
| Command-line Tools | `22.0` | `%ANDROID_HOME%\cmdline-tools\latest` |
| Platform-Tools / ADB | `37.0.1` | `%ANDROID_HOME%\platform-tools` |
| Android Emulator | `37.1.11` | `%ANDROID_HOME%\emulator` |

The redundant standalone Platform-Tools 35.0.2 installation formerly at `C:\adb` was removed after the SDK-managed copy was verified.

## User environment

```text
JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
ANDROID_HOME=C:\Users\orion\AppData\Local\Android\Sdk
ANDROID_USER_HOME=C:\Users\orion\.android
ANDROID_AVD_HOME=C:\Users\orion\.android\avd
```

`ANDROID_USER_HOME` and `ANDROID_AVD_HOME` are explicit because the command-line tools did not otherwise discover the existing AVD. `ANDROID_SDK_ROOT` is not set.

The user `Path`, in resolution order, contains:

```text
C:\Program Files\Android\Android Studio\jbr\bin
C:\Users\orion\AppData\Local\Android\Sdk\platform-tools
C:\Users\orion\AppData\Local\Android\Sdk\emulator
C:\Users\orion\AppData\Local\Android\Sdk\cmdline-tools\latest\bin
C:\Users\orion\AppData\Local\Microsoft\WindowsApps
```

Restart VS Code after environment changes so the editor, integrated terminals, extensions, and Codex inherit them.

## Installed SDK packages

```text
build-tools;36.0.0                          36.0.0
build-tools;37.0.0                          37.0.0
cmdline-tools;latest                        22.0
emulator                                    37.1.11
platform-tools                              37.0.1
platforms;android-37.0                      revision 2
sources;android-37.0                        revision 2
system-images;android-36;google_apis;x86_64 revision 7
```

The project compiles against the installed stable Android 17/API 37 platform. The Android 16/API 36 Google APIs image remains the reference runtime; a separate API 36 compilation platform is unnecessary.

## Emulator

AVD: `SecondPass_Tablet_API_36`

- Hardware profile: Pixel Tablet (`pixel_tablet`)
- OS: Android 16 (Baklava), API 36
- Image: Google APIs x86_64
- Location: `C:\Users\orion\.android\avd\SecondPass_Tablet_API_36.avd`
- Acceleration: Windows Hypervisor Platform, installed and usable

Verified while running:

```text
emulator-5554 device
ro.build.version.release=16
ro.build.version.sdk=36
```

## CLI verification

Run from a fresh terminal after restarting VS Code:

```powershell
where.exe java
java -version
javac -version
where.exe adb
adb version
adb devices -l
sdkmanager --version
sdkmanager --list_installed
avdmanager list avd
emulator -version
emulator -accel-check
emulator -list-avds
```

Command-line Tools 22 warns that `sdkmanager` is deprecated in favor of the newer `android sdk` command. `sdkmanager` remains installed and functional for compatibility and verification.

## Project Gradle validation baseline

Use the repository Gradle Wrapper for every local and CI invocation. The wrapper runs on the installed JBR 25 and both modules emit Java 17 bytecode; no standalone JDK or global Gradle installation is required.

- Android Lint: `gradlew.bat lint`
- Kotlin compilation/package: `gradlew.bat assembleDebug`
- Unit tests: `gradlew.bat test`
- Static analysis: `gradlew.bat detekt`
- Formatting/style: `gradlew.bat ktlintCheck` and `gradlew.bat ktlintFormat`

`gradlew.bat check` aggregates module checks, Android Lint, unit tests, detekt, and ktlint. Detekt owns semantic/code-smell rules; ktlint alone owns formatting.
