import React from "react";

type Props = {
  title?: string;
  description?: string;
  className?: string;
};

export default function EmptyState({
  title,
  description,
  className,
}: Props) {
  return (
    <div
      className={`flex flex-col items-center justify-center py-10 text-center ${className ?? ""}`}
    >
      {title && (
        <h3 className="text-sm font-semibold text-(--color-text-primary)">
          {title}
        </h3>
      )}

      {description && (
        <p className="mt-2 text-sm text-(--color-text-muted)">
          {description}
        </p>
      )}
    </div>
  );
}