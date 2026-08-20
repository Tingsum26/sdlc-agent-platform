function Test-IsTemporalDescendant {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][DateTime]$ParentStartedAt,
        [Parameter(Mandatory = $true)][DateTime]$ChildStartedAt
    )

    # A child must not predate its actual parent. Timestamp normalization is
    # reserved for matching persisted root identities to a live process; using
    # it here would accept a process belonging to a previous PID owner.
    return $ChildStartedAt.ToUniversalTime() -ge $ParentStartedAt.ToUniversalTime()
}

Export-ModuleMember -Function Test-IsTemporalDescendant
