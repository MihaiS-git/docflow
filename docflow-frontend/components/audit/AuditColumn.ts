import type { ReactNode } from "react";

export type AuditColumn<T> = {
  header: string;
  className?: string;
  render: (
    row: T,
    ctx: {
      setFilter?: <K extends string>(key: K, value: string) => void;
      triggerQuery?: () => void;
      onRowAction?: (type: string, payload: unknown) => void;
    }
  ) => ReactNode;
};