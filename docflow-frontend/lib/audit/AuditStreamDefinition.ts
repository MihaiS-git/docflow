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
};