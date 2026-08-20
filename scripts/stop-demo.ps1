[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$stateFile = Join-Path $repoRoot '.demo\processes.json'
if (-not (Test-Path -LiteralPath $stateFile)) {
    Write-Output 'No demo process state found.'
    exit 0
}

$state = Get-Content -Raw -LiteralPath $stateFile | ConvertFrom-Json
if ($state.repoRoot -ne $repoRoot) {
    throw 'Demo state belongs to a different repository path; refusing to stop processes.'
}

function Get-DescendantProcessIds([int]$ParentId) {
    $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$ParentId" -ErrorAction SilentlyContinue)
    foreach ($child in $children) {
        Get-DescendantProcessIds -ParentId $child.ProcessId
        $child.ProcessId
    }
}

function Wait-ForProcessExit([int[]]$ProcessIds, [int]$TimeoutSeconds = 10) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $remaining = @($ProcessIds | Select-Object -Unique | Where-Object {
            $null -ne (Get-Process -Id $_ -ErrorAction SilentlyContinue)
        })
        if ($remaining.Count -eq 0) {
            return
        }

        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)

    $remaining = @($ProcessIds | Select-Object -Unique | Where-Object {
        $null -ne (Get-Process -Id $_ -ErrorAction SilentlyContinue)
    })
    if ($remaining.Count -gt 0) {
        throw "Demo processes did not exit before timeout: $($remaining -join ', '). State was retained for diagnosis."
    }
}

foreach ($entry in $state.processes) {
    $process = Get-Process -Id ([int]$entry.pid) -ErrorAction SilentlyContinue
    if ($null -eq $process) { continue }
    $recorded = ([DateTime]$entry.startedAt).ToUniversalTime()
    if ([Math]::Abs(($process.StartTime.ToUniversalTime() - $recorded).TotalSeconds) -gt 5) {
        Write-Warning "PID $($entry.pid) was reused; it was not stopped."
        continue
    }
    $descendants = @(Get-DescendantProcessIds -ParentId $process.Id)
    foreach ($id in $descendants) { Stop-Process -Id $id -Force -ErrorAction SilentlyContinue }
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    Wait-ForProcessExit -ProcessIds @($descendants + $process.Id)
    Write-Output "Stopped $($entry.name) (PID $($entry.pid))."
}

Remove-Item -LiteralPath $stateFile -Force
