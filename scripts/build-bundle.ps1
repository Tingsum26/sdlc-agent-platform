# Builds a versioned, checksummed ZIP of the central customization bundle.
# Usage: powershell -File scripts/build-bundle.ps1 [-Version <semver>] [-OutDir <path>]
param(
    [string]$Version = "2.0.0",
    [string]$OutDir = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'dist')
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$central = Join-Path $repoRoot 'central'

if (-not (Test-Path (Join-Path $central 'manifests\bundle-manifest.json'))) {
    throw 'central/manifests/bundle-manifest.json is missing; run from the repository root worktree.'
}
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "Version must be semver, got: $Version" }

Write-Host 'Validating central bundle with the contracts suite...'
Push-Location $repoRoot
try {
    # pnpm prints a deprecation WARN to stderr; under Windows PowerShell 5.1 that
    # becomes a terminating NativeCommandError when $ErrorActionPreference='Stop',
    # so relax EAP around the validation call and let the exit code decide.
    $previousEAP = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { pnpm --filter @sdlc/contracts test *> $null } finally { $ErrorActionPreference = $previousEAP }
    if ($LASTEXITCODE -ne 0) { throw 'contracts tests failed' }
}
finally { Pop-Location }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$bundleName = "sdlc-central-bundle-$Version"
$staging = Join-Path $OutDir $bundleName
if (Test-Path $staging) { Remove-Item -Recurse -Force $staging }
Copy-Item -Recurse $central $staging

$zip = Join-Path $OutDir "$bundleName.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path (Join-Path $staging '*') -DestinationPath $zip -CompressionLevel Optimal
Remove-Item -Recurse -Force $staging

$hash = (Get-FileHash -Algorithm SHA256 -Path $zip).Hash.ToLowerInvariant()
Set-Content -Path "$zip.sha256" -Value $hash -Encoding ascii
Write-Output "Bundle: $zip"
Write-Output "SHA256: $hash"
