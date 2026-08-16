export const taskStatuses = [
  "CREATED",
  "WAITING_FOR_LOCAL_COPILOT",
  "LOCAL_COPILOT_RUNNING",
  "WAITING_FOR_USER_CONFIRMATION",
  "WAITING_FOR_APPROVAL",
  "WAITING_FOR_CI",
  "WAITING_FOR_MANUAL_E2E",
  "BLOCKED",
  "COMPLETED",
  "CANCELLED"
] as const;

export type TaskStatus = (typeof taskStatuses)[number];

export interface WorkflowScope {
  ticketId: string;
  repositoryAlias: string;
  targetCommit: string;
}

export interface WorkflowTask {
  schemaVersion: "1.0";
  taskId: string;
  type: string;
  status: TaskStatus;
  scope: WorkflowScope;
  assigneeId?: string | null;
  leaseExpiresAt?: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProblemDetails {
  type: string;
  title: string;
  status: number;
  detail?: string;
  correlationId: string;
}
