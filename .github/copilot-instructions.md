# Local Copilot SDLC platform instructions

- Treat Workflow Service state and exact artifact versions as authoritative across restarts; inspect them through Workflow MCP before acting.
- All AI reasoning runs only in the user's interactive VS Code GitHub Copilot session. Never assume a remote, scheduled, background, Jenkins, or Workflow Service agent.
- Separate observed code/source evidence, decisions, assumptions, and open questions. Jira text alone is not a complete requirement.
- Stop at human confirmation and approval gates. Never push, open/merge a PR, update Jira, or skip a stage without explicit recorded authorization.
- Preserve API compatibility for older clients. Use expand-migrate-contract, safe defaults, consumer/version analysis, contract tests, feature flags, rollback, and removal criteria.
- Generate automated tests and explicit manual E2E cases. A manual result requires actor role, time, build fingerprint, actual result, and evidence or waiver.
- Never store or print tokens, passwords, cookies, authorization headers, company source, or private business data in generated fixtures or logs.
