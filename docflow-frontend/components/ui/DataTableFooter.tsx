"use client";

import Pagination from "@/components/ui/Pagination";
import Select from "@/components/ui/Select";

type Props = {
  page: number;
  pageSize: number;
  totalPages: number;
  totalElements: number;

  onPageChange: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
};

export default function DataTableFooter({
  page,
  pageSize,
  totalPages,
  totalElements,
  onPageChange,
  onPageSizeChange,
}: Props) {
  const start = totalElements === 0 ? 0 : page * pageSize + 1;
  const end = Math.min(totalElements, (page + 1) * pageSize);

  return (
    <div className="flex flex-col items-center gap-3 text-sm">
      <div className="flex flex-wrap items-center justify-center gap-4 text-(--color-text-secondary)">
        <span>
          Showing {start}–{end} of {totalElements}
        </span>

        {onPageSizeChange && (
          <div className="flex items-center gap-2">
            <span>Rows</span>

            <Select
              value={String(pageSize)}
              onChange={(e) => onPageSizeChange(Number(e.target.value))}
              className="w-20"
            >
              <option value="25">25</option>
              <option value="50">50</option>
              <option value="100">100</option>
              <option value="200">200</option>
            </Select>
          </div>
        )}
      </div>

      <Pagination
        page={page}
        totalPages={totalPages}
        onPageChange={onPageChange}
      />
    </div>
  );
}