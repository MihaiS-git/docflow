"use client";

import { useCallback, useEffect, useState } from "react";

export function useAuditFilters<F extends Record<string, string>>(
  namespace: string,
  initial: F
) {
  const [filters, setFilters] = useState<F>(() => {
    if (typeof window === "undefined") return initial;

    const params = new URLSearchParams(window.location.search);
    const next = { ...initial };

    Object.keys(initial).forEach((k) => {
      const v = params.get(`${namespace}.${k}`);
      if (v !== null) next[k as keyof F] = v as F[keyof F];
    });

    return next;
  });

  const setFilter = useCallback((key: keyof F, value: string) => {
    setFilters((prev) => ({
      ...prev,
      [key]: value,
    }));
  }, []);

  const resetFilters = useCallback(() => {
    setFilters((prev) => {
      const cleared = { ...prev };

      Object.keys(cleared).forEach((k) => {
        cleared[k as keyof F] = "" as F[keyof F];
      });

      return cleared;
    });
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);

    Object.entries(filters)
      .filter(([, v]) => v?.trim())
      .sort(([a], [b]) => a.localeCompare(b))
      .forEach(([k, v]) => {
        params.set(`${namespace}.${k}`, v.trim());
      });

    const query = params.toString();

    if (window.location.search === (query ? `?${query}` : "")) return;

    const url = window.location.pathname + (query ? `?${query}` : "");

    window.history.replaceState(null, "", url);
  }, [filters, namespace]);

  const buildParams = useCallback(() => {
    const params = new URLSearchParams();

    Object.entries(filters)
      .filter(([, v]) => v?.trim())
      .sort(([a], [b]) => a.localeCompare(b))
      .forEach(([k, v]) => {
        params.set(k, v.trim());
      });

    return params;
  }, [filters]);

  return {
    filters,
    setFilter,
    resetFilters,
    buildParams,
  };
}