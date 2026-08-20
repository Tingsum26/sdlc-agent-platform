export interface SdlcStepEvent {
  label: string;
  detail: string;
}

export interface FictionalSdlcResult {
  steps: SdlcStepEvent[];
  auditTrail: TaskAuditEvent[];
  artifactIds: string[];
  ticketId: string;
  stageTypes: string[];
  epic: { epicId: string; status: string; version?: number };
  ticket: { ticketId: string; status: string; evidenceClassification: EvidenceClassification; version: number };
  repoTask: { repoTaskId: string; status: string; evidenceClassification: EvidenceClassification; version: number };
  tasks: Array<{ taskId: string; type: string; status: string; evidenceClassification: EvidenceClassification }>;
}

type EvidenceClassification = "REAL" | "SIMULATED_PASS";

interface TaskAuditEvent {
  action: string;
  detail?: string;
  taskId?: string;
  actorId?: string;
  previousStatus?: string;
  newStatus?: string;
  evidenceClassification: EvidenceClassification;
}

export interface FictionalSdlcInput {
  ticketId: string;
  repositoryAlias: string;
  targetCommit: string;
}

const STAGES: Array<{ type: string; artifactType: string; label: string }> = [
  { type: "REQUIREMENT_ANALYSIS", artifactType: "REQUIREMENT_REPORT", label: "requirement analysis" },
  { type: "DESIGN", artifactType: "DESIGN_REPORT", label: "design" },
  { type: "IMPLEMENTATION", artifactType: "DELIVERY_REPORT", label: "implementation" },
  { type: "TEST_GENERATION", artifactType: "TEST_REPORT", label: "generated tests" },
  { type: "PR_REVIEW", artifactType: "PR_REVIEW_REPORT", label: "PR review" },
  { type: "MANUAL_E2E", artifactType: "MANUAL_E2E_REPORT", label: "manual E2E simulation" },
];

/**
 * Fictional end-to-end SDLC driver for the Web demo.
 *
 * The Workflow Task API accepts an explicit stage type and creates one persisted task per
 * stage. This driver also creates and advances a Repo Task for the ticket, then walks each
 * stage through the real workflow-task state machine:
 *
 *   from-ticket (WAITING_FOR_LOCAL_COPILOT, v0)
 *     → claim (LOCAL_COPILOT_RUNNING, v1)
 *     → results (WAITING_FOR_USER_CONFIRMATION, v2)
 *     → confirm (WAITING_FOR_APPROVAL, v3)
 *     → approvals (COMPLETED for analysis/design; otherwise WAITING_FOR_CI, v4)
 *     → ci (COMPLETED for CI-only stages; WAITING_FOR_MANUAL_E2E for MANUAL_E2E, v5)
 *     → manual-e2e (MANUAL_E2E only, COMPLETED, v6)
 *
 * All data is fictitious; every REST call carries the demo identity header.
 */
