import { ReportFrame } from "../ReportFrame.js";

export interface ReportSection { key: string; title: string; body: string }

export function RequirementReport({ title, sections, graphDescription }: {
  title: string; sections: ReportSection[]; graphDescription?: string;
}) {
  return <ReportFrame title={title} {...(graphDescription === undefined ? {} : { graphDescription })}>
    {sections.map((section) => <section key={section.key} className="sdlc-card">
      <h2>{section.title}</h2><p>{section.body}</p>
    </section>)}
  </ReportFrame>;
}
