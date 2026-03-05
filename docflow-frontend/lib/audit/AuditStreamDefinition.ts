import type { ReactNode } from "react";
import type { AuditColumn } from "@/components/audit/AuditColumn";

export type AuditStreamDefinition<
  T,
  F extends Record<string, string>
> = {
  key: string;
  title: string;
  endpoint: string;
  filenameBase: string;

  columns: AuditColumn<T>[];

  filterDefinitions: {
    key: keyof F;
    label: string;
  }[];

  renderFilters?: (ctx: {
    filters: F;
    setFilter: (key: keyof F, value: string) => void;
    triggerQuery?: () => void;
  }) => ReactNode;
};