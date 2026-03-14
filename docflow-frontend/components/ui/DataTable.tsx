"use client";

import { ReactNode, useEffect, useRef, useState } from "react";
import TableSkeleton from "@/components/ui/TableSkeleton";

type Props = {
  children: ReactNode;
  loading?: boolean;
  empty?: boolean;
  emptyMessage?: string;
  footer?: ReactNode;
  className?: string;
};

export default function DataTable({
  children,
  loading = false,
  empty = false,
  emptyMessage = "No data",
  footer,
  className = "",
}: Props) {
  const scrollRef = useRef<HTMLDivElement>(null);

  const [showLeft, setShowLeft] = useState(false);
  const [showRight, setShowRight] = useState(true);

  function handleScroll() {
    const el = scrollRef.current;
    if (!el) return;

    const { scrollLeft, scrollWidth, clientWidth } = el;

    setShowLeft(scrollLeft > 0);
    setShowRight(scrollLeft + clientWidth < scrollWidth - 1);
  }

  useEffect(() => {
    handleScroll();
  }, []);

  return (
    <>
      <div className={`relative rounded-lg border border-(--color-border) bg-(--color-surface) ${className}`}>
        {showLeft && (
          <div className="pointer-events-none absolute inset-y-0 left-0 w-6 bg-linear-to-r from-(--color-surface) to-transparent" />
        )}

        {showRight && (
          <div className="pointer-events-none absolute inset-y-0 right-0 w-6 bg-linear-to-l from-(--color-surface) to-transparent" />
        )}

        <div ref={scrollRef} onScroll={handleScroll} className="overflow-x-auto">
          <table className="min-w-225 w-full table-fixed">
            {children}

            {loading && <TableSkeleton />}

            {!loading && empty && (
              <tbody>
                <tr>
                  <td
                    colSpan={999}
                    className="px-4 py-10 text-center text-sm text-(--color-text-muted)"
                  >
                    {emptyMessage}
                  </td>
                </tr>
              </tbody>
            )}
          </table>
        </div>
      </div>

      {footer && (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
          {footer}
        </div>
      )}
    </>
  );
}