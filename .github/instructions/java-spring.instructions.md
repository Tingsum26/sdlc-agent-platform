---
applyTo: "**/*.java"
---

# Java and Spring instructions

- Keep Java 17 compatibility unless the repository proves a different baseline.
- Preserve controller/domain/adapter boundaries and constructor injection. Keep business rules out of transport adapters.
- For Spring WebFlux/Reactor code, avoid blocking calls, hidden subscriptions, shared mutable state, and unbounded concurrency. Test cancellation, empty/error signals, retries, and context propagation.
- Add backward-compatible request/response behavior. Treat required-field, nullability, enum, error, authentication, pagination, idempotency, and semantic changes as contract risks.
- Write focused unit tests first, then integration/contract tests at affected boundaries. Report commands and actual outcomes.
- Emit structured, correlation-aware logs without request bodies or credentials.