export async function runFictionalSdlc(
  fetcher: typeof fetch,
  input: FictionalSdlcInput,
): Promise<FictionalSdlcResult> {
  const base = "/api/v1";
  const steps: SdlcStepEvent[] = [];
  const artifactIds: string[] = [];
  const runId = crypto.randomUUID().replaceAll("-", "").slice(0, 8).toUpperCase();
  const epicId = `EPIC-M7-${runId}`;
  const ticketId = `${input.ticketId}-M7-${runId}`;
  const createdTaskIds: string[] = [];
  const json = async <T>(path: string, init?: RequestInit): Promise<T> => {
    const response = await fetcher(`${base}${path}`, {
      ...init,
      headers: { "Content-Type": "application/json", "X-Demo-User": "SIMULATED-M7-RUNNER" },
    });
    if (!response.ok) throw new Error(`fictional-sdlc: ${path} -> ${response.status}`);
    return response.json() as Promise<T>;
  };

  steps.push({ label: "epic created", detail: `${epicId} · Fictional M7 epic` });
  await json("/epics", { method: "POST", body: JSON.stringify({ epicId, title: "Fictional M7 epic", journeyId: "ACCOUNT_OPENING" }) });
  await json(`/epics/${epicId}/activate`, { method: "POST", body: JSON.stringify({ expectedVersion: 0 }) });
  await json(`/epics/${epicId}/tickets`, { method: "POST", body: JSON.stringify({
    ticketId, channel: "API", evidenceClassification: "SIMULATED_PASS",
  }) });
  const repoTask = await json<{ repoTaskId: string; version: number }>(`/tickets/${ticketId}/repo-tasks`, {
    method: "POST", body: JSON.stringify({ repositoryAlias: input.repositoryAlias, baseCommit: input.targetCommit }),
  });
  steps.push({ label: "repo task created", detail: repoTask.repoTaskId });
  let repoTaskVersion = repoTask.version;
  const advanceRepoTask = async (target: "IN_PROGRESS" | "PR_OPEN" | "MERGED") => {
    const advanced = await json<{ version: number }>(`/repo-tasks/${repoTask.repoTaskId}/advance`, {
      method: "POST", body: JSON.stringify({ expectedVersion: repoTaskVersion, target }),
    });
    repoTaskVersion = advanced.version;
  };
  await advanceRepoTask("IN_PROGRESS");
  let ticketVersion = 0;
  const advanceTicket = async (target: string) => {
    const ticket = await json<{ version: number }>(`/tickets/${ticketId}/advance`, {
      method: "POST", body: JSON.stringify({ expectedVersion: ticketVersion, target }),
    });
    ticketVersion = ticket.version;
  };
  await advanceTicket("IN_ANALYSIS");

  for (const stage of STAGES) {
    const created = await json<{ taskId: string; version: number }>("/workflows/from-ticket", {
      method: "POST",
      body: JSON.stringify({ ticketId, repositoryAlias: input.repositoryAlias,
        targetCommit: input.targetCommit, type: stage.type }),
    });
    createdTaskIds.push(created.taskId);
    steps.push({ label: "task created", detail: created.taskId });

    let taskVersion = created.version;
    const claimed = await json<{ taskId: string; version: number }>(`/tasks/${created.taskId}/claim`, {
      method: "POST", body: JSON.stringify({ expectedVersion: taskVersion, leaseMinutes: 30 }),
    });
    taskVersion = claimed.version;

    const artifact = await json<{ artifactId: string; version: number }>(`/tasks/${created.taskId}/results`, {
      method: "POST", body: JSON.stringify({
        artifactId: `ART-${runId}-${stage.type}`,
        type: stage.artifactType,
        sections: [{
          key: "summary",
          title: stage.label,
          body: stage.type === "MANUAL_E2E"
            ? "SIMULATED_PASS marker only; no QA execution or manual evidence is persisted."
            : `Fictional ${stage.label} report with workflow evidence.`,
        }],
      }),
    });
    artifactIds.push(artifact.artifactId);
    steps.push({ label: `${stage.label} artifact submitted`, detail: artifact.artifactId });
    // `results` transitions the task internally (LOCAL_COPILOT_RUNNING →
    // WAITING_FOR_USER_CONFIRMATION) and returns only the artifact, so bump the
    // tracked version by one to stay in lockstep with the task's real version.
    taskVersion += 1;

    const confirmed = await json<{ taskId: string; version: number }>(`/tasks/${created.taskId}/confirm`, {
      method: "POST", body: JSON.stringify({ expectedVersion: taskVersion }),
    });
    taskVersion = confirmed.version;

    const approved = await json<{ taskId: string; status: string; version: number }>("/approvals", {
      method: "POST", body: JSON.stringify({
        taskId: created.taskId,
        artifactId: artifact.artifactId,
        artifactVersion: artifact.version,
        expectedTaskVersion: taskVersion,
      }),
    });
    taskVersion = approved.version;
    steps.push({ label: `${stage.label} approved`, detail: artifact.artifactId });

    let terminalStatus = approved.status;
    if (approved.status === "WAITING_FOR_CI") {
      const ci = await json<{ taskId: string; status: string; version: number }>(`/tasks/${created.taskId}/ci`, {
        method: "POST", body: JSON.stringify({
          expectedVersion: taskVersion,
          state: "SIMULATED_PASS",
          buildFingerprint: `m7-simulated-build-${stage.type}`,
        }),
      });
      taskVersion = ci.version;
      terminalStatus = ci.status;

      if (ci.status === "WAITING_FOR_MANUAL_E2E") {
        const manual = await json<{ status: string }>(`/tasks/${created.taskId}/manual-e2e`, {
          method: "POST", body: JSON.stringify({
            expectedVersion: taskVersion,
            result: "SIMULATED_PASS",
            actorRole: "SIMULATED_RUNNER",
          }),
        });
        terminalStatus = manual.status;
        steps.push({
          label: "simulated manual E2E transition",
          detail: "SIMULATED_PASS · state transition only",
        });
      }
    }
    if (terminalStatus !== "COMPLETED") {
      throw new Error(`fictional-sdlc: ${stage.type} did not reach its terminal state`);
    }

    // Aggregate state follows the completed work instead of being replayed as
    // a client-authored label list after all stages have already finished.
    if (stage.type === "REQUIREMENT_ANALYSIS") await advanceTicket("WAITING_FOR_APPROVAL");
    if (stage.type === "DESIGN") await advanceTicket("IN_DEVELOPMENT");
    if (stage.type === "TEST_GENERATION") {
      await advanceTicket("PR_OPEN");
      await advanceRepoTask("PR_OPEN");
    }
    if (stage.type === "PR_REVIEW") {
      const ci = await json<{
        ticket: { version: number }; state: string; evidenceClassification: EvidenceClassification;
      }>(`/tickets/${ticketId}/ci`, {
        method: "POST", body: JSON.stringify({ repositoryAlias: input.repositoryAlias, revision: input.targetCommit }),
      });
      if (ci.evidenceClassification !== "SIMULATED_PASS") {
        throw new Error("fictional-sdlc: ticket CI transition is not classified as SIMULATED_PASS");
      }
      ticketVersion = ci.ticket.version;
      steps.push({
        label: "simulated ticket CI transition",
        detail: `${ci.evidenceClassification} · deterministic fake adapter returned ${ci.state}`,
      });
    }
  }
  await advanceRepoTask("MERGED");
  steps.push({ label: "repo task merged", detail: repoTask.repoTaskId });
  for (const target of ["MERGED", "RELEASED", "FLAG_ENABLED", "E2E_VERIFIED"]) {
    await advanceTicket(target);
  }
  const tasks = await json<Array<{
    taskId: string; type: string; status: string; evidenceClassification: EvidenceClassification;
  }>>("/tasks");
  const stageTypes = createdTaskIds.map((taskId) => tasks.find((task) => task.taskId === taskId)?.type ?? "MISSING");
  if (stageTypes.join(",") !== STAGES.map((stage) => stage.type).join(",")) {
    throw new Error("fictional-sdlc: persisted stage types do not match requested stages");
  }
  steps.push({ label: "persisted stage types", detail: stageTypes.join(", ") });
  steps.push({
    label: "simulated release-state path recorded",
    detail: "SIMULATED_PASS · E2E_VERIFIED is workflow state, not QA or release evidence",
  });

  const resume = await json<{
    epic: { epicId: string; status: string; version?: number };
    tickets: Array<{ ticket: { ticketId: string; status: string; evidenceClassification: EvidenceClassification; version: number } }>;
    auditTrail: TaskAuditEvent[];
  }>(`/epics/${epicId}/resume`);
  const persistedTicket = resume.tickets.find((entry) => entry.ticket.ticketId === ticketId)?.ticket;
  const persistedRepoTasks = await json<Array<{
    repoTaskId: string; status: string; evidenceClassification: EvidenceClassification; version: number;
  }>>(`/tickets/${ticketId}/repo-tasks`);
  const persistedRepoTask = persistedRepoTasks.find((entry) => entry.repoTaskId === repoTask.repoTaskId);
  const persistedTasks = createdTaskIds.map((taskId) => tasks.find((task) => task.taskId === taskId)).filter((task): task is NonNullable<typeof task> => Boolean(task));
  const taskAuditsById = new Map(await Promise.all(createdTaskIds.map(async (taskId) => [
    taskId,
    await json<TaskAuditEvent[]>(`/tasks/${taskId}/audit`),
  ] as const)));
  const taskAudit = [...taskAuditsById.values()].flat();
  const ticketAudit = await json<TaskAuditEvent[]>(`/tickets/${ticketId}/audit`);
  if (resume.epic.status !== "ACTIVE" || !persistedTicket || persistedTicket.status !== "E2E_VERIFIED" || !persistedRepoTask || persistedRepoTask.status !== "MERGED"
      || persistedTasks.length !== STAGES.length || persistedTasks.some((task) => task.status !== "COMPLETED")) {
    throw new Error("fictional-sdlc: persisted lifecycle evidence is incomplete");
  }
  const classifiedEvidence = [persistedTicket, persistedRepoTask, ...persistedTasks,
    ...resume.auditTrail, ...taskAudit, ...ticketAudit];
  if (classifiedEvidence.some((item) => item.evidenceClassification !== "SIMULATED_PASS")) {
    throw new Error("fictional-sdlc: persisted evidence classification is not SIMULATED_PASS");
  }
  for (const task of persistedTasks) {
    const states = (taskAuditsById.get(task.taskId) ?? []).map((event) => event.newStatus).filter(Boolean);
    const passedThrough = (...expected: string[]) => expected.every((status, index) => {
      const previousIndex = index === 0 ? -1 : states.indexOf(expected[index - 1]);
      return states.indexOf(status, previousIndex + 1) > previousIndex;
    });
    const valid = task.type === "REQUIREMENT_ANALYSIS" || task.type === "DESIGN"
      ? passedThrough("WAITING_FOR_APPROVAL", "COMPLETED")
          && !states.includes("WAITING_FOR_CI") && !states.includes("WAITING_FOR_MANUAL_E2E")
      : task.type === "MANUAL_E2E"
        ? passedThrough("WAITING_FOR_CI", "WAITING_FOR_MANUAL_E2E", "COMPLETED")
        : passedThrough("WAITING_FOR_CI", "COMPLETED") && !states.includes("WAITING_FOR_MANUAL_E2E");
    if (!valid) throw new Error(`fictional-sdlc: persisted terminal evidence is invalid for ${task.type}`);
  }
  const manualTask = persistedTasks.find((task) => task.type === "MANUAL_E2E");
  const simulatedManualTransition = manualTask && (taskAuditsById.get(manualTask.taskId) ?? []).some((event) =>
    event.previousStatus === "WAITING_FOR_MANUAL_E2E" && event.newStatus === "COMPLETED"
      && event.actorId === "SIMULATED-M7-RUNNER");
  if (!simulatedManualTransition) {
    throw new Error("fictional-sdlc: simulated manual transition audit is missing");
  }
  steps.push({
    label: "stage terminal policy",
    detail: "approval-only 2 · CI-only 3 · simulated manual gate 1",
  });
  steps.push({
    label: "simulation evidence boundary",
    detail: "SIMULATED_PASS · no QA execution or manual evidence persisted",
  });
  steps.push({ label: "persisted evidence classification", detail: "SIMULATED_PASS across ticket, repo task, tasks, and audits" });
  steps.push({ label: "persisted epic state", detail: `${resume.epic.epicId} · ${resume.epic.status}` });
  steps.push({ label: "persisted ticket state", detail: `${persistedTicket.ticketId} · ${persistedTicket.status}` });
  steps.push({ label: "persisted repo task state", detail: `${persistedRepoTask.repoTaskId} · ${persistedRepoTask.status}` });
  steps.push({ label: "persisted service audit", detail: `${resume.auditTrail.length + taskAudit.length + ticketAudit.length} events` });

  return { steps, auditTrail: [...resume.auditTrail, ...ticketAudit, ...taskAudit], artifactIds, ticketId, stageTypes,
    epic: resume.epic, ticket: persistedTicket, repoTask: persistedRepoTask, tasks: persistedTasks };
}
