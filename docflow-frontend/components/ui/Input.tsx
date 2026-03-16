"use client";

import { InputHTMLAttributes, ReactNode, memo } from "react";

type Props = InputHTMLAttributes<HTMLInputElement> & {
  label?: ReactNode;
};

function InputComponent({ label, className = "", ...props }: Props) {
  const input = (
    <input
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
    />
  );

  if (!label) return input;

  return (
    <label className="flex flex-col gap-1 text-xs text-(--color-text-muted)">
      <span>{label}</span>
      {input}
    </label>
  );
}

export default memo(InputComponent);