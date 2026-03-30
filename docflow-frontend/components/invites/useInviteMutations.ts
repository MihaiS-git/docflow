"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";
import { invitesKeys } from "@/lib/queryKeys/invitesKeys";

type TableStatusSetter = (
  value:
    | { type: "success"; message: string }
    | { type: "error"; message: string }
    | null,
) => void;

type Params = {
  tenantId: string;
  setStatus: TableStatusSetter;
};

export function useInviteMutations({ tenantId, setStatus }: Params) {
  const queryClient = useQueryClient();

  const invalidate = async () => {
    if (!tenantId) return;
    await queryClient.invalidateQueries({
      queryKey: invitesKeys.lists(tenantId),
      refetchType: "active",
    });
  };

  const expireMutation = useMutation({
    mutationFn: async () => {
      return apiFetch<{ expiredInvites: number }>(
        `/api/tenants/${tenantId}/invites/expire`,
        { method: "POST" },
      );
    },
    onSuccess: async (result) => {
      setStatus({
        type: "success",
        message: `Expired ${result.expiredInvites} pending invites.`,
      });

      await invalidate();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setStatus({ type: "error", message: err.message });
      } else {
        setStatus({ type: "error", message: "Cleanup failed." });
      }
    },
  });

  const revokeMutation = useMutation<string, unknown, string>({
    mutationFn: async (inviteId: string) => {
      await apiFetch<void>(
        `/api/tenants/${tenantId}/invites/${inviteId}/revoke`,
        { method: "POST" },
      );
      return inviteId;
    },

    onError: async (err) => {
      if (err instanceof ApiError) {
        if (err.message.includes("terminal invite")) {
          setStatus({
            type: "error",
            message: "Invite already processed. Refreshing...",
          });

          await invalidate();
          return;
        }

        setStatus({ type: "error", message: err.message });
      } else {
        setStatus({
          type: "error",
          message: "Failed to revoke invite.",
        });
      }
    },

    onSuccess: async () => {
      setStatus({
        type: "success",
        message: "Invite revoked successfully.",
      });

      await invalidate();
    },
  });

  return {
    expireMutation,
    revokeMutation,
  };
}