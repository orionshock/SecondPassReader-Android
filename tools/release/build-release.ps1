$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $repositoryRoot
try {
    & npm.cmd ci --prefix tools/reader-cfi-runtime
    if ($LASTEXITCODE -ne 0) { throw 'CFI dependency install failed.' }
    & npm.cmd run check --prefix tools/reader-cfi-runtime
    if ($LASTEXITCODE -ne 0) { throw 'CFI runtime check failed.' }
    & .\gradlew.bat check assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    & node tools/release/package-release.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Release APK verification or packaging failed.' }
} finally {
    Pop-Location
}
