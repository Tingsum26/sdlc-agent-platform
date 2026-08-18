---
name: analyze-http-call-graph
description: Use to extract cross-repository HTTP call relationships from code and OpenAPI when the deterministic scanner is unavailable.
version: "1.0"
---

# Analyze HTTP Call Graph

## When to use
Journey or compatibility analysis needs caller→endpoint edges and no graph scanner is installed.

## Procedure
1. Collect endpoints (controllers/routes/OpenAPI) and clients (Feign/WebClient/RestTemplate/fetch/Retrofit/URLSession).
2. Match by service name, method, and normalized path.
3. Label each edge with confidence and evidence; `AI_INFERRED` for unresolved matches.
4. Submit edges into the journey manifest.

## Output contract
HTTP edges with provenance. This is the Level-0 fallback and must never claim scanner-grade completeness.
