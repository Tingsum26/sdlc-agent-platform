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
