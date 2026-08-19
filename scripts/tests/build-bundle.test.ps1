$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$outDir = Join-Path ([System.IO.Path]::GetTempPath()) ("sdlc-bundle-test-" + [guid]::NewGuid().ToString('N'))
try {
    & (Join-Path $repoRoot 'scripts\build-bundle.ps1') -Version '9.9.9' -OutDir $outDir | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "build-bundle failed with exit $LASTEXITCODE" }
    $zip = Join-Path $outDir 'sdlc-central-bundle-9.9.9.zip'
    $hashFile = "$zip.sha256"
    if (-not (Test-Path $zip)) { throw 'zip missing' }
    if (-not (Test-Path $hashFile)) { throw 'sha256 file missing' }
    $actual = (Get-FileHash -Algorithm SHA256 -Path $zip).Hash.ToLowerInvariant()
    $declared = (Get-Content $hashFile).Trim().ToLowerInvariant()
    if ($actual -ne $declared) { throw "hash mismatch: $actual vs $declared" }
    Write-Host 'PASS'
} finally {
    Remove-Item -Recurse -Force $outDir -ErrorAction SilentlyContinue
}
