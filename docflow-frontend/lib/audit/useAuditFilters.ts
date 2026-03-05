"use client";

import { useState } from "react";

export type AuditFilterState = Record<string, string>;

export function useAuditFilters<T extends AuditFilterState>(initial: T) {
  const [filters, setFilters] = useState<T>(initial);

  function setFilter<K extends keyof T>(key: K, value: string) {
    setFilters((prev) => ({
      ...prev,
      [key]: value,
    }));
  }

  function buildParams(): Record<string, string> {
    const params: Record<string, string> = {};

    for (const [key, value] of Object.entries(filters)) {
      if (value.trim()) {
        params[key] = value.trim();
      }
    }

    return params;
  }

  return {
    filters,
    setFilter,
    buildParams,
  };
}