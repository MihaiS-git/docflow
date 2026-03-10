import { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "secondary" | "danger";

type Props = {
  children: ReactNode;
  variant?: Variant;
} & ButtonHTMLAttributes<HTMLButtonElement>;

export default function Button({
  children,
  variant = "primary",
  className = "",
  ...props
}: Props) {
  const base =
    "px-4 py-2 rounded-md hover:opacity-90 disabled:opacity-50";

  const variants: Record<Variant, string> = {
    primary: "bg-(--color-primary) text-(--color-text-inverse)",
    secondary: "bg-(--color-surface-alt) text-(--color-text-primary)",
    danger: "bg-(--color-error) text-(--color-text-inverse)",
  };

  return (
    <button
      className={`${base} ${variants[variant]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}