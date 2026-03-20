"use client";

import {
  InputHTMLAttributes,
  ReactNode,
  memo,
  useId,
} from "react";

type Props = InputHTMLAttributes<HTMLInputElement> & {
  label?: ReactNode;
  invalid?: boolean;
};

function InputComponent({
  label,
  className = "",
  invalid,
  id,
  type = "text",
  ...props
}: Props) {
  const generatedId = useId();
  const inputId = id ?? generatedId;

  const input = (
    <input
      id={inputId}
      type={type}
      className={`
        w-full
        rounded
        border border-(--color-border)
        bg-(--color-surface)
        h-9 px-2
        text-(--color-text-primary)
        ${type === "date" ? "cursor-pointer" : "cursor-text"}
        focus:outline-none
        focus:ring-2
        focus:ring-(--color-primary)
        disabled:cursor-not-allowed
        disabled:opacity-60
        ${invalid ? "border-(--color-error) ring-(--color-error)" : ""}
        ${className}
      `}
      aria-invalid={invalid || undefined}
      {...props}
    />
  );

  if (!label) return input;

  return (
    <label
      htmlFor={inputId}
      className="flex flex-col gap-1 text-xs text-(--color-text-muted)"
    >
      <span>{label}</span>
      {input}
    </label>
  );
}

export default memo(InputComponent);