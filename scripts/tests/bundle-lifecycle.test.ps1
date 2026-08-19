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

    $expectedDirs = @('agents', 'skills', 'instructions', 'hooks', 'mcp', 'policies', 'templates', 'evals', 'manifests')
    foreach ($dir in $expectedDirs) {
        if (-not (Test-Path (Join-Path $extractDir $dir))) { throw "missing dir: $dir" }
    }
    $agentCount = (Get-ChildItem (Join-Path $extractDir 'agents') -Filter '*.agent.md' -File).Count
    $skillCount = (Get-ChildItem (Join-Path $extractDir 'skills') -Filter 'SKILL.md' -Recurse -File).Count
    if ($agentCount -ne 13) { throw "expected 13 agents, got $agentCount" }
    if ($skillCount -ne 33) { throw "expected 33 skills, got $skillCount" }
    if (-not (Test-Path (Join-Path $extractDir 'hooks\hooks-manifest.json'))) { throw 'hooks manifest missing' }
    if (-not (Test-Path (Join-Path $extractDir 'mcp\profiles.json'))) { throw 'profiles missing' }
    if (-not (Test-Path (Join-Path $extractDir 'manifests\bundle-manifest.json'))) { throw 'bundle manifest missing' }

    Write-Output 'PASS'
} finally {
    Remove-Item -Recurse -Force $outDir -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force $extractDir -ErrorAction SilentlyContinue
}
