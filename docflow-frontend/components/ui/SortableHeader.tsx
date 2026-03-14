"use client";

import { ChevronUp, ChevronDown } from "lucide-react";

type Props = {
  label: string;
  field: string;
  activeSort: string;
  direction: "ASC" | "DESC";
  onSortChange: (field: string) => void;
};

export default function SortableHeader({
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
      className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide cursor-pointer select-none"
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