import { SelectHTMLAttributes } from "react";

type Props = SelectHTMLAttributes<HTMLSelectElement>;

export default function Select({ className = "", children, ...props }: Props) {
  return (
    <select
      className={`
        w-full
        p-2
        rounded
        border border-(--color-border)
        bg-(--color-surface)
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