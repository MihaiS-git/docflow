import { AdminInvite, InviteStatus } from "@/types/admin/Invite";

export type InviteUiStatus = InviteStatus | "EXPIRED";

export function getEffectiveInviteStatus(
  invite: AdminInvite,
  now: number = Date.now()
): InviteUiStatus {
  if (
    invite.status === "PENDING" &&
    new Date(invite.expiresAt).getTime() < now
  ) {
    return "EXPIRED";
  }

  return invite.status;
}