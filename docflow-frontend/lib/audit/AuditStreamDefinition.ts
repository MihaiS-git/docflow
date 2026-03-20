import type { AuditColumn } from "@/components/audit/AuditColumn";

export type AuditFilterDefinition = {
  key: string;
  label: string;
  validate?: (value: string) => boolean;
};

export type AuditStreamDefinition<
  T,
  F extends Record<string, string> = Record<string, string>,
> = {
  key: string;
  title: string;
  endpoint: string;
  filenameBase: string;
  columns: AuditColumn<T>[];
  filterDefinitions?: AuditFilterDefinition[];
  renderFilters?: (args: {
    filters: F;
    setFilter: (key: keyof F, value: string) => void;
    triggerQuery: () => void;
  }) => React.ReactNode;
};