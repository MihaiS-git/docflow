export const usersKeys = {
  all: ["users"] as const,

  lists: () => [...usersKeys.all, "list"] as const,

  list: (params: {
    page: number;
    size: number;
    sort: string;
    direction: "ASC" | "DESC";
    tenantId?: string;
    status?: string;
    email?: string;
  }) =>
    [
      ...usersKeys.lists(),
      params.page,
      params.size,
      params.sort,
      params.direction,
      params.tenantId ?? "",
      params.status ?? "",
      params.email ?? "",
    ] as const,
};