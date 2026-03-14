"use client";

import { useCallback, useEffect, useRef, useState } from "react";

type PageResult<T> = {
  content: T[];
  totalPages: number;
  totalElements: number;
};

type FetchFn<T, F> = (args: {
  page: number;
  size: number;
  sort: string;
  direction: "ASC" | "DESC";
  filters: F;
}) => Promise<PageResult<T>>;

type Options<F> = {
  initialSort: string;
  initialDirection?: "ASC" | "DESC";
  initialPageSize?: number;
  initialFilters: F;
};

export function useServerTable<T, F extends Record<string, unknown>>(
  fetchFn: FetchFn<T, F>,
  options: Options<F>,
) {
  const {
    initialSort,
    initialDirection = "DESC",
    initialPageSize = 20,
    initialFilters,
  } = options;

  const [rows, setRows] = useState<T[]>([]);
  const [filters, setFilters] = useState<F>(initialFilters);

  const [sort, setSort] = useState(initialSort);
  const [direction, setDirection] = useState<"ASC" | "DESC">(initialDirection);

  const [page, setPage] = useState(0);
  const [size] = useState(initialPageSize);

  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [loading, setLoading] = useState(false);

  const requestIdRef = useRef(0);

  const load = useCallback(async () => {
    const requestId = ++requestIdRef.current;

    setLoading(true);

    try {
      const res = await fetchFn({
        page,
        size,
        sort,
        direction,
        filters,
      });

      if (requestId !== requestIdRef.current) return;

      const nextTotalPages = res.totalPages ?? 0;

      setRows(res.content ?? []);
      setTotalPages(nextTotalPages);
      setTotalElements(res.totalElements ?? 0);

      if (page >= nextTotalPages && nextTotalPages > 0) {
        setPage(nextTotalPages - 1);
      }
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false);
      }
    }
  }, [fetchFn, page, size, sort, direction, filters]);

  useEffect(() => {
    load();
  }, [load]);

  const setSortField = (field: string) => {
    if (sort === field) {
      setDirection((d) => (d === "ASC" ? "DESC" : "ASC"));
    } else {
      setSort(field);
      setDirection("ASC");
    }

    setPage(0);
  };

  const updateFilters = (patch: Partial<F>) => {
    setFilters((prev) => ({ ...prev, ...patch }));
    setPage(0);
  };

  return {
    rows,
    loading,
    page,
    size,
    totalPages,
    totalElements,
    sort,
    direction,
    filters,
    setPage,
    setSortField,
    updateFilters,
    reload: load,
  };
}