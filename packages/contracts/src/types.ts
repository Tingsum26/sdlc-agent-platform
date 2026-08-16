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
