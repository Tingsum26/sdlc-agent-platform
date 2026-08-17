import { useCallback, useEffect, useState } from "react";
import { EmptyState, ErrorState, TaskList, type TaskListItem, type TaskStatus } from "@sdlc/ui";
import "@sdlc/ui/tokens.css";
import "./app.css";
import { parsePodCsv } from "./podCsv";

interface ApiTask {
  taskId: string; type: string; status: string;
  scope: { ticketId: string; repositoryAlias: string; targetCommit: string };
  version: number; updatedAt: string;
}

interface Identity { employeeId: string; displayLabel: string; source: string }
interface Diagnostic { provider: string; status: string; observedAt: string; source: string; safeDetail: string }
interface JourneyAnalysis { status: string; totalEdges: number; provenEdges: number; gaps: Array<{ code: string; detail: string }> }

interface Member { principalId: string; employeeId: string; displayLabel: string; role: string; onboardingStatus: string }
interface Roster { revision: number }

const headers = { "Content-Type": "application/json", "X-Demo-User": "developer-1" };
const readinessHeaders = { "Content-Type": "application/json", "X-Demo-User": "PRINCIPAL-EMP-100", "X-Correlation-ID": "fictional-readiness-ui" };
const ref = "0123456789012345678901234567890123456789";
const journeyManifest = {
  schemaVersion: "1.0", journeyId: "ACCOUNT_OPENING", domainId: "CUSTOMER", version: 1,
  repositories: [
    { alias: "API_REPO", role: "API", ref }, { alias: "WEB_REPO", role: "WEB", ref },
    { alias: "IOS_REPO", role: "IOS", ref }, { alias: "ANDROID_REPO", role: "ANDROID", ref },
  ],
  screens: [{ screenId: "OPEN_ACCOUNT", client: "WEB", repositoryAlias: "WEB_REPO" }],
  httpEdges: [{ edgeId: "EDGE_1", caller: "WEB_REPO", apiRepositoryAlias: "API_REPO", method: "POST", normalizedPath: "/accounts",
    requestSchemaRef: "schema/request", responseSchemaRef: "schema/response", commonHeaderRule: "X-Company-Context",
    authenticationClass: "OAUTH", compatibility: "ADDITIVE_WITH_FLAG", provenance: { source: "CODE_SCAN", ref, evidenceId: "EVIDENCE_1" } }],
  releasePolicy: { webApiFirst: true, nativeReleaseTrain: "MONTHLY_NATIVE", compatibilityWindowDays: 60, rollbackRule: "disable AWS toggle" },
  featureFlag: { required: true, provider: "AWS_APP_CONFIG", ownerRole: "PRODUCT_OWNER" },
  e2eOwners: [{ scenario: "HAPPY_PATH", ownerRole: "QA" }],
};

