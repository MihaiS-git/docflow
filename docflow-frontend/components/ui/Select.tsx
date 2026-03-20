"use client";

import { SelectHTMLAttributes, ReactNode, memo } from "react";

type Props = SelectHTMLAttributes<HTMLSelectElement> & {
  label?: ReactNode;
};

function SelectComponent({ label, className = "", children, ...props }: Props) {
  const select = (
    <select
      className={`
        w-full
        rounded
        border border-(--color-border)
        bg-(--color-surface)
        h-9
        px-2
        text-(--color-text-primary)
        cursor-pointer
        focus:outline-none
        focus:ring-2
        focus:ring-(--color-primary)
        disabled:cursor-not-allowed
        disabled:opacity-60
        ${className}
      `}
      {...props}
    >
      {children}
    </select>
  );

  if (!label) return select;

  return (
    <label className="flex flex-col gap-1 text-xs text-(--color-text-muted)">
      <span>{label}</span>
      {select}
    </label>
  );
}

export default memo(SelectComponent);