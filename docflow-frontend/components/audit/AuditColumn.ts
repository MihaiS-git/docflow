import type { ReactNode } from "react";

export type AuditColumn<T> = {
  header: string;
  className?: string;
  render: (
    row: T,
    ctx: {
      setFilter?: (key: string, value: string) => void;
      triggerQuery?: () => void;
    }
  ) => ReactNode;
};