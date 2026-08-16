export type TaskStatus = "CREATED" | "WAITING_FOR_LOCAL_COPILOT" | "LOCAL_COPILOT_RUNNING"
  | "WAITING_FOR_USER_CONFIRMATION" | "WAITING_FOR_APPROVAL" | "WAITING_FOR_CI"
  | "WAITING_FOR_MANUAL_E2E" | "BLOCKED" | "COMPLETED" | "CANCELLED";

const labels: Record<TaskStatus, string> = {
  CREATED: "Created",
  WAITING_FOR_LOCAL_COPILOT: "Waiting for local Copilot",
  LOCAL_COPILOT_RUNNING: "Local Copilot running",
  WAITING_FOR_USER_CONFIRMATION: "Waiting for user confirmation",
  WAITING_FOR_APPROVAL: "Waiting for approval",
  WAITING_FOR_CI: "Waiting for CI",
  WAITING_FOR_MANUAL_E2E: "Waiting for manual E2E",
  BLOCKED: "Blocked",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

export function TaskStatusBadge({ status }: { status: TaskStatus }) {
  const label = labels[status];
  return <span className={`sdlc-status sdlc-status--${status.toLowerCase()}`} role="status" aria-label={label}>
    <svg aria-hidden="true" width="12" height="12" viewBox="0 0 12 12" focusable="false">
      <circle cx="6" cy="6" r="4" fill="currentColor" />
    </svg>
    <span>{label}</span>
  </span>;
}
