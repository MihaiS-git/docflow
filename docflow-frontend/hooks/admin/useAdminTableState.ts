"use client";

import { useCallback, useState } from "react";

type SetFieldAction<T> = {
  type: "SET_FIELD";
  field: keyof T;
  value: string;
};

type ResetAction = {
  type: "RESET";
};

type FiltersAction<T> = SetFieldAction<T> | ResetAction;

export function useAdminTableState<TFilters>({
  initialSort,
  initialDirection,
  initialPageSize,
  dispatchFilters,
}: {
  initialSort: string;
  initialDirection: "ASC" | "DESC";
  initialPageSize: number;
  dispatchFilters: React.Dispatch<FiltersAction<TFilters>>;
}) {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(initialPageSize);
  const [sort, setSort] = useState(initialSort);
  const [direction, setDirection] =
    useState<"ASC" | "DESC">(initialDirection);

  /* ---------------- Sort ---------------- */

  const handleSort = useCallback((field: string) => {
    setSort((prev) => {
      if (prev === field) {
        setDirection((d) => (d === "ASC" ? "DESC" : "ASC"));
        return prev;
      }

      setDirection("ASC");
      return field;
    });

    setPage(0);
  }, []);

  /* ---------------- Filters ---------------- */

  const handleFilterChange = useCallback(
    <K extends keyof TFilters>(field: K) =>
      (value: string) => {
        setPage(0);

        dispatchFilters({
          type: "SET_FIELD",
          field,
          value,
        });
      },
    [dispatchFilters],
  );

  const resetAll = useCallback(() => {
    dispatchFilters({ type: "RESET" });
    setPage(0);
  }, [dispatchFilters]);

  /* ---------------- Pagination ---------------- */

  const handlePageSizeChange = useCallback((size: number) => {
    setPageSize(size);
    setPage(0);
  }, []);

  return {
    page,
    pageSize,
    sort,
    direction,

    setPage,

    handleSort,
    handleFilterChange,
    handlePageSizeChange,
    resetAll,
  };
}