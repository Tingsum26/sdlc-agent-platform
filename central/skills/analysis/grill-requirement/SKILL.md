---
name: grill-requirement
description: Use before accepting any new or ambiguous Jira requirement; asks one Socratic question at a time and never invents business rules.
version: "1.0"
---

# Grill Requirement

## When to use
A ticket or epic change is vague, high-level, or was written without code awareness.

## Procedure
1. Read the ticket and the code-context evidence pack.
2. Ask exactly ONE question at a time, prioritized by decision impact: observable behavior → actors/data → failure paths → compatibility → rollout → accessibility → manual E2E evidence.
3. Convert answers into acceptance criteria; convert silent gaps into `UNKNOWN` items.
4. Business rules come from BA/PO, technical interpretation from architect — never self-answer.
5. Stop when no critical `UNKNOWN` remains or the human declares the risk accepted.

## Output contract
Requirements interview report: questions, answers, remaining `UNKNOWN` items, and acceptance criteria with source tags.
