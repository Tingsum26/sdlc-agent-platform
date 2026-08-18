# Reference Sources and License Compliance

Central catalog structure and wording draw on the public open-source
projects below. Status captured 2026-08-18.

## Copied or closely adapted (permissive licenses only)

| Repository | Stars | License | Used for |
|---|---|---|---|
| agentskills/agentskills | 24,418 | Apache-2.0 | SKILL.md layout, description-matching principles, resources layout |
| obra/superpowers | 273,524 | MIT | SKILL.md frontmatter structure and RED/GREEN contract-eval pattern |
| github/spec-kit | 130,050 | MIT | Requirement → spec → plan → task → archive phase boundaries |
| Fission-AI/OpenSpec | 65,348 | MIT | Spec lifecycle concept (propose/apply/archive) |
| github/awesome-copilot | 37,977 | MIT | VS Code `.agent.md` fields (name/description/model/tools/handoffs) |
| arozumenko/sdlc-skills | 19 | MIT | SDLC role division with Copilot-compatible wording |
| addyosmani/agent-skills | 88,253 | MIT | Review/test/TDD skill step breakdowns and checklists |
| Jeffallan/claude-skills | 11,059 | MIT | Full-stack skill step decomposition per topic |
| gotalab/cc-sdd | 3,621 | MIT | Copilot-compatible SDD skill harness structure |
| VoltAgent/awesome-agent-skills | 30,489 | MIT | Catalog organization by lifecycle phase |

## Concept-only (no license file — never copied)

| Repository | Stars | Used for |
|---|---|---|
| anthropics/skills | 170,215 | Agent Skills format concepts only |
| vercel-labs/agent-skills | 30,159 | Frontend engineering checklist concepts only |
| ComposioHQ/awesome-claude-skills | 72,723 | Catalog discovery concepts only |

Compliance rule: no text from the concept-only section appears in this
repository. All copied or adapted material comes from MIT or Apache-2.0
sources only. This file is validated by `packages/contracts` tests.
