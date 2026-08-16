import type { ReactNode } from "react";

export function ReportFrame({ title, graphDescription, children }: {
  title: string; graphDescription?: string; children: ReactNode;
}) {
  return <main className="sdlc-report sdlc-stack" aria-label={title}>
    <header><h1>{title}</h1></header>
    {graphDescription && <section aria-label="Text alternative for relationship graph">
      <h2>Relationship graph — text alternative</h2><p>{graphDescription}</p>
    </section>}
    <div className="sdlc-long-content">{children}</div>
  </main>;
}
