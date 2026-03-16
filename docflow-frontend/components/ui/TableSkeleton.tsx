"use client";

type Props = {
  rows?: number;
  columns?: number;
};

export default function TableSkeleton({ rows = 6, columns = 8 }: Props) {
  return (
    <>
      {Array.from({ length: rows }).map((_, r) => (
        <tr key={r} className="animate-pulse">
          {Array.from({ length: columns }).map((_, c) => (
            <td key={c} className="px-4 py-3">
              <div className="h-3 w-full rounded bg-(--color-border)" />
            </td>
          ))}
        </tr>
      ))}
    </>
  );
}