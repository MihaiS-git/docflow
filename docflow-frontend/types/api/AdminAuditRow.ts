import { AuditResult } from "./AuditResult";
import { CorrelationSource } from "./CorrelationSource";
import { ExecutionContext } from "./ExecutionContext";

export type AdminAuditActionType =
  | "ROLE_ASSIGNED"
  | "ROLE_REVOKED"
  | "BOOTSTRAP_ACTIVATED"
  | "USER_ACTIVATED"
  | "USER_LOCKED"
  | "USER_DISABLED"
  | "USER_INVITED"
  | "INVITE_FAILED"
  | "INVITE_CLEANUP"
  | "INVITE_REVOKED"
  | "TENANT_CREATED"
  | "TENANT_CREATE_FAILED"
  | "TENANT_UPDATED"
  | "TENANT_SUSPENDED"
  | "TENANT_MUTATION_DENIED"
  | "RETENTION_POLICY_UPSERT"
  | "RETENTION_POLICY_UPSERT_FAILED";

export type RoleChangeMetadata = {
  type: "ROLE_CHANGE";
  roleName: string;
  comment: string | null;
};

export type UserStateChangeMetadata = {
  type: "USER_STATE_CHANGE";
  reason: string;
  comment: string | null;
};

export type InviteAuditMetadata = {
  type: "INVITE";
  invitedEmail: string;
  inviteId: string;
};

export type InviteCleanupAuditMetadata = {
  type: "INVITE_CLEANUP";
  deletedInvites: number;
  deletedUsers: number;
};

export type TenantAuditMetadata = {
  type: "TENANT";
  tenantId: string;
  operation: string;
  comment: string | null;
};

export type TenantMembershipChangeMetadata = {
  type: "TENANT_MEMBERSHIP_CHANGE";
  tenantId: string;
  targetUserId: string;
  operation: string;
  oldRole: string | null;
  newRole: string | null;
  oldStatus: string | null;
  newStatus: string | null;
  comment: string | null;
};

export type RetentionPolicyAuditMetadata = {
  type: "RETENTION_POLICY";
  streamName: string;
  oldRetentionDays: number | null;
  newRetentionDays: number | null;
  oldArchiveEnabled: boolean;
  newArchiveEnabled: boolean;
};

export type AdminAuditMetadata =
  | RoleChangeMetadata
  | UserStateChangeMetadata
  | InviteAuditMetadata
  | InviteCleanupAuditMetadata
  | TenantAuditMetadata
  | TenantMembershipChangeMetadata
  | RetentionPolicyAuditMetadata;

export type AdminAuditRow = {
  id: string;
  timestamp: string;
  actorUserId: string;
  subjectId: string;
  tenantId: string;
  actionType: AdminAuditActionType;
  targetUserId: string | null;
  metadata: AdminAuditMetadata | null;
  ip: string;
  userAgent: string;
  correlationId: string;
  correlationSource: CorrelationSource;
  executionContext: ExecutionContext;
  result: AuditResult;
  eventFingerprint: string;
  chainVersion: number;
  prevEventHash: string;
  eventHash: string;
};
