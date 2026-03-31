import { useMutation, useQueryClient } from "@tanstack/react-query";

import {
  activateUser,
  assignRole,
  disableUser,
  lockUser,
  revokeRole,
} from "@/lib/admin/adminUsers";

import { usersKeys } from "@/lib/queryKeys/usersKeys";

export function useUserMutations() {
  const queryClient = useQueryClient();

  const invalidate = async () => {
    // Invalidate ALL user list variants (filters, pagination, etc.)
    await queryClient.invalidateQueries({
      queryKey: usersKeys.lists(),
    });
  };

  const activate = useMutation({
    mutationFn: activateUser,
    onSuccess: invalidate,
  });

  const lock = useMutation({
    mutationFn: lockUser,
    onSuccess: invalidate,
  });

  const disable = useMutation({
    mutationFn: disableUser,
    onSuccess: invalidate,
  });

  const assign = useMutation({
    mutationFn: ({
      userId,
      role,
    }: {
      userId: string;
      role: string;
    }) => assignRole(userId, role),
    onSuccess: invalidate,
  });

  const revoke = useMutation({
    mutationFn: ({
      userId,
      role,
    }: {
      userId: string;
      role: string;
    }) => revokeRole(userId, role),
    onSuccess: invalidate,
  });

  return { activate, lock, disable, assign, revoke };
}