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

export type EvidenceStatus = "SIMULATED_PASS" | "CONTRACT_PASS" | "INTERNAL_VALIDATION_REQUIRED" | "BLOCKED";
export type IdentitySource = "GITHUB_ENTERPRISE" | "ADMIN_BINDING" | "SSO";

export interface EnterprisePrincipal {
  schemaVersion: "1.0";
  principalId: string;
  employeeId: string;
  displayLabel: string;
  maskedEmail?: string;
  source: IdentitySource;
  githubLogin?: string;
}

export interface PodMembership {
  membershipId: string;
  employeeId: string;
  principalId: string;
  displayLabel: string;
  role: string;
  journeyId: string;
  active: boolean;
  effectiveFrom: string;
  effectiveTo?: string;
  aliases: string[];
}

export interface IntegrationDiagnostic {
  schemaVersion: "1.0";
  provider: "JIRA" | "CONFLUENCE" | "GHES" | "JENKINS" | "SPLUNK";
  status: EvidenceStatus;
  observedAt: string;
  source: string;
  safeDetail: string;
}

export interface JourneyManifest {
  schemaVersion: "1.0";
  journeyId: string;
  domainId: string;
  version: number;
  repositories: Array<{ alias: string; role: "API" | "WEB" | "IOS" | "ANDROID" | "SUPPORTING"; ref: string }>;
  screens: Array<{ screenId: string; client: "WEB" | "IOS" | "ANDROID"; repositoryAlias: string }>;
  httpEdges: Array<{
    edgeId: string; caller: string; apiRepositoryAlias: string; method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
    normalizedPath: string; requestSchemaRef: string; responseSchemaRef: string; commonHeaderRule: string;
    authenticationClass: string; compatibility: "BACKWARD_COMPATIBLE" | "ADDITIVE_WITH_FLAG" | "BREAKING_REJECTED";
    provenance: { source: string; ref: string; evidenceId: string };
  }>;
  releasePolicy: { webApiFirst: boolean; nativeReleaseTrain: string; compatibilityWindowDays: number; rollbackRule: string };
  featureFlag: { required: boolean; provider: string; ownerRole: string };
  e2eOwners: Array<{ scenario: string; ownerRole: string }>;
}

export const SDLC_CHANNELS = ["API", "WEB", "IOS", "ANDROID"] as const;

export type EpicStatus = "CREATED" | "ACTIVE" | "COMPLETED" | "CANCELLED";
export type Channel = (typeof SDLC_CHANNELS)[number];
export type TicketDeliveryStatus =
  | "PLANNED" | "IN_ANALYSIS" | "WAITING_FOR_APPROVAL" | "IN_DEVELOPMENT"
  | "PR_OPEN" | "CI_PASSED" | "MERGED" | "RELEASED" | "FLAG_ENABLED"
  | "E2E_VERIFIED" | "BLOCKED" | "CANCELLED";
export type RepoTaskStatus = "PLANNED" | "IN_PROGRESS" | "PR_OPEN" | "MERGED" | "BLOCKED" | "CANCELLED";
export type ChangeRequestStatus = "DRAFT" | "APPROVED" | "REJECTED";
export type ChangeUrgency = "STANDARD" | "URGENT";
export type DependencyKind = "REQUIRES_BEFORE";
export type DependencyStatus = "BLOCKING" | "RESOLVED";
export type ChangeApproverRole = "BUSINESS_OWNER" | "TECHNICAL_OWNER";

export interface EpicWorkflow {
  epicId: string;
  title: string;
  journeyId: string;
  status: EpicStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface TicketWorkflow {
  ticketId: string;
  epicId: string;
  channel: Channel;
  status: TicketDeliveryStatus;
  pendingChangeConfirmation: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface RepoTask {
  repoTaskId: string;
  ticketId: string;
  repositoryAlias: string;
  baseCommit: string;
  status: RepoTaskStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface Dependency {
  dependencyId: string;
  epicId: string;
  fromTicketId: string;
  toTicketId: string;
  kind: DependencyKind;
  status: DependencyStatus;
  version: number;
  updatedAt: string;
}

export interface EpicChangeRequest {
  changeRequestId: string;
  epicId: string;
  reason: string;
  urgency: ChangeUrgency;
  description: string;
  affectedTicketIds: string[];
  approvedRoles: ChangeApproverRole[];
  requiredApprovals: number;
  status: ChangeRequestStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface SkipAttestation {
  attestationId: string;
  taskId: string;
  stageType: string;
  reason: string;
  discussedWith: string;
  actorId: string;
  actorRole: string;
  occurredAt: string;
  correlationId: string;
}

export interface DomainAuditEvent {
  eventId: string;
  aggregateId: string;
  aggregateType: string;
  action: string;
  detail: string;
  actorId: string;
  occurredAt: string;
  correlationId: string;
}
