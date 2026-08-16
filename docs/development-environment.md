# Development environment

Verified on Windows 10 Pro x64 on 2026-08-16. This repository is intentionally not an Android project yet. Nothing here selects an application architecture, `minSdk`, `targetSdk`, or `compileSdk`.

## Installed toolchain

| Component | Version | Location |
| --- | --- | --- |
| Android Studio Quail 3 | 2026.1.3, build `AI-261.26222.65.2613.15948027` | `C:\Program Files\Android\Android Studio` |
| JetBrains Runtime/JDK | OpenJDK `25.0.2` | `C:\Program Files\Android\Android Studio\jbr` |
| Android SDK | — | `C:\Users\orion\AppData\Local\Android\Sdk` |
| Command-line Tools | `22.0` | `%ANDROID_HOME%\cmdline-tools\latest` |
| Platform-Tools / ADB | `37.0.1` | `%ANDROID_HOME%\platform-tools` |
| Android Emulator | `37.1.11` | `%ANDROID_HOME%\emulator` |

The older standalone Platform-Tools `35.0.2` installation at `C:\adb` was redundant and was removed after the canonical SDK copy was verified.

## User environment

```text
JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
ANDROID_HOME=C:\Users\orion\AppData\Local\Android\Sdk
ANDROID_USER_HOME=C:\Users\orion\.android
ANDROID_AVD_HOME=C:\Users\orion\.android\avd
```

`ANDROID_USER_HOME` and `ANDROID_AVD_HOME` are explicit because the installed command-line tools did not discover the existing AVD from this Windows terminal environment without them. `ANDROID_SDK_ROOT` is not set.

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

The Android 16/API 36 emulator image is installed, but the separate `platforms;android-36` compilation package is not. A modern platform and Build Tools are installed, so this is not a workstation-readiness blocker. Install a platform package later only when the Gradle project deliberately selects its `compileSdk`.

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

The Command-line Tools 22 package warns that `sdkmanager` is deprecated in favor of the newer `android sdk` command. `sdkmanager` remains installed and functional for compatibility and verification.

## Future Gradle validation baseline

When the Android project is created, use its committed Gradle Wrapper for every local and CI invocation. Do not depend on a globally installed Gradle distribution. Recheck the selected Gradle and Android Gradle Plugin versions against JDK 25 compatibility at that point.

The initial quality gate should expose these Gradle/CLI checks:

- Android Lint: `gradlew.bat lint`
- Kotlin compilation: `gradlew.bat compileDebugKotlin` (or the generated variant-equivalent task)
- Unit tests: `gradlew.bat testDebugUnitTest`
- Static analysis: detekt through `gradlew.bat detekt`
- Formatting/style: the Gradle ktlint plugin through `gradlew.bat ktlintCheck` and `gradlew.bat ktlintFormat`

Wire the non-mutating checks into the Gradle `check` lifecycle where practical. Use detekt for semantic/code-smell rules and ktlint for formatting; do not enable detekt's formatting rules at the same time. Pin plugin and wrapper versions when the project is created.
