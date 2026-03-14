import { ReactNode } from "react";

export type Column<T> = {
  label: string;
  field: keyof T;
  render?: (row: T) => ReactNode;
  className?: string;
};

/**
 * Helper for creating strongly typed table columns.
 *
 * Prevents typos like:
 *   field: "mangerName"
 *
 * because the key must exist on T.
 */
export function createColumns<T>() {
  return function <K extends keyof T>(
    defs: {
      label: string;
      field: K;
      render?: (row: T) => ReactNode;
      className?: string;
    }[]
  ): Column<T>[] {
    return defs;
  };
}