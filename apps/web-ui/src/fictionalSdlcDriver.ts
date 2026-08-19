export interface SdlcStepEvent {
  label: string;
  detail: string;
}

export interface FictionalSdlcResult {
  steps: SdlcStepEvent[];
  auditTrail: SdlcStepEvent[];
  artifactIds: string[];
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
];

/**
 * Fictional end-to-end SDLC driver for the Web demo.
 *
 * The Workflow Task API is single-stage: POST /workflows/from-ticket always creates a
 * REQUIREMENT_ANALYSIS task, keyed by idempotency key `ticket:<ticketId>:<targetCommit>`.
 * This driver therefore creates a NEW task per SDLC stage, each with a distinct
 * targetCommit, and walks each task through the real state machine:
 *
 *   from-ticket (WAITING_FOR_LOCAL_COPILOT, v0)
 *     → claim (LOCAL_COPILOT_RUNNING, v1)
 *     → results (WAITING_FOR_USER_CONFIRMATION, v2)
 *     → confirm (WAITING_FOR_APPROVAL, v3)
 *     → approvals (WAITING_FOR_CI, v4)
 *     → ci (WAITING_FOR_MANUAL_E2E, v5)
 *     → manual-e2e (COMPLETED, v6)
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
  const json = async <T>(path: string, init?: RequestInit): Promise<T> => {
    const response = await fetcher(`${base}${path}`, {
      ...init,
      headers: { "Content-Type": "application/json", "X-Demo-User": "PRINCIPAL-EMP-100" },
    });
    if (!response.ok) throw new Error(`fictional-sdlc: ${path} -> ${response.status}`);
    return response.json() as Promise<T>;
  };

  steps.push({ label: "epic created", detail: "EPIC-M7-1 · Fictional M7 epic" });
  await json("/epics", { method: "POST", body: JSON.stringify({ epicId: "EPIC-M7-1", title: "Fictional M7 epic", journeyId: "ACCOUNT_OPENING" }) });
  await json("/epics/EPIC-M7-1/activate", { method: "POST", body: JSON.stringify({ expectedVersion: 0 }) });
  await json("/epics/EPIC-M7-1/tickets", { method: "POST", body: JSON.stringify({ ticketId: "M7-API-1", channel: "API" }) });

  for (const [stageIndex, stage] of STAGES.entries()) {
    // Distinct targetCommit per stage keeps the idempotency key
    // `ticket:<ticketId>:<targetCommit>` unique so each stage gets its own task.
    const targetCommit = `${input.targetCommit.slice(0, 62)}${(stageIndex + 1).toString(16).padStart(2, "0")}`;
    const created = await json<{ taskId: string; version: number }>("/workflows/from-ticket", {
      method: "POST",
      body: JSON.stringify({ ticketId: input.ticketId, repositoryAlias: input.repositoryAlias, targetCommit }),
    });
    steps.push({ label: "task created", detail: created.taskId });

    let taskVersion = created.version;
    const claimed = await json<{ taskId: string; version: number }>(`/tasks/${created.taskId}/claim`, {
      method: "POST", body: JSON.stringify({ expectedVersion: taskVersion, leaseMinutes: 30 }),
    });
    taskVersion = claimed.version;

    const artifact = await json<{ artifactId: string; version: number }>(`/tasks/${created.taskId}/results`, {
      method: "POST", body: JSON.stringify({
        artifactId: `ART-${stage.type}`,
        type: stage.artifactType,
        sections: [{ key: "summary", title: stage.label, body: `Fictional ${stage.label} report with evidence.` }],
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

    const approved = await json<{ taskId: string; version: number }>("/approvals", {
      method: "POST", body: JSON.stringify({
        taskId: created.taskId,
        artifactId: artifact.artifactId,
        artifactVersion: artifact.version,
        expectedTaskVersion: taskVersion,
      }),
    });
    taskVersion = approved.version;
    steps.push({ label: `${stage.label} approved`, detail: artifact.artifactId });

    const ci = await json<{ taskId: string; version: number }>(`/tasks/${created.taskId}/ci`, {
      method: "POST", body: JSON.stringify({
        expectedVersion: taskVersion,
        state: "PASSED",
        buildFingerprint: `m7-fake-build-${stage.type}`,
      }),
    });
    taskVersion = ci.version;

    await json(`/tasks/${created.taskId}/manual-e2e`, {
      method: "POST", body: JSON.stringify({
        expectedVersion: taskVersion,
        caseId: "E2E-M7-1",
        result: "PASS",
        actorRole: "QA",
        executedAt: new Date().toISOString(),
        buildFingerprint: `m7-fake-build-${stage.type}`,
        actualResult: "Fictional manual E2E passed",
        evidenceOrWaiver: "fictional-evidence",
      }),
    });
    steps.push({ label: "manual E2E passed", detail: "E2E-M7-1" });
  }

  return { steps, auditTrail: steps, artifactIds };
}
