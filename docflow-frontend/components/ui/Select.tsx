"use client";

import { SelectHTMLAttributes, memo } from "react";

type Props = SelectHTMLAttributes<HTMLSelectElement>;

function SelectComponent({ className = "", children, ...props }: Props) {
  return (
    <select
      className={`
        w-full
        rounded
        border border-(--color-border)
        bg-(--color-surface)
        p-2
        text-(--color-text-primary)
        focus:outline-none
        focus:ring-2
        focus:ring-(--color-primary)
        ${className}
      `}
      {...props}
    >
      {children}
    </select>
  );
}

export default memo(SelectComponent);