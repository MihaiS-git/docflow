import { useState } from "react";

export type CursorPage<T> = {
  content: T[];
  nextCursorTimestamp?: string;
  nextCursorId?: string;
  hasNext: boolean;
};

export function useCursorPagination<T>() {
  const [rows, setRows] = useState<T[]>([]);
  const [nextCursorTimestamp, setNextCursorTimestamp] = useState<string | undefined>(undefined);
  const [nextCursorId, setNextCursorId] = useState<string | undefined>(undefined);
  const [hasNext, setHasNext] = useState<boolean>(false);

  function reset(): void {
    setRows([]);
    setNextCursorTimestamp(undefined);
    setNextCursorId(undefined);
    setHasNext(false);
  }

  function applyFirstPage(page: CursorPage<T>): void {
    setRows(page.content);
    setNextCursorTimestamp(page.nextCursorTimestamp);
    setNextCursorId(page.nextCursorId);
    setHasNext(Boolean(page.hasNext));
  }

  function appendPage(page: CursorPage<T>): void {
    setRows((prev) => [...prev, ...page.content]);
    setNextCursorTimestamp(page.nextCursorTimestamp);
    setNextCursorId(page.nextCursorId);
    setHasNext(Boolean(page.hasNext));
  }

  return {
    rows,
    nextCursorTimestamp,
    nextCursorId,
    hasNext,
    reset,
    applyFirstPage,
    appendPage,
  };
}
