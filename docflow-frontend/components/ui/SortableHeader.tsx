"use client";

import { ChevronDown, ChevronUp } from "lucide-react";
import { memo } from "react";

type Props = {
  label: string;
  field: string;
  activeSort: string;
  direction: "ASC" | "DESC";
  onSortChange: (field: string) => void;
};

function SortableHeaderComponent({
  label,
  field,
  activeSort,
  direction,
  onSortChange,
}: Props) {
  const active = activeSort === field;

  return (
    <th
      onClick={() => onSortChange(field)}
      className="cursor-pointer select-none px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide"
    >
      <span className="flex items-center gap-1">
        {label}
        {active &&
          (direction === "ASC" ? (
            <ChevronUp size={14} />
          ) : (
            <ChevronDown size={14} />
          ))}
      </span>
    </th>
  );
}

export default memo(SortableHeaderComponent);