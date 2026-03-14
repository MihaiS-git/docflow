import { ReactNode } from "react";

type Props = {
  children: ReactNode;
  title?: ReactNode;
  className?: string;
};

export default function Card({ children, title, className = "" }: Props) {
  return (
    <section
      className={`
        bg-(--color-surface)
        border border-(--color-border)
        rounded-lg
        p-4
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