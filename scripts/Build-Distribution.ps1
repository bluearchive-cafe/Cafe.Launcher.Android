[CmdletBinding()]
param(
    [string]$Tag
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
$BuildFilePath = Join-Path $RootDir "app\build.gradle.kts"
$ArtifactsDir = Join-Path $RootDir "artifacts"
$DistributionDir = Join-Path $ArtifactsDir "distribution"
$GeneratedDir = Join-Path $ArtifactsDir "generated"
$ApkPath = Join-Path $RootDir "app\build\outputs\apk\release\app-release.apk"

function Find-ApkSigner {
    $sdkRoot = $env:ANDROID_HOME
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
        $sdkRoot = $env:ANDROID_SDK_ROOT
    }
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
        throw "ANDROID_HOME or ANDROID_SDK_ROOT is required to locate apksigner."
    }

    $buildToolsDir = Join-Path $sdkRoot "build-tools"
    if (-not (Test-Path -LiteralPath $buildToolsDir -PathType Container)) {
        throw "Android build-tools directory was not found: $buildToolsDir"
    }

    $apksignerName = if ($IsWindows) { "apksigner.bat" } else { "apksigner" }
    $candidates = Get-ChildItem -LiteralPath $buildToolsDir -Directory |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName $apksignerName } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Leaf }

    if (-not $candidates -or $candidates.Count -eq 0) {
        throw "apksigner was not found under $buildToolsDir"
    }
    return $candidates[0]
}

function Test-ApkSignature {
    param(
        [Parameter(Mandatory=$true)][string]$ApkPath
    )

    $apksigner = Find-ApkSigner
    $verifyOutput = & $apksigner verify --verbose --print-certs $ApkPath 2>&1
    $exitCode = $LASTEXITCODE
    $verifyOutput | Out-Host
    if ($exitCode -ne 0) {
        throw "APK signature verification failed."
    }

    $expectedCertSha256 = $env:ANDROID_SIGNING_CERT_SHA256
    if (-not [string]::IsNullOrWhiteSpace($expectedCertSha256)) {
        $expectedCertSha256 = ($expectedCertSha256 -replace '[^0-9A-Fa-f]', '').ToLowerInvariant()
        if ($expectedCertSha256.Length -ne 64) {
            throw "ANDROID_SIGNING_CERT_SHA256 must contain a 64-character SHA-256 fingerprint."
        }
        $actualCertSha256 = $null
        foreach ($line in $verifyOutput) {
            if ($line -match 'SHA-256 digest:\s*([0-9A-Fa-f:]+)') {
                $actualCertSha256 = ($Matches[1] -replace ':', '').ToLowerInvariant()
                break
            }
        }
        if ([string]::IsNullOrWhiteSpace($actualCertSha256)) {
            throw "Could not read signer certificate SHA-256 from apksigner output."
        }
        if ($actualCertSha256 -cne $expectedCertSha256) {
            throw "APK signer certificate SHA-256 mismatch."
        }
    }
}

$buildFile = Get-Content -Raw -LiteralPath $BuildFilePath
$versionNameMatches = [regex]::Matches($buildFile, '(?m)^\s*versionName\s*=\s*"([^"]+)"\s*$')
$versionCodeMatches = [regex]::Matches($buildFile, '(?m)^\s*versionCode\s*=\s*(\d+)\s*$')

if ($versionNameMatches.Count -ne 1 -or [string]::IsNullOrWhiteSpace($versionNameMatches[0].Groups[1].Value)) {
    throw "Exactly one non-empty versionName is required in app/build.gradle.kts."
}

if ($versionCodeMatches.Count -ne 1 -or [string]::IsNullOrWhiteSpace($versionCodeMatches[0].Groups[1].Value)) {
    throw "Exactly one numeric versionCode is required in app/build.gradle.kts."
}

$versionName = $versionNameMatches[0].Groups[1].Value.Trim()
$versionCode = $versionCodeMatches[0].Groups[1].Value.Trim()
$Tag = if ([string]::IsNullOrWhiteSpace($Tag)) { "v$versionName" } else { $Tag }

if ($Tag -cne "v$versionName") {
    throw "Tag '$Tag' does not exactly match versionName '$versionName'."
}

Remove-Item -LiteralPath $DistributionDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $GeneratedDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $DistributionDir, $GeneratedDir | Out-Null

$gradleWrapper = if ($IsWindows) {
    Join-Path $RootDir "gradlew.bat"
} else {
    Join-Path $RootDir "gradlew"
}
if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle Wrapper was not found: $gradleWrapper"
}

& $gradleWrapper clean assembleRelease --no-daemon | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "Gradle release build failed."
}

$releaseApks = @(Get-ChildItem -LiteralPath (Join-Path $RootDir "app\build\outputs\apk\release") -Filter "*.apk" -File | Sort-Object Name)
if ($releaseApks.Count -ne 1) {
    throw "Exactly one release APK is required, found $($releaseApks.Count)."
}

$ApkPath = $releaseApks[0].FullName
if ($releaseApks[0].Name -match 'unsigned') {
    throw "Release APK is unsigned: $($releaseApks[0].Name)"
}
Test-ApkSignature -ApkPath $ApkPath

$apkName = "Cafe.Launcher.Android_${Tag}_apk.apk"
$distributionApkPath = Join-Path $DistributionDir $apkName
Copy-Item -LiteralPath $ApkPath -Destination $distributionApkPath -Force

if (-not (Test-Path -LiteralPath $distributionApkPath -PathType Leaf)) {
    throw "Distribution APK was not created: $distributionApkPath"
}

$sha256Name = "$apkName.sha256"
$sha256Path = Join-Path $DistributionDir $sha256Name
$sha256 = (Get-FileHash -LiteralPath $distributionApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText($sha256Path, "$sha256  $apkName`n", (New-Object System.Text.UTF8Encoding $false))

[pscustomobject]@{
    Tag = $Tag
    VersionName = $versionName
    VersionCode = $versionCode
    ApkPath = $distributionApkPath
    Sha256Path = $sha256Path
}
