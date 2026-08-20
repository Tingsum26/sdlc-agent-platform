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
    $process = $null
    try {
        $process = [System.Diagnostics.Process]::GetProcessById($ProcessId)
        if ($process.HasExited) {
            $process.Dispose()
            return $null
        }

        return [PSCustomObject]@{
            id = $process.Id
            startedAt = $process.StartTime.ToUniversalTime()
            handle = $process
        }
    } catch {
        if ($null -ne $process) {
            $process.Dispose()
        }
        return $null
    }
}

function Test-ProcessIdentity($Identity) {
    if ($null -eq $Identity -or $null -eq $Identity.handle) {
        return $false
    }

    try {
        if ($Identity.handle.HasExited) {
            return $false
        }

        return [Math]::Abs(($Identity.handle.StartTime.ToUniversalTime() - ([DateTime]$Identity.startedAt).ToUniversalTime()).TotalSeconds) -le 5
    } catch {
        return $false
    }
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
        try {
            $Identity.handle.Kill()
        } catch {
            # The bound process may have exited between identity verification and termination.
        }
    }
}

function Get-IdentityKey($Identity) {
    return "$($Identity.id):$(([DateTime]$Identity.startedAt).ToUniversalTime().Ticks)"
}

function Test-RootPidCanBeRescanned($RootIdentity) {
    $current = Get-ProcessIdentity -ProcessId ([int]$RootIdentity.id)
    if ($null -eq $current) {
        return $true
    }

    try {
        return [Math]::Abs(($current.startedAt - ([DateTime]$RootIdentity.startedAt).ToUniversalTime()).TotalSeconds) -le 5
    } finally {
        if ($current.handle -is [System.IDisposable]) {
            $current.handle.Dispose()
        }
    }
}

function Dispose-ProcessIdentity($Identity) {
    if ($null -ne $Identity -and $Identity.handle -is [System.IDisposable]) {
        $Identity.handle.Dispose()
    }
}

function Stop-ProcessTreeSafely($RootIdentity, [int]$TimeoutSeconds = 10) {
    $observedDescendants = @{}
    $quiescentRescans = 0
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    try {
        do {
            $rootStillRunning = Test-ProcessIdentity -Identity $RootIdentity
            if ($rootStillRunning -or (Test-RootPidCanBeRescanned -RootIdentity $RootIdentity)) {
                $rescannedDescendants = @(Get-DescendantProcessIdentities -ParentId ([int]$RootIdentity.id))
                foreach ($descendant in $rescannedDescendants) {
                    $observedDescendants[(Get-IdentityKey -Identity $descendant)] = $descendant
                }
                if (-not $rootStillRunning -and $rescannedDescendants.Count -eq 0) {
                    $quiescentRescans++
                } else {
                    $quiescentRescans = 0
                }
            } else {
                throw "PID $($RootIdentity.id) was reused before its process tree became quiescent. State was retained for diagnosis."
            }

            foreach ($descendant in @($observedDescendants.Values)) {
                Stop-ProcessIfIdentityMatches -Identity $descendant
            }
            Stop-ProcessIfIdentityMatches -Identity $RootIdentity

            $remaining = @($observedDescendants.Values | Where-Object { Test-ProcessIdentity -Identity $_ })
            if (Test-ProcessIdentity -Identity $RootIdentity) {
                $remaining += $RootIdentity
            }
            if ($remaining.Count -eq 0 -and $quiescentRescans -ge 2) {
                return
            }

            Start-Sleep -Milliseconds 100
        } while ((Get-Date) -lt $deadline)

        $remaining = @($observedDescendants.Values | Where-Object { Test-ProcessIdentity -Identity $_ })
        if (Test-ProcessIdentity -Identity $RootIdentity) {
            $remaining += $RootIdentity
        }
        throw "Demo processes did not become quiescent before timeout: $($remaining.id -join ', '). State was retained for diagnosis."
    } finally {
        foreach ($descendant in @($observedDescendants.Values)) {
            Dispose-ProcessIdentity -Identity $descendant
        }
        Dispose-ProcessIdentity -Identity $RootIdentity
    }
}

foreach ($entry in $state.processes) {
    $process = Get-ProcessIdentity -ProcessId ([int]$entry.pid)
    if ($null -eq $process) { continue }
    $recorded = [PSCustomObject]@{
        id = $process.id
        startedAt = ([DateTime]$entry.startedAt).ToUniversalTime()
        handle = $process.handle
    }
    if (-not (Test-ProcessIdentity -Identity $recorded)) {
        Write-Warning "PID $($entry.pid) was reused; it was not stopped."
        Dispose-ProcessIdentity -Identity $recorded
        continue
    }
    Stop-ProcessTreeSafely -RootIdentity $recorded
    Write-Output "Stopped $($entry.name) (PID $($entry.pid))."
}

Remove-Item -LiteralPath $stateFile -Force
