export class WorkflowApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly correlationId: string,
  ) {
    super(message);
    this.name = "WorkflowApiError";
  }
}

export class WorkflowApiClient {
  private readonly baseUrl: string;

  constructor(
    baseUrl: string,
    private readonly fetcher: typeof fetch = fetch,
    private readonly demoUser?: string,
  ) {
    const parsed = new URL(baseUrl);
    if (demoUser && !["127.0.0.1", "localhost", "::1"].includes(parsed.hostname)) {
      throw new Error("Demo identity is allowed only for a loopback Workflow Service");
    }
    this.baseUrl = parsed.toString().replace(/\/$/, "");
  }

  listTasks(correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request("/api/v1/tasks", { method: "GET" }, correlationId, signal);
  }

  getTaskContext(taskId: string, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/tasks/${encodeURIComponent(taskId)}`, { method: "GET" }, correlationId, signal);
  }

  claimTask(taskId: string, expectedVersion: number, leaseMinutes: number,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/tasks/${encodeURIComponent(taskId)}/claim`, {
      method: "POST",
      body: JSON.stringify({ expectedVersion, leaseMinutes }),
    }, correlationId, signal);
  }

  submitArtifact(taskId: string, artifact: unknown,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/tasks/${encodeURIComponent(taskId)}/results`, {
      method: "POST",
      body: JSON.stringify(artifact),
    }, correlationId, signal);
  }

  requestApproval(approval: unknown, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request("/api/v1/approvals", {
      method: "POST",
      body: JSON.stringify(approval),
    }, correlationId, signal);
  }

  completeTask(taskId: string, expectedVersion: number,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/tasks/${encodeURIComponent(taskId)}/confirm`, {
      method: "POST",
      body: JSON.stringify({ expectedVersion }),
    }, correlationId, signal);
  }

  getIdentity(correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request("/api/v1/internal-readiness/identity", { method: "GET" }, correlationId, signal);
  }

  validatePodRoster(roster: unknown, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request("/api/v1/internal-readiness/pods/validate", {
      method: "POST", body: JSON.stringify(roster),
    }, correlationId, signal);
  }

  importPodRoster(roster: unknown, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request("/api/v1/internal-readiness/pods/import", {
      method: "POST", body: JSON.stringify(roster),
    }, correlationId, signal);
  }

  getIntegrationDiagnostics(correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request("/api/v1/internal-readiness/integrations", { method: "GET" }, correlationId, signal);
  }

  analyzeJourney(manifest: unknown, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request("/api/v1/journeys/analyze", {
      method: "POST", body: JSON.stringify(manifest),
    }, correlationId, signal);
  }

  getNextInternalValidation(correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request("/api/v1/internal-readiness/next-validation", { method: "GET" }, correlationId, signal);
  }

  createEpic(epic: unknown, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request("/api/v1/epics", { method: "POST", body: JSON.stringify(epic) }, correlationId, signal);
  }

  activateEpic(epicId: string, expectedVersion: number, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/epics/${encodeURIComponent(epicId)}/activate`, {
      method: "POST", body: JSON.stringify({ expectedVersion }),
    }, correlationId, signal);
  }

  attachTicket(epicId: string, body: unknown, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/epics/${encodeURIComponent(epicId)}/tickets`, {
      method: "POST", body: JSON.stringify(body),
    }, correlationId, signal);
  }

  advanceTicket(ticketId: string, expectedVersion: number, target: string,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/tickets/${encodeURIComponent(ticketId)}/advance`, {
      method: "POST", body: JSON.stringify({ expectedVersion, target }),
    }, correlationId, signal);
  }

  addRepoTask(ticketId: string, repositoryAlias: string, baseCommit: string,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/tickets/${encodeURIComponent(ticketId)}/repo-tasks`, {
      method: "POST", body: JSON.stringify({ repositoryAlias, baseCommit }),
    }, correlationId, signal);
  }

  advanceRepoTask(repoTaskId: string, expectedVersion: number, target: string,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/repo-tasks/${encodeURIComponent(repoTaskId)}/advance`, {
      method: "POST", body: JSON.stringify({ expectedVersion, target }),
    }, correlationId, signal);
  }

  addDependency(epicId: string, fromTicketId: string, toTicketId: string,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/epics/${encodeURIComponent(epicId)}/dependencies`, {
      method: "POST", body: JSON.stringify({ fromTicketId, toTicketId }),
    }, correlationId, signal);
  }

  createChangeRequest(epicId: string, body: unknown,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/epics/${encodeURIComponent(epicId)}/change-requests`, {
      method: "POST", body: JSON.stringify(body),
    }, correlationId, signal);
  }

  approveChangeRequest(changeRequestId: string, expectedVersion: number, actorRole: string,
    correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/change-requests/${encodeURIComponent(changeRequestId)}/approve`, {
      method: "POST", body: JSON.stringify({ expectedVersion, actorRole }),
    }, correlationId, signal);
  }

  skipTask(taskId: string, body: unknown, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/tasks/${encodeURIComponent(taskId)}/skip`, {
      method: "POST", body: JSON.stringify(body),
    }, correlationId, signal);
  }

  resumeEpic(epicId: string, correlationId: string, signal: AbortSignal): Promise<unknown> {
    return this.request(`/api/v1/epics/${encodeURIComponent(epicId)}/resume`, { method: "GET" }, correlationId, signal);
  }

  private async request(
    path: string,
    init: RequestInit,
    correlationId: string,
    signal: AbortSignal,
  ): Promise<unknown> {
    const headers: Record<string, string> = {
      "Accept": "application/json",
      "Content-Type": "application/json",
      "X-Correlation-ID": correlationId,
    };
    if (this.demoUser) headers["X-Demo-User"] = this.demoUser;

    const response = await this.fetcher(`${this.baseUrl}${path}`, { ...init, headers, signal });
    if (!response.ok) {
      throw new WorkflowApiError(response.status, "Workflow Service request failed", correlationId);
    }
    if (response.status === 204) return undefined;
    return response.json();
  }
}
