[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$sourceScript = (Resolve-Path (Join-Path $PSScriptRoot '..\stop-demo.ps1')).Path
$lineageModule = (Resolve-Path (Join-Path $PSScriptRoot '..\process-lineage.psm1')).Path
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("sdlc-stop-demo-test-" + [Guid]::NewGuid().ToString('N'))
$testScripts = Join-Path $testRoot 'scripts'
$testState = Join-Path $testRoot '.demo'
$sleeper = $null
$pidReuseSleeper = $null
$treeRoot = $null
$treeChild = $null
$discoveryFailureSleeper = $null

function Set-DemoProcessState($Entries) {
    @{
        repoRoot = $testRoot
        processes = @($Entries)
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $testState 'processes.json') -Encoding utf8
}

function Assert-ProcessHasExited([int]$ProcessId, [string]$Description) {
    if ($null -ne (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) {
        throw "$Description was still running after stop-demo.ps1 returned."
    }
}

try {
    New-Item -ItemType Directory -Path $testScripts, $testState | Out-Null
    Copy-Item -LiteralPath $sourceScript -Destination (Join-Path $testScripts 'stop-demo.ps1')
    Copy-Item -LiteralPath $lineageModule -Destination (Join-Path $testScripts 'process-lineage.psm1')
    Import-Module (Join-Path $testScripts 'process-lineage.psm1') -Force
    $parentStart = [DateTime]::Parse('2026-08-21T00:00:10Z').ToUniversalTime()
    if (Test-IsTemporalDescendant -ParentStartedAt $parentStart -ChildStartedAt $parentStart.AddMilliseconds(-1)) {
        throw 'Temporal lineage accepted a prospective child that predates its parent identity.'
    }
    if (Test-IsTemporalDescendant -ParentStartedAt $parentStart -ChildStartedAt $parentStart.AddSeconds(-30)) {
        throw 'Temporal lineage accepted a child that predates the parent identity (PID-reuse hazard).'
    }
    if (-not (Test-IsTemporalDescendant -ParentStartedAt $parentStart -ChildStartedAt $parentStart.AddMilliseconds(1))) {
        throw 'Temporal lineage rejected a child created after its parent.'
    }
    $sleeper = Start-Process -FilePath 'powershell.exe' `
        -ArgumentList @('-NoProfile', '-Command', 'Start-Sleep -Seconds 30') `
        -WindowStyle Hidden -PassThru

    Set-DemoProcessState @{
        name = 'test-sleeper'
        pid = $sleeper.Id
        startedAt = $sleeper.StartTime.ToUniversalTime().ToString('o')
    }

    & (Join-Path $testScripts 'stop-demo.ps1') | Out-Null
    Assert-ProcessHasExited -ProcessId $sleeper.Id -Description 'The UTC-identified process'

    $pidReuseSleeper = Start-Process -FilePath 'powershell.exe' `
        -ArgumentList @('-NoProfile', '-Command', 'Start-Sleep -Seconds 30') `
        -WindowStyle Hidden -PassThru
    Set-DemoProcessState @{
        name = 'pid-reuse-candidate'
        pid = $pidReuseSleeper.Id
        startedAt = $pidReuseSleeper.StartTime.ToUniversalTime().AddMinutes(-1).ToString('o')
    }

    & (Join-Path $testScripts 'stop-demo.ps1') | Out-Null
    if ($null -eq (Get-Process -Id $pidReuseSleeper.Id -ErrorAction SilentlyContinue)) {
        throw 'stop-demo.ps1 terminated a PID whose recorded identity did not match.'
    }

    $childPidFile = Join-Path $testRoot 'spawned-child.pid'
    $env:SDLC_STOP_DEMO_CHILD_PID_FILE = $childPidFile
    $treeCommand = '$child = Start-Process -FilePath powershell.exe -ArgumentList @(''-NoProfile'', ''-Command'', ''Start-Sleep -Seconds 30'') -WindowStyle Hidden -PassThru; Set-Content -LiteralPath $env:SDLC_STOP_DEMO_CHILD_PID_FILE -Value $child.Id -NoNewline; Start-Sleep -Seconds 30'
    $treeRoot = Start-Process -FilePath 'powershell.exe' `
        -ArgumentList @('-NoProfile', '-Command', $treeCommand) `
        -WindowStyle Hidden -PassThru

    $childDeadline = (Get-Date).AddSeconds(5)
    do {
        if (Test-Path -LiteralPath $childPidFile) { break }
        Start-Sleep -Milliseconds 50
    } while ((Get-Date) -lt $childDeadline)
    if (-not (Test-Path -LiteralPath $childPidFile)) {
        throw 'The spawned-child lifecycle test could not obtain its child PID.'
    }
    $treeChild = Get-Process -Id ([int](Get-Content -Raw -LiteralPath $childPidFile)) -ErrorAction Stop
    Set-DemoProcessState @{
        name = 'tree-root'
        pid = $treeRoot.Id
        startedAt = $treeRoot.StartTime.ToUniversalTime().ToString('o')
    }

    & (Join-Path $testScripts 'stop-demo.ps1') | Out-Null
    Assert-ProcessHasExited -ProcessId $treeRoot.Id -Description 'The tree root'
    Assert-ProcessHasExited -ProcessId $treeChild.Id -Description 'The spawned child'


    $discoveryFailureSleeper = Start-Process -FilePath 'powershell.exe' `
        -ArgumentList @('-NoProfile', '-Command', 'Start-Sleep -Seconds 30') `
        -WindowStyle Hidden -PassThru
    Set-DemoProcessState @{
        name = 'discovery-failure-candidate'
        pid = $discoveryFailureSleeper.Id
        startedAt = $discoveryFailureSleeper.StartTime.ToUniversalTime().ToString('o')
    }
    $env:SDLC_STOP_DEMO_TEST_FORCE_DESCENDANT_DISCOVERY_FAILURE = '1'
    $discoveryFailedAsExpected = $false
    try {
        & (Join-Path $testScripts 'stop-demo.ps1') | Out-Null
    } catch {
        $discoveryFailedAsExpected = $true
    } finally {
        Remove-Item Env:SDLC_STOP_DEMO_TEST_FORCE_DESCENDANT_DISCOVERY_FAILURE -ErrorAction SilentlyContinue
    }
    if (-not $discoveryFailedAsExpected) {
        throw 'stop-demo.ps1 treated an indeterminate descendant discovery as a successful empty scan.'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $testState 'processes.json'))) {
        throw 'stop-demo.ps1 removed state after descendant discovery failed.'
    }
    if ($null -eq (Get-Process -Id $discoveryFailureSleeper.Id -ErrorAction SilentlyContinue)) {
        throw 'stop-demo.ps1 terminated the root after descendant discovery failed.'
    }

    Write-Output 'stop-demo process identity and tree lifecycle tests passed.'
} finally {
    if ($null -ne $sleeper) {
        Stop-Process -Id $sleeper.Id -Force -ErrorAction SilentlyContinue
    }
    if ($null -ne $pidReuseSleeper) {
        Stop-Process -Id $pidReuseSleeper.Id -Force -ErrorAction SilentlyContinue
    }
    if ($null -ne $treeRoot) {
        Stop-Process -Id $treeRoot.Id -Force -ErrorAction SilentlyContinue
    }
    if ($null -ne $treeChild) {
        Stop-Process -Id $treeChild.Id -Force -ErrorAction SilentlyContinue
    }
    if ($null -ne $discoveryFailureSleeper) {
        Stop-Process -Id $discoveryFailureSleeper.Id -Force -ErrorAction SilentlyContinue
    }
    Remove-Item Env:SDLC_STOP_DEMO_TEST_FORCE_DESCENDANT_DISCOVERY_FAILURE -ErrorAction SilentlyContinue
    Remove-Item Env:SDLC_STOP_DEMO_CHILD_PID_FILE -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
