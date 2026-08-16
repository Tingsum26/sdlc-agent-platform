# Internal-shaped contract reference

## Evidence vocabulary

| Status | Exact meaning | Permitted promotion |
|---|---|---|
| `SIMULATED_PASS` | deterministic fake behaved as specified | never directly to production proof |
| `CONTRACT_PASS` | schema/structural rules passed | add internal evidence before reliance |
| `INTERNAL_VALIDATION_REQUIRED` | public code cannot prove the fact | run named internal action |
| `BLOCKED` | required input/capability is unavailable | resolve blocker and rerun |

Every diagnostic contains provider, status, `observedAt`, source and a safe detail. Every real internal PASS must reference an evidence ID retained inside the company.

## Enterprise adapter contract

- Providers are restricted to Jira, Confluence, GHES, Jenkins and Splunk.
- Secrets are obtained through `EnterpriseCredentialProvider`; callers never pass or log them.
- Transport enforces configured base URI, connect/request timeout, cancellation and maximum response bytes.
- Errors are normalized to authentication, authorization, rate limit, timeout, network, contract, upstream and cancelled categories. Provider response bodies are not returned to MCP/VSIX.
- The fake transport returns deterministic fictional bodies and always produces `SIMULATED_PASS` diagnostics.
- The Mongo/internal profile returns `INTERNAL_VALIDATION_REQUIRED` until a company-network check supplies proof.

## Identity, Pod and assignment

`principalId` is the stable workflow actor identifier. It is independent of VSIX onboarding and can be created by administrator import. An optional GitHub login may later bind to it. Public MVP authorization is audit-only; identity is not permission.

Pod import is atomic. All rows match one Journey, membership IDs are unique, active employee IDs are unique, effective dates are valid, and `expectedRevision` must match. Assignment chooses an explicit principal when supplied or a deterministic active membership matching `requiredRole`.

## Journey v1

Account Opening requires at least one API, Web, iOS and Android repository. A relationship records caller, API repository, HTTP method/path, request/response schema refs, common header rule, authentication class, compatibility and provenance pinned to a commit ref/evidence ID.

The analyzer emits ordered gaps for missing repositories, request/response schemas, common header, rejected breaking change, provenance, Native release train, required feature flag and E2E owner. A gap-free fictional manifest is `CONTRACT_PASS`; it is not verification that the relationships exist in company code.

For Account Opening the default release policy is Web/API first and Native later with a compatibility window and AWS-managed toggle. For any other Journey the Agent must ask the user for release order and flag provider instead of copying this assumption.

## Data and artifact boundary

Mongo is authoritative for workflow state, audit and structured artifacts. Jira receives concise idempotent summaries and, only when approved and supported, a human-readable attachment. The MVP does not require GridFS or S3 and must not paste a large report into a Jira comment.
