"use client";

import Button from "@/components/ui/Button";

type Props = {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
};

export default function Pagination({
  page,
  totalPages,
  onPageChange,
}: Props) {
  if (totalPages <= 1) return null;

  const isFirst = page === 0;
  const isLast = page >= totalPages - 1;

  const mobileWindow = 1;
  const desktopWindow = 2;

  const window = desktopWindow;

  const start = Math.max(0, page - window);
  const end = Math.min(totalPages - 1, page + window);

  const pages: (number | "ellipsis")[] = [];

  if (start > 0) {
    pages.push(0);
    if (start > 1) pages.push("ellipsis");
  }

  for (let i = start; i <= end; i++) pages.push(i);

  if (end < totalPages - 1) {
    if (end < totalPages - 2) pages.push("ellipsis");
    pages.push(totalPages - 1);
  }

  return (
    <div className="flex flex-wrap items-center justify-center gap-2 text-sm">
      <Button
        size="sm"
        variant="secondary"
        disabled={isFirst}
        onClick={() => onPageChange(0)}
        className="hidden sm:inline-flex"
      >
        First
      </Button>

      <Button
        size="sm"
        variant="secondary"
        disabled={isFirst}
        onClick={() => onPageChange(page - 1)}
      >
        Prev
      </Button>

      {pages.map((p, i) =>
        p === "ellipsis" ? (
          <span key={i} className="px-2 text-(--color-text-muted)">
            …
          </span>
        ) : (
          <Button
            key={p}
            size="sm"
            variant={p === page ? "primary" : "secondary"}
            onClick={() => onPageChange(p)}
            className={
              Math.abs(p - page) > mobileWindow ? "hidden sm:inline-flex" : ""
            }
          >
            {p + 1}
          </Button>
        )
      )}

      <Button
        size="sm"
        variant="secondary"
        disabled={isLast}
        onClick={() => onPageChange(page + 1)}
      >
        Next
      </Button>

      <Button
        size="sm"
        variant="secondary"
        disabled={isLast}
        onClick={() => onPageChange(totalPages - 1)}
        className="hidden sm:inline-flex"
      >
        Last
      </Button>
    </div>
  );
}