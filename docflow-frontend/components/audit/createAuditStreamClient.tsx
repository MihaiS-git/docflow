"use client";

import { useCallback, useState } from "react";
import { useAuditFilters } from "@/lib/audit/useAuditFilters";
import { AuditStreamContainer } from "@/components/audit/AuditStreamContainer";
import { AuditFiltersPanel } from "@/components/audit/filters/AuditFiltersPanel";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";

export function createAuditStreamClient<
  T extends { id?: string; eventFingerprint?: string },
  F extends Record<string, string>,
>(definition: AuditStreamDefinition<T, F>) {
  return function AuditStreamClient() {
    const { filters, setFilter } = useAuditFilters<F>(definition.key, {} as F);

    const [queryNonce, setQueryNonce] = useState(0);

    const triggerQuery = useCallback(() => {
      setQueryNonce((n) => n + 1);
    }, []);

    const renderFilters = () => {
      // custom filters override
      if (definition.renderFilters) {
        return definition.renderFilters({
          filters,
          setFilter,
          triggerQuery,
        });
      }

      // default filters panel from definitions
      if (definition.filterDefinitions?.length) {
        return (
          <AuditFiltersPanel
            filters={filters}
            setFilter={setFilter}
            triggerQuery={triggerQuery}
            definitions={definition.filterDefinitions}
          />
        );
      }

      return null;
    };

    return (
      <AuditStreamContainer
        title={definition.title}
        endpoint={definition.endpoint}
        filenameBase={definition.filenameBase}
        filters={filters}
        renderFilters={renderFilters}
        columns={definition.columns}
        rowKey={(r) => r.id ?? r.eventFingerprint ?? ""}
        setFilter={(k, v) => setFilter(k as keyof F, v)}
        queryNonce={queryNonce}
        triggerQuery={triggerQuery}
      />
    );
  };
}