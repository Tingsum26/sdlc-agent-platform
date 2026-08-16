export function EmptyState({ title, detail, action }: { title: string; detail: string; action?: React.ReactNode }) {
  return <section className="sdlc-card sdlc-stack"><h2>{title}</h2><p>{detail}</p>{action}</section>;
}
