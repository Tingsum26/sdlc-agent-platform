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

interface EpicState { epicId: string; title: string; status: string; version: number }
interface TicketState { ticketId: string; channel: string; status: string; pendingChangeConfirmation: boolean; version: number }
interface ChangeRequestState { changeRequestId: string; status: string; approvedRoles: string[]; requiredApprovals: number; version: number }
interface ResumeState { epic: EpicState; tickets: Array<{ ticket: TicketState; nextAction: string }>; auditTrail: Array<{ action: string; actorId: string; occurredAt: string }> }

interface JiraDraftState { projectionId: string; ticketId: string; milestoneId: string; summary: string; status: string; attempts: number }
interface CiStateLine { ticketId: string; status: string; detailsUrl: string }

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
  const [epic, setEpic] = useState<EpicState>();
  const [tickets, setTickets] = useState<TicketState[]>([]);
  const [repoTaskLine, setRepoTaskLine] = useState<string>();
  const [dependencyLine, setDependencyLine] = useState<string>();
  const [dependencyError, setDependencyError] = useState<string>();
  const [mergeBlocked, setMergeBlocked] = useState(false);
  const [changeRequest, setChangeRequest] = useState<ChangeRequestState>();
  const [resume, setResume] = useState<ResumeState>();
  const [skipLine, setSkipLine] = useState<string>();
  const [m2Busy, setM2Busy] = useState<string>();
  const [jiraDraft, setJiraDraft] = useState<JiraDraftState>();
  const [ciLine, setCiLine] = useState<CiStateLine>();

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

  const m2Api = async <T,>(path: string, init: RequestInit = {}): Promise<T> => {
    const response = await fetch(path, { ...init, headers: readinessHeaders });
    if (!response.ok) throw new Error(`status ${response.status}`);
    return response.json() as Promise<T>;
  };

  const refreshTickets = async (epicId: string) => {
    setTickets(await m2Api<TicketState[]>(`/api/v1/epics/${epicId}/tickets`));
  };

  const createEpic = async () => {
    setM2Busy("epic");
    try {
      const created = await m2Api<EpicState>("/api/v1/epics", { method: "POST", body: JSON.stringify({
        epicId: "EPIC-M2-1", title: "Fictional M2 epic", journeyId: "ACCOUNT_OPENING",
      }) });
      setEpic(created);
    } catch { setDependencyError("epic-create-failed"); } finally { setM2Busy(undefined); }
  };

  const activateEpic = async () => {
    if (!epic) return;
    setM2Busy("activate");
    try {
      setEpic(await m2Api<EpicState>(`/api/v1/epics/${epic.epicId}/activate`, {
        method: "POST", body: JSON.stringify({ expectedVersion: epic.version }),
      }));
    } catch { setDependencyError("epic-activate-failed"); } finally { setM2Busy(undefined); }
  };

  const attachTickets = async () => {
    if (!epic) return;
    setM2Busy("attach");
    try {
      for (const [ticketId, channel] of [["M2-API-1", "API"], ["M2-WEB-1", "WEB"], ["M2-IOS-1", "IOS"], ["M2-AND-1", "ANDROID"]] as const) {
        await m2Api(`/api/v1/epics/${epic.epicId}/tickets`, { method: "POST", body: JSON.stringify({ ticketId, channel }) });
      }
      await refreshTickets(epic.epicId);
    } catch { setDependencyError("attach-tickets-failed"); } finally { setM2Busy(undefined); }
  };

  const addRepoTask = async () => {
    setM2Busy("repotask");
    try {
      const task = await m2Api<{ repoTaskId: string; status: string }>("/api/v1/tickets/M2-API-1/repo-tasks", {
        method: "POST", body: JSON.stringify({ repositoryAlias: "REPO_A", baseCommit: "0123456789abcdef" }),
      });
      setRepoTaskLine(`${task.repoTaskId} · ${task.status}`);
    } catch { setDependencyError("repo-task-failed"); } finally { setM2Busy(undefined); }
  };

  const addDependency = async () => {
    if (!epic) return;
    setM2Busy("dependency");
    try {
      const dep = await m2Api<{ dependencyId: string; fromTicketId: string; toTicketId: string; status: string }>(
        `/api/v1/epics/${epic.epicId}/dependencies`, {
          method: "POST", body: JSON.stringify({ fromTicketId: "M2-API-1", toTicketId: "M2-WEB-1" }),
        });
      setDependencyLine(`${dep.fromTicketId} → ${dep.toTicketId} · ${dep.status}`);
    } catch { setDependencyError("dependency-failed"); } finally { setM2Busy(undefined); }
  };

  const advanceWebTicket = async () => {
    const ticket = tickets.find((item) => item.ticketId === "M2-WEB-1");
    if (!ticket) return;
    setM2Busy("advance");
    const path: Array<"IN_ANALYSIS" | "WAITING_FOR_APPROVAL" | "IN_DEVELOPMENT" | "PR_OPEN" | "CI_PASSED"> =
      ["IN_ANALYSIS", "WAITING_FOR_APPROVAL", "IN_DEVELOPMENT", "PR_OPEN", "CI_PASSED"];
    try {
      let current = ticket;
      for (const target of path) {
        current = await m2Api<TicketState>(`/api/v1/tickets/${current.ticketId}/advance`, {
          method: "POST", body: JSON.stringify({ expectedVersion: current.version, target }),
        });
      }
      await refreshTickets(epic!.epicId);
    } catch { setDependencyError("advance-failed"); } finally { setM2Busy(undefined); }
  };

  const mergeWebTicket = async () => {
    const ticket = tickets.find((item) => item.ticketId === "M2-WEB-1");
    if (!ticket) return;
    setM2Busy("merge");
    try {
      await m2Api(`/api/v1/tickets/${ticket.ticketId}/advance`, {
        method: "POST", body: JSON.stringify({ expectedVersion: ticket.version, target: "MERGED" }),
      });
      await refreshTickets(epic!.epicId);
      setMergeBlocked(false);
    } catch {
      setMergeBlocked(true); setDependencyError(undefined);
      await refreshTickets(epic!.epicId);
    } finally { setM2Busy(undefined); }
  };

  const resolveDependency = async () => {
    if (!epic) return;
    setM2Busy("resolve"); setDependencyError(undefined);
    try {
      const deps = await m2Api<Array<{ dependencyId: string; version: number }>>(`/api/v1/epics/${epic.epicId}/dependencies`);
      if (deps.length === 0) {
        setDependencyError("no-dependency-to-resolve");
        return;
      }
      const blocking = deps[0];
      await m2Api(`/api/v1/dependencies/${blocking.dependencyId}/resolve`, {
        method: "POST", body: JSON.stringify({ expectedVersion: blocking.version }),
      });
      setDependencyLine(`RESOLVED ${blocking.dependencyId}`);
      setMergeBlocked(false);
    } catch { setDependencyError("dependency-resolve-failed"); } finally { setM2Busy(undefined); }
  };

  const createChangeRequest = async () => {
    if (!epic) return;
    setM2Busy("changerequest");
    try {
      setChangeRequest(await m2Api<ChangeRequestState>(`/api/v1/epics/${epic.epicId}/change-requests`, {
        method: "POST", body: JSON.stringify({
          reason: "Fictional urgent scope change", urgency: "URGENT", description: "Fictional detail",
          affectedTicketIds: ["M2-API-1", "M2-WEB-1"],
        }),
      }));
    } catch { setDependencyError("change-request-failed"); } finally { setM2Busy(undefined); }
  };

  const approveChange = async (role: "BUSINESS_OWNER" | "TECHNICAL_OWNER") => {
    if (!changeRequest) return;
    setM2Busy("approve");
    try {
      const updated = await m2Api<ChangeRequestState>(`/api/v1/change-requests/${changeRequest.changeRequestId}/approve`, {
        method: "POST", body: JSON.stringify({ expectedVersion: changeRequest.version, actorRole: role }),
      });
      setChangeRequest(updated);
      if (epic) await refreshTickets(epic.epicId);
    } catch { setDependencyError("change-approve-failed"); } finally { setM2Busy(undefined); }
  };

  const ackChangeOnApi = async () => {
    const ticket = tickets.find((item) => item.ticketId === "M2-API-1");
    if (!ticket) return;
    setM2Busy("ack");
    try {
      await m2Api(`/api/v1/tickets/${ticket.ticketId}/ack-change`, {
        method: "POST", body: JSON.stringify({ expectedVersion: ticket.version }),
      });
      await refreshTickets(epic!.epicId);
    } catch { setDependencyError("ack-change-failed"); } finally { setM2Busy(undefined); }
  };

  const skipFirstTask = async () => {
    const task = tasks[0];
    if (!task) return;
    setM2Busy("skip");
    try {
      const result = await m2Api<{ attestation: { taskId: string; stageType: string; reason: string } }>(
        `/api/v1/tasks/${task.taskId}/skip`, {
          method: "POST", body: JSON.stringify({
            expectedVersion: task.version, reason: "Fictional fast-track", discussedWith: "Fictional architect",
            actorRole: "DEVELOPER",
          }),
        });
      setSkipLine(`SKIPPED ${result.attestation.taskId} · ${result.attestation.stageType} · ${result.attestation.reason}`);
      await refresh();
    } catch { setDependencyError("skip-failed"); } finally { setM2Busy(undefined); }
  };

  const showResume = async () => {
    if (!epic) return;
    setM2Busy("resume");
    try {
      setResume(await m2Api<ResumeState>(`/api/v1/epics/${epic.epicId}/resume`));
    } catch { setDependencyError("resume-failed"); } finally { setM2Busy(undefined); }
  };

  const draftJiraComment = async () => {
    setM2Busy("jira");
    try {
      setJiraDraft(await m2Api<JiraDraftState>("/api/v1/jira-drafts", {
        method: "POST", body: JSON.stringify({ ticketId: "DEMO-123", milestoneId: "REQ-APPROVED", summary: "Requirement approved" }),
      }));
    } catch { setDependencyError("jira-draft-failed"); } finally { setM2Busy(undefined); }
  };

  const publishJiraComment = async () => {
    if (!jiraDraft) return;
    setM2Busy("jira");
    try {
      setJiraDraft(await m2Api<JiraDraftState>(`/api/v1/jira-drafts/${jiraDraft.projectionId}/publish`, {
        method: "POST",
      }));
    } catch { setDependencyError("jira-publish-failed"); } finally { setM2Busy(undefined); }
  };

  const retryJiraComments = async () => {
    setM2Busy("jira");
    try {
      const updated = await m2Api<JiraDraftState[]>("/api/v1/jira-drafts/retry", { method: "POST", body: "{}" });
      const mine = updated.find((item) => item.projectionId === jiraDraft?.projectionId);
      if (mine) setJiraDraft(mine);
    } catch { setDependencyError("jira-retry-failed"); } finally { setM2Busy(undefined); }
  };

  const recordJenkinsCi = async () => {
    setM2Busy("ci");
    try {
      const result = await m2Api<{ ticket: TicketState; status: string; detailsUrl: string }>("/api/v1/tickets/M2-API-1/ci", {
        method: "POST", body: JSON.stringify({ repositoryAlias: "REPO_A", revision: "0123456789abcdef" }),
      });
      setCiLine({ ticketId: result.ticket.ticketId, status: result.ticket.status, detailsUrl: result.detailsUrl });
      await refreshTickets(epic!.epicId);
    } catch { setDependencyError("ci-record-failed"); } finally { setM2Busy(undefined); }
  };

  const advanceApiTicketToPr = async () => {
    const ticket = tickets.find((item) => item.ticketId === "M2-API-1");
    if (!ticket) return;
    setM2Busy("advance-api");
    const path: Array<"IN_ANALYSIS" | "WAITING_FOR_APPROVAL" | "IN_DEVELOPMENT" | "PR_OPEN"> =
      ["IN_ANALYSIS", "WAITING_FOR_APPROVAL", "IN_DEVELOPMENT", "PR_OPEN"];
    try {
      let current = ticket;
      for (const target of path) {
        current = await m2Api<TicketState>(`/api/v1/tickets/${current.ticketId}/advance`, {
          method: "POST", body: JSON.stringify({ expectedVersion: current.version, target }),
        });
      }
      await refreshTickets(epic!.epicId);
    } catch { setDependencyError("advance-api-failed"); } finally { setM2Busy(undefined); }
  };

  async function json<T = unknown>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(path, { ...init, headers: readinessHeaders });
    if (!response.ok) throw new Error(`status ${response.status}`);
    return response.json() as Promise<T>;
  }

  const m2NextAction = !epic ? "Create EPIC-M2-1"
    : epic.status === "CREATED" ? "Activate epic"
    : tickets.length === 0 ? "Attach four channel tickets"
    : !repoTaskLine ? "Add repo task to M2-API-1"
    : !dependencyLine ? "Add dependency M2-API-1 → M2-WEB-1"
    : tickets.find((item) => item.ticketId === "M2-WEB-1")?.status === "PLANNED" ? "Advance M2-WEB-1 to CI_PASSED"
    : mergeBlocked ? "Resolve dependency"
    : !changeRequest ? "Create emergency change request"
    : changeRequest.status === "DRAFT" ? "Approve change as Business Owner then Technical Owner"
    : tickets.some((item) => item.pendingChangeConfirmation) ? "Acknowledge change on M2-API-1"
    : "Show resume context";

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
      <section className="sdlc-card sdlc-stack readiness" aria-labelledby="m2-title">
        <div className="section-heading"><div><p className="eyebrow">M2 · Three-level workflow</p><h2 id="m2-title">Epic, tickets, and repo tasks</h2></div></div>
        <p className="sdlc-muted" role="status" aria-atomic="true">Next action: {m2NextAction}</p>
        <div className="sdlc-actions">
          <button type="button" disabled={Boolean(m2Busy) || Boolean(epic)} aria-busy={Boolean(m2Busy)} onClick={() => void createEpic()}>Create EPIC-M2-1</button>
          <button type="button" disabled={Boolean(m2Busy) || !epic || epic.status !== "CREATED"} aria-busy={Boolean(m2Busy)} onClick={() => void activateEpic()}>Activate epic</button>
          <button type="button" disabled={Boolean(m2Busy) || !epic || epic.status !== "ACTIVE"} aria-busy={Boolean(m2Busy)} onClick={() => void attachTickets()}>{m2Busy === "attach" ? "Attaching tickets…" : "Attach four channel tickets"}</button>
          <button type="button" disabled={Boolean(m2Busy) || tickets.length === 0} aria-busy={Boolean(m2Busy)} onClick={() => void addRepoTask()}>Add repo task to M2-API-1</button>
          <button type="button" disabled={Boolean(m2Busy) || tickets.length < 2} aria-busy={Boolean(m2Busy)} onClick={() => void addDependency()}>Add dependency M2-API-1 → M2-WEB-1</button>
          <button type="button" disabled={Boolean(m2Busy) || !tickets.some((item) => item.ticketId === "M2-WEB-1" && item.status === "PLANNED")} aria-busy={Boolean(m2Busy)} onClick={() => void advanceWebTicket()}>{m2Busy === "advance" ? "Advancing…" : "Advance M2-WEB-1 to CI_PASSED"}</button>
          <button type="button" disabled={Boolean(m2Busy) || !tickets.some((item) => item.ticketId === "M2-WEB-1" && item.status === "CI_PASSED")} aria-busy={Boolean(m2Busy)} onClick={() => void mergeWebTicket()}>Try merge M2-WEB-1</button>
          <button type="button" disabled={Boolean(m2Busy) || !mergeBlocked} aria-busy={Boolean(m2Busy)} onClick={() => void resolveDependency()}>Resolve dependency</button>
          <button type="button" disabled={Boolean(m2Busy) || !epic || epic.status !== "ACTIVE"} aria-busy={Boolean(m2Busy)} onClick={() => void createChangeRequest()}>Create emergency change request</button>
          <button type="button" disabled={Boolean(m2Busy) || !changeRequest || changeRequest.status !== "DRAFT"} aria-busy={Boolean(m2Busy)} onClick={() => void approveChange("BUSINESS_OWNER")}>Approve change as Business Owner</button>
          <button type="button" disabled={Boolean(m2Busy) || !changeRequest || changeRequest.status !== "DRAFT"} aria-busy={Boolean(m2Busy)} onClick={() => void approveChange("TECHNICAL_OWNER")}>Approve change as Technical Owner</button>
          <button type="button" disabled={Boolean(m2Busy) || !tickets.some((item) => item.ticketId === "M2-API-1" && item.pendingChangeConfirmation)} aria-busy={Boolean(m2Busy)} onClick={() => void ackChangeOnApi()}>Acknowledge change on M2-API-1</button>
          <button type="button" disabled={Boolean(m2Busy) || tasks.length === 0} aria-busy={Boolean(m2Busy)} onClick={() => void skipFirstTask()}>Skip first DEMO-123 task with attestation</button>
          <button type="button" disabled={Boolean(m2Busy) || !epic} aria-busy={Boolean(m2Busy)} onClick={() => void showResume()}>Show resume context</button>
        </div>
        {dependencyError && <ErrorState title="M2 action unavailable" correlationId={dependencyError} onRetry={() => setDependencyError(undefined)} />}
        {epic && <p role="status">Epic {epic.epicId} · {epic.title} · {epic.status} · v{epic.version}</p>}
        {tickets.length > 0 && <div className="table-scroll"><table><caption>EPIC-M2-1 tickets</caption>
          <thead><tr><th scope="col">Ticket</th><th scope="col">Channel</th><th scope="col">Status</th><th scope="col">Change confirmation</th></tr></thead>
          <tbody>{tickets.map((ticket) => <tr key={ticket.ticketId}>
            <th scope="row">{ticket.ticketId}</th><td>{ticket.channel}</td><td>{ticket.status}</td>
            <td>{ticket.pendingChangeConfirmation ? "PENDING_CHANGE_CONFIRMATION" : "—"}</td></tr>)}</tbody></table></div>}
        {repoTaskLine && <p>{repoTaskLine}</p>}
        {dependencyLine && <p>{dependencyLine}</p>}
        {mergeBlocked && <p role="status">MERGE_BLOCKED_BY_DEPENDENCY — resolve the dependency before merging.</p>}
        {changeRequest && <p role="status">Change request {changeRequest.changeRequestId} · {changeRequest.status} · approvals {changeRequest.approvedRoles.length}/{changeRequest.requiredApprovals}</p>}
        {skipLine && <p role="status">{skipLine}</p>}
        {resume && <section aria-labelledby="resume-title"><h3 id="resume-title">Resume context · {resume.epic.status}</h3>
          <ul>{resume.tickets.map((item) => <li key={item.ticket.ticketId}>{item.ticket.ticketId} · {item.ticket.status} → {item.nextAction}</li>)}</ul>
          <p className="sdlc-muted">Audit trail: {resume.auditTrail.map((event) => event.action).join(" · ")}</p></section>}
      </section>
      <section className="sdlc-card sdlc-stack readiness" aria-labelledby="m3-title">
        <div className="section-heading"><div><p className="eyebrow">M3 · Enterprise adapters</p><h2 id="m3-title">Jira projection and Jenkins CI</h2></div></div>
        <div className="sdlc-actions">
          <button type="button" disabled={Boolean(m2Busy) || Boolean(jiraDraft)} aria-busy={Boolean(m2Busy)} onClick={() => void draftJiraComment()}>Draft Jira comment for DEMO-123</button>
          <button type="button" disabled={Boolean(m2Busy) || !jiraDraft || jiraDraft.status !== "JIRA_ARTIFACT_SYNC_PENDING"} aria-busy={Boolean(m2Busy)} onClick={() => void publishJiraComment()}>Confirm publish Jira comment</button>
          <button type="button" disabled={Boolean(m2Busy) || !jiraDraft} aria-busy={Boolean(m2Busy)} onClick={() => void retryJiraComments()}>Retry pending Jira comments</button>
          <button type="button" disabled={Boolean(m2Busy) || !tickets.some((item) => item.ticketId === "M2-API-1" && item.status === "PLANNED")} aria-busy={Boolean(m2Busy)} onClick={() => void advanceApiTicketToPr()}>Advance M2-API-1 to PR_OPEN</button>
          <button type="button" disabled={Boolean(m2Busy) || !tickets.some((item) => item.ticketId === "M2-API-1" && item.status === "PR_OPEN")} aria-busy={Boolean(m2Busy)} onClick={() => void recordJenkinsCi()}>Record Jenkins CI for M2-API-1</button>
        </div>
        {jiraDraft && <p role="status">{jiraDraft.projectionId} · {jiraDraft.milestoneId} · {jiraDraft.status} · attempts {jiraDraft.attempts}</p>}
        {ciLine && <p role="status">{ciLine.ticketId} · {ciLine.status} · {ciLine.detailsUrl}</p>}
      </section>
    </main>
  </>;
}
