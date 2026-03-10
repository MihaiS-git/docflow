import { ReactNode } from "react";

type Props = {
  label: string;
  children: ReactNode;
  description?: string;
  error?: string | null;
};

export default function FormField({
  label,
  children,
  description,
  error,
}: Props) {
  return (
    <div className="space-y-1">
      <label className="block text-sm text-(--color-text-secondary)">
        {label}
      </label>

      {children}

      {description && !error && (
        <p className="text-xs text-(--color-text-muted)">
          {description}
        </p>
      )}

      {error && (
        <p className="text-xs text-(--color-error)">
          {error}
        </p>
      )}
    </div>
  );
}