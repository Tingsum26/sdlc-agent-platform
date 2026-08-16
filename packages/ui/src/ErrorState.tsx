export function ErrorState({ title, correlationId, onRetry }: {
  title: string; correlationId: string; onRetry: () => void;
}) {
  return <section role="alert" className="sdlc-card sdlc-stack">
    <h2>{title}</h2><p>Try again or share correlation ID <code>{correlationId}</code> with support.</p>
    <button type="button" onClick={onRetry}>Retry</button>
  </section>;
}
