# Second Pass Reader

Native Android reader for Second Pass Library (SPL), with tablet interaction,
EPUB reading, reading sessions, and reader-owned marginalia. An SPL server is required.

Repository: **SecondPassReader-Android**. The canonical development branch is `main`.

```powershell
git clone https://github.com/orionshock/SecondPassReader-Android.git
Set-Location SecondPassReader-Android
```

See [Development environment](docs/development-environment.md) for setup,
[Repository tooling](docs/repository-tooling.md) for validation and signed builds,
and [Development test server](docs/development-test-server.md) for optional pairing.

[Actions](https://github.com/orionshock/SecondPassReader-Android/actions) runs the
push test kit and signed build workflows.
[Releases](https://github.com/orionshock/SecondPassReader-Android/releases) provides
versioned APKs when a release has been published. Manual signed builds remain
Actions artifacts.

The Android application ID is `com.secondpasslibrary.reader`; repository and
branch names do not alter installed-app identity.
