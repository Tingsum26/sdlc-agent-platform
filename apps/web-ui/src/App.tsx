import { useCallback, useEffect, useState } from "react";
import { EmptyState, ErrorState, TaskList, type TaskListItem, type TaskStatus } from "@sdlc/ui";
import "@sdlc/ui/tokens.css";
import "./app.css";

interface ApiTask {
  taskId: string; type: string; status: string;
  scope: { ticketId: string; repositoryAlias: string; targetCommit: string };
  version: number; updatedAt: string;
}

const headers = { "Content-Type": "application/json", "X-Demo-User": "developer-1" };

export function App() {
  const [tasks, setTasks] = useState<ApiTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [lastUpdated, setLastUpdated] = useState<string>();
  const [creating, setCreating] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true); setError(undefined);
    try {
      const response = await fetch("/api/v1/tasks", { headers });
      if (!response.ok) throw new Error(`status ${response.status}`);
      setTasks(await response.json() as ApiTask[]);
      setLastUpdated(new Date().toISOString());
    } catch {
      setError("demo-refresh-failed");
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  const createDemo = async () => {
    setError(undefined); setCreating(true);
    const response = await fetch("/api/v1/workflows/from-ticket", { method: "POST", headers, body: JSON.stringify({
      ticketId: "DEMO-123", repositoryAlias: "REPO_A", targetCommit: "0123456789abcdef0123456789abcdef01234567",
    }) });
    if (!response.ok) { setError("demo-create-failed"); setCreating(false); return; }
    await refresh(); setCreating(false);
  };

  const items: TaskListItem[] = tasks.map((task) => ({
    taskId: task.taskId,
    title: `${task.scope.ticketId} requirement analysis`,
    repositoryAlias: task.scope.repositoryAlias,
    status: task.status as TaskStatus,
    updatedAt: task.updatedAt,
  }));

  return <>
    <a className="skip-link" href="#main">Skip to main content</a>
    <header className="app-header"><div><p className="eyebrow">Local Copilot · human-controlled</p><h1>SDLC Workbench</h1></div>
      <div className="sdlc-actions"><button type="button" disabled={creating} aria-busy={creating} onClick={() => void createDemo()}>{creating ? "Creating DEMO-123…" : "Create DEMO-123"}</button>
        <button type="button" disabled={loading} aria-busy={loading} onClick={() => void refresh()}>{loading ? "Refreshing…" : "Refresh tasks"}</button></div></header>
    <main id="main" className="app-main sdlc-stack">
      <section className="summary-grid" aria-label="Workflow summary">
        <article className="sdlc-card"><span className="metric">{tasks.length}</span><span>Persisted tasks</span></article>
        <article className="sdlc-card"><span className="metric">{tasks.filter((task) => task.status === "COMPLETED").length}</span><span>Completed</span></article>
        <article className="sdlc-card"><span className="metric">Local</span><span>AI execution boundary</span></article>
      </section>
      <p role="status" aria-atomic="true" className="sdlc-muted">{loading ? "Refreshing workflow state…" : lastUpdated ? `State refreshed ${new Date(lastUpdated).toLocaleTimeString()}` : "Not refreshed"}</p>
      {error && <ErrorState title="Workflow demo unavailable" correlationId={error} onRetry={() => void refresh()} />}
      {!loading && !error && (items.length ? <TaskList tasks={items} onOpen={() => undefined} /> :
        <EmptyState title="No workflow tasks yet" detail="Create the fictional DEMO-123 task to begin the public vertical slice." />)}
    </main>
  </>;
}
