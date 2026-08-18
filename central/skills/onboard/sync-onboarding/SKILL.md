---
name: sync-onboarding
description: Use to refresh stale repository or Journey onboarding after code merges.
version: "1.0"
---

# Sync Onboarding

## When to use
Onboarding is flagged `POSSIBLY_STALE`/`STALE`, or a merge changed the surface it documents.

## Procedure
1. Compare the documented source commit with the current checkout.
2. Re-verify the affected claims; update only what changed.
3. Recompute the evidence and gap labels.
4. Produce an onboarding update draft or PR description for the human.

## Output contract
Updated onboarding draft with a new verified-against commit, for the human to PR. Never silently trust the old summary.
