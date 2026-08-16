import { randomUUID } from "node:crypto";

export interface WorkflowTask {
  taskId: string;
  type: string;
  status: string;
  scope: { ticketId: string; repositoryAlias: string; targetCommit: string };
  version: number;
  updatedAt: string;
}

export interface EnterpriseIdentity { employeeId: string; displayName: string; source: string }
export interface IntegrationDiagnostic { provider: string; status: string; observedAt: string; source: string; safeDetail: string }
export interface NextInternalValidation { complete: boolean; provider?: string; status?: string; instruction?: string }

export class WorkflowClient {
  private etag: string | undefined;
  private cachedTasks: WorkflowTask[] = [];
  private readonly baseUrl: string;

  constructor(baseUrl: string, private readonly fetcher: typeof fetch = fetch, private readonly demoActorId?: string) {
    const url = new URL(baseUrl);
    if (demoActorId && !["127.0.0.1", "localhost", "::1"].includes(url.hostname)) {
      throw new Error("Demo actor is restricted to a loopback Workflow Service");
    }
    this.baseUrl = url.toString().replace(/\/$/, "");
  }

  async listTasks(signal?: AbortSignal): Promise<WorkflowTask[]> {
    const headers = this.headers();
    if (this.etag) headers["If-None-Match"] = this.etag;
    const response = await this.fetcher(`${this.baseUrl}/api/v1/tasks`, {
      headers,
      ...(signal === undefined ? {} : { signal }),
    });
    if (response.status === 304) return this.cachedTasks;
    await this.requireOk(response);
    this.etag = response.headers.get("ETag") ?? undefined;
    this.cachedTasks = await response.json() as WorkflowTask[];
    return this.cachedTasks;
  }

  async getTask(taskId: string): Promise<WorkflowTask> {
    return this.json(`/api/v1/tasks/${encodeURIComponent(taskId)}`) as Promise<WorkflowTask>;
  }

  async getReport(artifactId: string, version: number): Promise<string> {
    const response = await this.fetcher(`${this.baseUrl}/api/v1/reports/${encodeURIComponent(artifactId)}/versions/${version}`, {
      headers: this.headers(),
    });
    await this.requireOk(response);
    return response.text();
  }

  async approve(input: { taskId: string; artifactId: string; artifactVersion: number; expectedTaskVersion: number }): Promise<WorkflowTask> {
    return this.json("/api/v1/approvals", { method: "POST", body: JSON.stringify(input) }) as Promise<WorkflowTask>;
  }

  async health(): Promise<boolean> {
    try {
      const response = await this.fetcher(`${this.baseUrl}/actuator/health`, { headers: this.headers() });
      return response.ok;
    } catch { return false; }
  }

  getIdentity(): Promise<EnterpriseIdentity> {
    return this.json("/api/v1/internal-readiness/identity") as Promise<EnterpriseIdentity>;
  }

  getIntegrationDiagnostics(): Promise<IntegrationDiagnostic[]> {
    return this.json("/api/v1/internal-readiness/integrations") as Promise<IntegrationDiagnostic[]>;
  }

  getNextInternalValidation(): Promise<NextInternalValidation> {
    return this.json("/api/v1/internal-readiness/next-validation") as Promise<NextInternalValidation>;
  }

  async renderJourneyReport(manifest: unknown): Promise<string> {
    const response = await this.fetcher(`${this.baseUrl}/api/v1/journeys/report`, {
      method: "POST", headers: this.headers(), body: JSON.stringify(manifest),
    });
    await this.requireOk(response);
    return response.text();
  }

  private async json(path: string, init: RequestInit = {}): Promise<unknown> {
    const response = await this.fetcher(`${this.baseUrl}${path}`, { ...init, headers: this.headers() });
    await this.requireOk(response);
    return response.json();
  }

  private headers(): Record<string, string> {
    const headers: Record<string, string> = {
      "Accept": "application/json", "Content-Type": "application/json", "X-Correlation-ID": randomUUID(),
    };
    if (this.demoActorId) headers["X-Demo-User"] = this.demoActorId;
    return headers;
  }

  private async requireOk(response: Response): Promise<void> {
    if (!response.ok) throw new Error(`Workflow request failed (${response.status})`);
  }
}
