param(
    [Parameter(Mandatory = $true)]
    [string]$SourceCheckout
)

$ErrorActionPreference = "Stop"

$PinnedRevision = "ce0649234f76cc71ab0ed63ea344566f8a083466"
$ExpectedSha256 = "661515025940C5D1AD2F2238FE03455D4616A3B00BD58978EC59823D818B24E0"
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$SourceRoot = (Resolve-Path -LiteralPath $SourceCheckout).Path
$Output = Join-Path $RepositoryRoot "app\src\main\assets\reader\cfi\colibrio-epubcfi-1.1.0.min.js"

$ActualRevision = (& git -C $SourceRoot rev-parse HEAD).Trim()
if ($ActualRevision -ne $PinnedRevision) {
    throw "Expected Colibrio revision $PinnedRevision, found $ActualRevision."
}

Push-Location $SourceRoot
try {
    & npm.cmd ci --ignore-scripts
    if ($LASTEXITCODE -ne 0) {
        throw "npm ci failed."
    }

    & "$SourceRoot\node_modules\.bin\rolldown.cmd" `
        "$SourceRoot\src\index.ts" `
        --format iife `
        --name SecondPassColibrio `
        --platform browser `
        --minify `
        --legal-comments inline `
        --banner "/*! colibrio-web-epubcfi 1.1.0 | MIT | commit $PinnedRevision */" `
        --file $Output
    if ($LASTEXITCODE -ne 0) {
        throw "Colibrio browser bundle generation failed."
    }
} finally {
    Pop-Location
}

$ActualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $Output).Hash
if ($ActualSha256 -ne $ExpectedSha256) {
    throw "Unexpected bundle hash $ActualSha256; expected $ExpectedSha256."
}

Write-Output "Generated $Output"
Write-Output "SHA-256 $ActualSha256"
