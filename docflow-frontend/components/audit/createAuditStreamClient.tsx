"use client";

import { useCallback, useState } from "react";
import { useAuditFilters } from "@/lib/audit/useAuditFilters";
import { AuditStreamContainer } from "@/components/audit/AuditStreamContainer";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";
import { AuditFiltersPanel } from "./filters/AuditFiltersPanel";

export function createAuditStreamClient<
  T extends { id?: string; eventFingerprint?: string },
  F extends Record<string, string>,
>(definition: AuditStreamDefinition<T, F>) {
  return function AuditStreamClient() {
    const { filters, setFilter, buildParams } = useAuditFilters<F>({} as F);

    const [queryNonce, setQueryNonce] = useState(0);

    const triggerQuery = useCallback(() => {
      setQueryNonce((n) => n + 1);
    }, []);

    return (
      <AuditStreamContainer<T>
        title={definition.title}
        endpoint={definition.endpoint}
        filenameBase={definition.filenameBase}
        buildFilterParams={buildParams}
        renderFilters={() => (
          <AuditFiltersPanel
            filters={filters}
            setFilter={setFilter}
            triggerQuery={triggerQuery}
            definitions={definition.filterDefinitions}
          />
        )}
        columns={definition.columns}
        rowKey={(r) => r.id ?? r.eventFingerprint ?? ""}
        setFilter={(k, v) => setFilter(k as keyof F, v)}
        queryNonce={queryNonce}
        triggerQuery={triggerQuery}
      />
    );
  };
}
