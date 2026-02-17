export function adaptCursorPage<T>(dto: {
  items?: T[];
  hasMore?: boolean;
  nextCursorTimestamp?: string | null;
  nextCursorId?: string | null;
}) {
  return {
    content: dto.items ?? [],
    hasNext: dto.hasMore ?? false,
    nextCursorTimestamp: dto.nextCursorTimestamp ?? undefined,
    nextCursorId: dto.nextCursorId ?? undefined,
  };
}
