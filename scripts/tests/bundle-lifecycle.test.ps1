$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$outDir = Join-Path ([System.IO.Path]::GetTempPath()) ("sdlc-lifecycle-" + [guid]::NewGuid().ToString('N'))
$extractDir = Join-Path ([System.IO.Path]::GetTempPath()) ("sdlc-lifecycle-x-" + [guid]::NewGuid().ToString('N'))
try {
    & (Join-Path $repoRoot 'scripts\build-bundle.ps1') -Version '9.8.1' -OutDir $outDir | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "build-bundle failed with exit $LASTEXITCODE" }
    $zip = Join-Path $outDir 'sdlc-central-bundle-9.8.1.zip'
    if (-not (Test-Path $zip)) { throw 'zip missing' }
    $hashFile = "$zip.sha256"
    if (-not (Test-Path $hashFile)) { throw 'sha256 file missing' }
    $actual = (Get-FileHash -Algorithm SHA256 -Path $zip).Hash.ToLowerInvariant()
    $declared = (Get-Content $hashFile).Trim().ToLowerInvariant()
    if ($actual -ne $declared) { throw "hash mismatch: $actual vs $declared" }

    New-Item -ItemType Directory -Force -Path $extractDir | Out-Null
    Expand-Archive -Path $zip -DestinationPath $extractDir

    # Installer input contract: loadAndValidateBundle requires central/manifests/bundle-manifest.json
    # directly under the selected folder, so the zip root must be central/.
    if (-not (Test-Path (Join-Path $extractDir 'central'))) { throw 'central dir missing' }
    if (-not (Test-Path (Join-Path $extractDir 'central\manifests\bundle-manifest.json'))) { throw 'bundle manifest missing' }
    $agentCount = (Get-ChildItem (Join-Path $extractDir 'central\agents') -Filter '*.agent.md' -File).Count
    $skillCount = (Get-ChildItem (Join-Path $extractDir 'central\skills') -Filter 'SKILL.md' -Recurse -File).Count
    if ($agentCount -ne 13) { throw "expected 13 agents, got $agentCount" }
    if ($skillCount -ne 33) { throw "expected 33 skills, got $skillCount" }
    if (-not (Test-Path (Join-Path $extractDir 'central\hooks\hooks-manifest.json'))) { throw 'hooks manifest missing' }
    if (-not (Test-Path (Join-Path $extractDir 'central\mcp\profiles.json'))) { throw 'profiles missing' }

    Write-Output 'PASS'
} finally {
    Remove-Item -Recurse -Force $outDir -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force $extractDir -ErrorAction SilentlyContinue
}
