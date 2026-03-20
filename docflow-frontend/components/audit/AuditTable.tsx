"use client";

import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeaderCell,
  TableHeaderRow,
  TableRow,
} from "../ui/table";
import type { AuditColumn } from "./AuditColumn";

type AuditTableProps<T> = {
  rows: T[];
  columns: AuditColumn<T>[];
  rowKey: (row: T) => string;
  setFilter?: <K extends string>(key: K, value: string) => void;
  triggerQuery?: () => void;
};

export function AuditTable<T>({
  rows,
  columns,
  rowKey,
  setFilter,
  triggerQuery,
}: AuditTableProps<T>) {
  const ctx = {
    setFilter,
    triggerQuery,
  };

  return (
    <TableContainer>
      <Table>
        <TableHead>
          <TableHeaderRow>
            {columns.map((c) => (
              <TableHeaderCell key={c.header}>{c.header}</TableHeaderCell>
            ))}
          </TableHeaderRow>
        </TableHead>

        <TableBody>
          {rows.map((r) => (
            <TableRow
              key={rowKey(r)}
              variant={triggerQuery ? "clickable" : "default"}
            >
              {columns.map((c) => (
                <TableCell
                  key={c.header}
                  className={`${
                    triggerQuery ? "cursor-pointer" : ""
                  } ${c.className ?? ""}`}
                >
                  {c.render(r, ctx)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
