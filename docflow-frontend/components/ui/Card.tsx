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
        <h2 className="mb-3 text-md font-bold text-(--color-text-secondary)">
          {title}
        </h2>
      )}

      {children}
    </section>
  );
}