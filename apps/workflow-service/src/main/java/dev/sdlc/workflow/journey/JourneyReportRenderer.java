package dev.sdlc.workflow.journey;

public final class JourneyReportRenderer {
    public String render(JourneyAnalysis analysis) {
        StringBuilder gaps = new StringBuilder();
        if (analysis.gaps().isEmpty()) {
            gaps.append("<li class=\"ok\">No structural gaps found.</li>");
        } else {
            for (JourneyGap gap : analysis.gaps()) {
                gaps.append("<li><strong>").append(escape(gap.code())).append("</strong> — ")
                        .append(escape(gap.detail())).append("</li>");
            }
        }
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Journey readiness report</title><style>
                :root{color-scheme:light;--ink:#162033;--muted:#526176;--line:#d7deea;--surface:#f6f8fb;--accent:#2358c4}body{font:16px/1.55 system-ui,sans-serif;color:var(--ink);margin:0;background:var(--surface)}main{max-width:960px;margin:auto;padding:40px 24px}section{background:white;border:1px solid var(--line);border-radius:12px;padding:24px;margin:16px 0}.status{display:inline-flex;gap:8px;align-items:center;color:var(--accent);font-weight:700}.ok{color:#17633a}code{overflow-wrap:anywhere}@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto!important}}</style></head>
                <body><main><h1>Journey readiness: %s</h1><p class="status" aria-label="Evidence status: %s"><span aria-hidden="true">◆</span> Evidence status: %s</p>
                <section aria-labelledby="coverage"><h2 id="coverage">Relationship coverage</h2><p>%d of %d HTTP edges include provenance.</p></section>
                <section aria-labelledby="gaps"><h2 id="gaps">Gaps requiring action</h2><ul>%s</ul></section>
                <section aria-labelledby="notice"><h2 id="notice">Evidence boundary</h2><p>CONTRACT_PASS proves only deterministic structure checks. Internal connectivity and repository truth still require company-network validation.</p></section>
                </main></body></html>
                """.formatted(escape(analysis.manifest().journeyId()), analysis.status(), analysis.status(), analysis.provenEdges(), analysis.totalEdges(), gaps);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
