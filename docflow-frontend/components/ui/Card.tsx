import { ReactNode } from "react";

type Padding = "default" | "none";

type Props = {
  children: ReactNode;
  title?: ReactNode;
  className?: string;
  padding?: Padding;
};

export default function Card({
  children,
  title,
  className = "",
  padding = "default",
}: Props) {
  const paddingClass =
    padding === "none" ? "p-0" : "p-4";

  return (
    <section
      className={`
        bg-(--color-surface)
        border border-(--color-border)
        rounded-lg
        w-full
        ${paddingClass}
        ${className}
      `}
    >
      {title && (
        <div className="mb-3 text-sm font-semibold text-(--color-text-secondary)">
          {title}
        </div>
      )}

      {children}
    </section>
  );
}