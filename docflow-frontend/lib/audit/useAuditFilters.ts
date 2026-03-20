"use client";

import { useCallback, useMemo, useState } from "react";

type FilterDefinition = {
  key: string;
  validate?: (value: string) => boolean;
};

export function useAuditFilters<F extends Record<string, string>>(
  namespace: string,
  initial: F,
  filterDefinitions?: FilterDefinition[],
) {
  const validators = useMemo(() => {
    const next: Record<string, ((value: string) => boolean) | undefined> = {};

    (filterDefinitions ?? []).forEach((definition) => {
      if (definition.validate) {
        next[definition.key] = definition.validate;
      }
    });

    return next;
  }, [filterDefinitions]);

  const [filters, setFilters] = useState<F>(() => {
    if (typeof window === "undefined") return initial;

    const params = new URLSearchParams(window.location.search);
    const next = { ...initial };

    Object.keys(initial).forEach((key) => {
      const value = params.get(`${namespace}.${key}`);

      if (value !== null) {
        next[key as keyof F] = value as F[keyof F];
      }
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

      Object.keys(cleared).forEach((key) => {
        cleared[key as keyof F] = "" as F[keyof F];
      });

      return cleared;
    });
  }, []);

  const syncToUrl = useCallback(() => {
    const params = new URLSearchParams();

    Object.entries(filters)
      .filter(([, value]) => value?.trim())
      .sort(([a], [b]) => a.localeCompare(b))
      .forEach(([key, value]) => {
        const trimmedValue = value.trim();
        const validator = validators[key];

        if (validator && !validator(trimmedValue)) {
          return;
        }

        params.set(`${namespace}.${key}`, trimmedValue);
      });

    const query = params.toString();
    const next = query ? `?${query}` : "";

    if (window.location.search === next) return;

    const url = window.location.pathname + next;
    window.history.replaceState(null, "", url);
  }, [filters, namespace, validators]);

  const buildParams = useCallback(() => {
    const params = new URLSearchParams();

    Object.entries(filters)
      .filter(([, value]) => value?.trim())
      .sort(([a], [b]) => a.localeCompare(b))
      .forEach(([key, value]) => {
        const trimmedValue = value.trim();
        const validator = validators[key];

        if (validator && !validator(trimmedValue)) {
          return;
        }

        params.set(key, trimmedValue);
      });

    return params;
  }, [filters, validators]);

  return {
    filters,
    setFilter,
    resetFilters,
    buildParams,
    syncToUrl,
  };
}