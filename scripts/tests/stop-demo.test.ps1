[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$sourceScript = (Resolve-Path (Join-Path $PSScriptRoot '..\stop-demo.ps1')).Path
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("sdlc-stop-demo-test-" + [Guid]::NewGuid().ToString('N'))
$testScripts = Join-Path $testRoot 'scripts'
$testState = Join-Path $testRoot '.demo'
$sleeper = $null

try {
    New-Item -ItemType Directory -Path $testScripts, $testState | Out-Null
    Copy-Item -LiteralPath $sourceScript -Destination (Join-Path $testScripts 'stop-demo.ps1')
    $sleeper = Start-Process -FilePath 'powershell.exe' `
        -ArgumentList @('-NoProfile', '-Command', 'Start-Sleep -Seconds 30') `
        -WindowStyle Hidden -PassThru

    @{
        repoRoot = $testRoot
        processes = @(
            @{
                name = 'test-sleeper'
                pid = $sleeper.Id
                startedAt = $sleeper.StartTime.ToUniversalTime().ToString('o')
            }
        )
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $testState 'processes.json') -Encoding utf8

    & (Join-Path $testScripts 'stop-demo.ps1') | Out-Null
    if (Get-Process -Id $sleeper.Id -ErrorAction SilentlyContinue) {
        throw 'stop-demo.ps1 did not stop the process recorded with a UTC start time.'
    }

    Write-Output 'stop-demo UTC process identity test passed.'
} finally {
    if ($null -ne $sleeper) {
        Stop-Process -Id $sleeper.Id -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
