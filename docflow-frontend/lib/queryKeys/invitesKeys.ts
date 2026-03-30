export const invitesKeys = {
  all: ["invites"] as const,

  lists: (tenantId: string) =>
    [...invitesKeys.all, tenantId] as const,

  list: (
    tenantId: string,
    params: {
      page: number;
      pageSize: number;
      sort: string;
      direction: "ASC" | "DESC";
      email?: string;
      status?: string;
    },
  ) =>
    [
      ...invitesKeys.lists(tenantId),
      params.page,
      params.pageSize,
      params.sort,
      params.direction,
      params.email ?? "",
      params.status ?? "",
    ] as const,
};