export function App() {
  const [tasks, setTasks] = useState<ApiTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [lastUpdated, setLastUpdated] = useState<string>();
  const [creating, setCreating] = useState(false);
  const [readinessLoading, setReadinessLoading] = useState(false);
  const [identity, setIdentity] = useState<Identity>();
  const [diagnostics, setDiagnostics] = useState<Diagnostic[]>([]);
  const [assignment, setAssignment] = useState<string>();
  const [analysis, setAnalysis] = useState<JourneyAnalysis>();
  const [reportHtml, setReportHtml] = useState<string>();
  const [readinessError, setReadinessError] = useState<string>();
  const [members, setMembers] = useState<Member[]>([]);
  const [rosterImporting, setRosterImporting] = useState(false);
  const [rosterError, setRosterError] = useState<string>();
  const [assigning, setAssigning] = useState(false);
  const [queueAssignment, setQueueAssignment] = useState<{ ticketId: string; principalId: string; reason: string }>();
  const [assignmentError, setAssignmentError] = useState<string>();

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

  const runReadiness = async () => {
    setReadinessLoading(true); setReadinessError(undefined);
    try {
      setIdentity(await json<Identity>("/api/v1/internal-readiness/identity"));
      await json("/api/v1/internal-readiness/pods/import", { method: "POST", body: JSON.stringify({
        journeyId: "ACCOUNT_OPENING", expectedRevision: 0, memberships: [{ membershipId: "MEM-SM-1", employeeId: "EMP-100",
          principalId: "PRINCIPAL-EMP-100", displayLabel: "Fictional Scrum Master", role: "SCRUM_MASTER", journeyId: "ACCOUNT_OPENING",
          active: true, effectiveFrom: "2026-01-01", aliases: [] }],
      }) });
      const assigned = await json<{ ticketId: string; principalId: string }>("/api/v1/internal-readiness/assignments", {
        method: "POST", body: JSON.stringify({ ticketId: "DEMO-123", journeyId: "ACCOUNT_OPENING", requiredRole: "SCRUM_MASTER" }),
      });
      setAssignment(`Assigned ${assigned.ticketId} · ${assigned.principalId}`);
      setDiagnostics(await json<Diagnostic[]>("/api/v1/internal-readiness/integrations"));
      setAnalysis(await json<JourneyAnalysis>("/api/v1/journeys/analyze", { method: "POST", body: JSON.stringify(journeyManifest) }));
      const report = await fetch("/api/v1/journeys/report", { method: "POST", headers: readinessHeaders, body: JSON.stringify(journeyManifest) });
      if (!report.ok) throw new Error(`status ${report.status}`);
      setReportHtml(await report.text());
    } catch { setReadinessError("fictional-readiness-failed"); }
    finally { setReadinessLoading(false); }
  };

  const importRoster = async () => {
    setRosterImporting(true); setRosterError(undefined);
    try {
      const csvResponse = await fetch("/fixtures/pod-roster.csv");
      if (!csvResponse.ok) throw new Error("pod-csv-missing");
      const rows = parsePodCsv(await csvResponse.text());
      const rosterResponse = await fetch("/api/v1/internal-readiness/pods/ACCOUNT_OPENING", { headers: readinessHeaders });
      const expectedRevision = rosterResponse.ok ? ((await rosterResponse.json() as Roster).revision ?? 0) : 0;
      const importResponse = await fetch("/api/v1/internal-readiness/pods/import", {
        method: "POST", headers: readinessHeaders, body: JSON.stringify({
          journeyId: "ACCOUNT_OPENING", expectedRevision,
          memberships: rows.map((row) => ({
            membershipId: `MEM-${row.employeeId}`, employeeId: row.employeeId, principalId: row.principalId,
            displayLabel: row.displayLabel, role: row.role, journeyId: row.journeyId, active: row.active,
            effectiveFrom: row.effectiveFrom, aliases: [],
          })),
        }),
      });
      if (!importResponse.ok) throw new Error("pod-import-failed");
      setMembers(await json<Member[]>("/api/v1/internal-readiness/pods/ACCOUNT_OPENING/members"));
    } catch { setRosterError("pod-roster-import-failed"); }
    finally { setRosterImporting(false); }
  };

  const assignDeveloper = async () => {
    setAssigning(true); setAssignmentError(undefined);
    try {
      const assigned = await json<{ ticketId: string; principalId: string; reason: string }>(
        "/api/v1/internal-readiness/assignments", {
          method: "POST", body: JSON.stringify({ ticketId: "DEMO-123", journeyId: "ACCOUNT_OPENING", requiredRole: "DEVELOPER" }),
        });
      setQueueAssignment(assigned);
    } catch {
      setQueueAssignment(undefined);
      setAssignmentError("pod-assignment-failed");
    } finally { setAssigning(false); }
  };

  async function json<T = unknown>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(path, { ...init, headers: readinessHeaders });
    if (!response.ok) throw new Error(`status ${response.status}`);
    return response.json() as Promise<T>;
  }

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
      <section className="sdlc-card sdlc-stack readiness" aria-labelledby="readiness-title">
        <div className="section-heading"><div><p className="eyebrow">EPIC-DEMO-1 · Account Opening</p><h2 id="readiness-title">Internal-shaped readiness simulation</h2></div>
          <button type="button" disabled={readinessLoading || Boolean(reportHtml)} aria-busy={readinessLoading} onClick={() => void runReadiness()}>
            {readinessLoading ? "Running fictional scenario…" : reportHtml ? "Fictional scenario complete" : "Run fictional readiness scenario"}
          </button></div>
        <p><strong>Evidence boundary:</strong> SIMULATED_PASS and CONTRACT_PASS do not prove company-network connectivity or real repository relationships.</p>
        {readinessError && <ErrorState title="Readiness simulation unavailable" correlationId={readinessError} onRetry={() => void runReadiness()} />}
        {identity && <p className="status-line" role="status">Identity · {identity.employeeId} <span>— {identity.displayLabel} · {identity.source}</span></p>}
        {assignment && <p>{assignment}</p>}
        {diagnostics.length > 0 && <div className="table-scroll"><table><caption>Enterprise adapter observations</caption><thead><tr><th scope="col">Provider</th><th scope="col">Evidence status</th><th scope="col">Source</th><th scope="col">Observed</th></tr></thead>
          <tbody>{diagnostics.map((item) => <tr key={item.provider}><th scope="row">{item.provider}</th><td><span aria-hidden="true">◆ </span>{item.status}</td><td>{item.source}</td><td>{new Date(item.observedAt).toLocaleString()}</td></tr>)}</tbody></table></div>}
        {analysis && <section aria-labelledby="journey-result"><h3 id="journey-result">Journey · {analysis.status}</h3><p>{analysis.provenEdges} of {analysis.totalEdges} HTTP relationships include provenance.</p>
          {analysis.gaps.length > 0 && <ul>{analysis.gaps.map((gap) => <li key={gap.code}><strong>{gap.code}</strong> — {gap.detail}</li>)}</ul>}</section>}
        {reportHtml && <iframe className="journey-report" title="Journey readiness HTML report" sandbox="" srcDoc={reportHtml} />}
      </section>
      <section className="sdlc-card sdlc-stack readiness" aria-labelledby="pod-title">
        <div className="section-heading"><div><p className="eyebrow">M1 · Identity &amp; Pod</p><h2 id="pod-title">Pod roster and assignment</h2></div>
          <button type="button" disabled={rosterImporting} aria-busy={rosterImporting} onClick={() => void importRoster()}>
            {rosterImporting ? "Importing roster…" : "Import fictitious Pod roster (CSV)"}
          </button></div>
        {rosterError && <ErrorState title="Pod roster unavailable" correlationId={rosterError} onRetry={() => void importRoster()} />}
        {assignmentError && <ErrorState title="Assignment unavailable" correlationId={assignmentError} onRetry={() => void assignDeveloper()} />}
        {members.length > 0 && <div className="table-scroll"><table><caption>ACCOUNT_OPENING Pod members</caption>
          <thead><tr><th scope="col">Employee</th><th scope="col">Label</th><th scope="col">Role</th><th scope="col">Onboarding</th></tr></thead>
          <tbody>{members.map((member) => <tr key={member.principalId}>
            <th scope="row">{member.employeeId}</th><td>{member.displayLabel}</td><td>{member.role}</td>
            <td><span aria-hidden="true">◆ </span>{member.onboardingStatus}</td></tr>)}</tbody></table></div>}
        <div className="sdlc-actions">
          <button type="button" disabled={assigning || members.length === 0} aria-busy={assigning} onClick={() => void assignDeveloper()}>
            {assigning ? "Assigning…" : "Assign DEMO-123 to first active DEVELOPER"}
          </button>
        </div>
        {queueAssignment && <p role="status">Assigned {queueAssignment.ticketId} · {queueAssignment.principalId} · {queueAssignment.reason}</p>}
        {queueAssignment && members.some((member) =>
          member.principalId === queueAssignment.principalId && member.onboardingStatus === "NOT_ONBOARDED") &&
          <p className="sdlc-muted">ASSIGNEE_NOT_ONBOARDED — this fictitious assignee has not bound a workbench identity yet.</p>}
      </section>
    </main>
  </>;
}
