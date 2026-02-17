"use client";

type Props = {
  hasNext: boolean;
  loading: boolean;
  onLoadMore: () => void;
};

export function AuditCursorPagination({
  hasNext,
  loading,
  onLoadMore,
}: Props) {
  if (!hasNext) return null;

  return (
    <div className="pt-3">
      <button
        type="button"
        onClick={onLoadMore}
        disabled={loading}
        className="px-3 py-1 rounded border disabled:opacity-50"
      >
        {loading ? "Loading…" : "Load more"}
      </button>
    </div>
  );
}
