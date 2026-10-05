$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $repositoryRoot
try {
    & npm.cmd ci --prefix tools/reader-cfi-runtime
    if ($LASTEXITCODE -ne 0) { throw 'CFI dependency install failed.' }
    & npm.cmd run check --prefix tools/reader-cfi-runtime
    if ($LASTEXITCODE -ne 0) { throw 'CFI runtime check failed.' }
    Write-Host 'Phase A: repository gate starts'
    & node --test tools/release/*.test.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Release script tests failed.' }
    & .\gradlew.bat check
    if ($LASTEXITCODE -ne 0) { throw 'Repository gate failed; release assembly was not started.' }
    Write-Host 'Phase A: repository gate passed'
    Write-Host 'Phase B: release assembly/signing starts'
    & .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Release assembly failed.' }
    Write-Host 'Phase B: release assembly/signing passed'
    Write-Host 'Phase C: package/signer/checksum verification starts'
    & node tools/release/package-release.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Release APK verification or packaging failed.' }
    Write-Host 'Phase C: verified release artifacts staged'
} finally {
    Pop-Location
}
