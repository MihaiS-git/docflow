import { InputHTMLAttributes } from "react";

type Props = InputHTMLAttributes<HTMLInputElement>;

export default function Input({ className = "", ...props }: Props) {
  return (
    <input
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
    />
  );
}