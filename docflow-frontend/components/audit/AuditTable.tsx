"use client";

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
    <div className="overflow-auto border rounded">
      <table className="min-w-full text-xs">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.header} className="p-2 border-b text-left">
                {c.header}
              </th>
            ))}
          </tr>
        </thead>

        <tbody>
          {rows.map((r) => (
            <tr key={rowKey(r)} className="hover:bg-gray-50">
              {columns.map((c) => (
                <td
                  key={c.header}
                  className={`p-2 ${c.className ?? ""} ${
                    triggerQuery ? "cursor-pointer" : ""
                  }`}
                >
                  {c.render(r, ctx)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}