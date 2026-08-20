function Test-IsTemporalDescendant {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][DateTime]$ParentStartedAt,
        [Parameter(Mandatory = $true)][DateTime]$ChildStartedAt
    )

    # StartTime precision differs between CIM and Process on Windows. Permit
    # only a small clock-resolution tolerance; a materially older process can
    # belong to an earlier owner of a reused parent PID and must never be killed.
    return $ChildStartedAt.ToUniversalTime() -ge $ParentStartedAt.ToUniversalTime().AddMilliseconds(-100)
}

Export-ModuleMember -Function Test-IsTemporalDescendant
