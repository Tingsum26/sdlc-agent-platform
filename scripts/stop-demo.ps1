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

function Get-ProcessIdentity([int]$ProcessId) {
    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        return $null
    }

    return [PSCustomObject]@{
        id = $process.Id
        startedAt = $process.StartTime.ToUniversalTime()
    }
}

function Test-ProcessIdentity($Identity) {
    $current = Get-ProcessIdentity -ProcessId ([int]$Identity.id)
    if ($null -eq $current) {
        return $false
    }

    return [Math]::Abs(($current.startedAt - ([DateTime]$Identity.startedAt).ToUniversalTime()).TotalSeconds) -le 5
}

function Get-DescendantProcessIdentities([int]$ParentId) {
    $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$ParentId" -ErrorAction SilentlyContinue)
    foreach ($child in $children) {
        $identity = Get-ProcessIdentity -ProcessId $child.ProcessId
        if ($null -eq $identity) { continue }
        Get-DescendantProcessIdentities -ParentId $identity.id
        $identity
    }
}

function Stop-ProcessIfIdentityMatches($Identity) {
    if (Test-ProcessIdentity -Identity $Identity) {
        Stop-Process -Id ([int]$Identity.id) -Force -ErrorAction SilentlyContinue
    }
}

function Get-IdentityKey($Identity) {
    return "$($Identity.id):$(([DateTime]$Identity.startedAt).ToUniversalTime().Ticks)"
}

function Stop-ProcessTreeSafely($RootIdentity, [int]$TimeoutSeconds = 10) {
    $observedDescendants = @{}
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $rootCurrent = Get-ProcessIdentity -ProcessId ([int]$RootIdentity.id)
        if ($null -eq $rootCurrent -or (Test-ProcessIdentity -Identity $RootIdentity)) {
            foreach ($descendant in @(Get-DescendantProcessIdentities -ParentId ([int]$RootIdentity.id))) {
                $observedDescendants[(Get-IdentityKey -Identity $descendant)] = $descendant
            }
        } else {
            Write-Warning "PID $($RootIdentity.id) was reused while stopping its process tree; no new descendants were inspected."
        }

        foreach ($descendant in @($observedDescendants.Values)) {
            Stop-ProcessIfIdentityMatches -Identity $descendant
        }
        Stop-ProcessIfIdentityMatches -Identity $RootIdentity

        $remaining = @($observedDescendants.Values | Where-Object { Test-ProcessIdentity -Identity $_ })
        if (Test-ProcessIdentity -Identity $RootIdentity) {
            $remaining += $RootIdentity
        }
        if ($remaining.Count -eq 0) {
            return
        }

        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)

    $remaining = @($observedDescendants.Values | Where-Object { Test-ProcessIdentity -Identity $_ })
    if (Test-ProcessIdentity -Identity $RootIdentity) {
        $remaining += $RootIdentity
    }
    if ($remaining.Count -gt 0) {
        throw "Demo processes did not exit before timeout: $($remaining.id -join ', '). State was retained for diagnosis."
    }
}

foreach ($entry in $state.processes) {
    $process = Get-Process -Id ([int]$entry.pid) -ErrorAction SilentlyContinue
    if ($null -eq $process) { continue }
    $recorded = [PSCustomObject]@{
        id = $process.Id
        startedAt = ([DateTime]$entry.startedAt).ToUniversalTime()
    }
    if (-not (Test-ProcessIdentity -Identity $recorded)) {
        Write-Warning "PID $($entry.pid) was reused; it was not stopped."
        continue
    }
    Stop-ProcessTreeSafely -RootIdentity $recorded
    Write-Output "Stopped $($entry.name) (PID $($entry.pid))."
}

Remove-Item -LiteralPath $stateFile -Force
