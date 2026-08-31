[CmdletBinding()]
param(
    [string]$Serial,
    [string]$ServerUrl,
    [string]$Username,
    [string]$Password,
    [string]$ClientName,
    [int]$TimeoutSeconds = 90
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$testServerDocument = Join-Path $repositoryRoot "docs/development-test-server.md"
$packageName = "com.secondpasslibrary.reader"
$activityName = "$packageName/.debug.DebugPairingActivity"
$statusFile = "files/debug-pairing-status.properties"

function Read-TestServerSetting {
    param([Parameter(Mandatory)][string]$Name)

    $match = Select-String -Path $testServerDocument -Pattern "^$Name`:\s+(.+)$"
    if ($null -eq $match) {
        throw "Could not read '$Name' from $testServerDocument."
    }
    return $match.Matches[0].Groups[1].Value.Trim()
}

if ([string]::IsNullOrWhiteSpace($ServerUrl)) {
    $ServerUrl = Read-TestServerSetting "Server"
}
if ([string]::IsNullOrWhiteSpace($Username)) {
    $Username = Read-TestServerSetting "Username"
}
if ([string]::IsNullOrWhiteSpace($Password)) {
    $Password = Read-TestServerSetting "Password"
}
if ([string]::IsNullOrWhiteSpace($ClientName)) {
    $date = Get-Date -Format "yyyyMMdd"
    $suffix = [Guid]::NewGuid().ToString("N").Substring(0, 4).ToUpperInvariant()
    $ClientName = "Second Pass Android debug $date-$suffix"
}

$adbPrefix = @()
if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    $adbPrefix = @("-s", $Serial)
}

function Invoke-Adb {
    param([Parameter(Mandatory)][string[]]$AdbArguments)

    & adb @adbPrefix @AdbArguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed with exit code $LASTEXITCODE."
    }
}

function ConvertTo-AdbShellArgument {
    param([Parameter(Mandatory)][string]$Value)

    return "'" + $Value.Replace("'", "'\''") + "'"
}

function Read-DebugPairingStatus {
    $content = & adb @adbPrefix exec-out run-as $packageName cat $statusFile 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($content -join "`n"))) {
        return @{}
    }
    $values = @{}
    foreach ($line in $content) {
        $parts = $line -split "=", 2
        if ($parts.Count -eq 2) {
            $values[$parts[0]] = $parts[1]
        }
    }
    return $values
}

function Wait-DebugPairingStatus {
    param([Parameter(Mandatory)][string[]]$Expected)

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        $values = Read-DebugPairingStatus
        if ($values.ContainsKey("status")) {
            if ($values.status -eq "failed") {
                throw "Debug pairing failed: $($values.message)"
            }
            if ($Expected -contains $values.status) {
                return $values
            }
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for debug pairing state: $($Expected -join ', ')."
}

function New-AuthenticatedWebSession {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $loginUri = [Uri]::new([Uri]$ServerUrl, "/login/?next=/")
    $loginPage = Invoke-WebRequest -Uri $loginUri -WebSession $session -UseBasicParsing
    $csrfMatch = [regex]::Match(
        $loginPage.Content,
        'name=["'']csrfmiddlewaretoken["''][^>]*value=["'']([^"'']+)["'']'
    )
    if (-not $csrfMatch.Success) {
        throw "The test server login page did not contain a CSRF token."
    }
    $loginResult = Invoke-WebRequest `
        -Uri $loginUri `
        -Method Post `
        -WebSession $session `
        -UseBasicParsing `
        -Body @{
            username = $Username
            password = $Password
            csrfmiddlewaretoken = $csrfMatch.Groups[1].Value
            next = "/"
        } `
        -Headers @{ Referer = $loginUri.AbsoluteUri }
    if ($loginResult.BaseResponse.ResponseUri.AbsolutePath -eq "/login/") {
        throw "The documented development test-server login was rejected."
    }
    return $session
}

function Approve-Pairing {
    param(
        [Parameter(Mandatory)][string]$Code,
        [Parameter(Mandatory)][string]$ApprovedClientName
    )

    $session = New-AuthenticatedWebSession
    $origin = [Uri]$ServerUrl
    $csrfCookie = $session.Cookies.GetCookies($origin) |
        Where-Object Name -eq "csrftoken" |
        Select-Object -First 1
    if ($null -eq $csrfCookie) {
        throw "The authenticated test-server session did not issue a CSRF cookie."
    }
    $decisionUri = [Uri]::new($origin, "/api/v1/client-api/pairing/decision/")
    $response = Invoke-RestMethod `
        -Uri $decisionUri `
        -Method Post `
        -WebSession $session `
        -ContentType "application/json" `
        -Headers @{
            "X-CSRFToken" = $csrfCookie.Value
            Referer = ([Uri]::new($origin, "/profile/client-pairing")).AbsoluteUri
        } `
        -Body (@{
            code = $Code
            action = "approve"
            client_name = $ApprovedClientName
        } | ConvertTo-Json -Compress)
    if ($response.status -ne "approved") {
        throw "The test server did not approve the pairing request."
    }
}

Invoke-Adb -AdbArguments @("shell", "am", "force-stop", $packageName)
Invoke-Adb -AdbArguments @(
    "shell",
    "am",
    "start",
    "-a",
    "com.secondpasslibrary.reader.debug.PAIR_TEST_SERVER",
    "-n",
    $activityName,
    "--es",
    "debug.server_url",
    (ConvertTo-AdbShellArgument $ServerUrl),
    "--es",
    "debug.client_name",
    (ConvertTo-AdbShellArgument $ClientName)
)

$waiting = Wait-DebugPairingStatus -Expected @("waiting", "linked")
if ($waiting.status -eq "waiting") {
    Approve-Pairing -Code $waiting.code -ApprovedClientName $waiting.clientName
}
Wait-DebugPairingStatus -Expected @("linked") | Out-Null
Write-Host "Debug Reader pairing completed."
