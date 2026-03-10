import { ReactNode } from "react";

type SystemPageProps = {
  title: string;
  description?: string;
  children?: ReactNode;
};

export default function SystemPage({
  title,
  description,
  children,
}: SystemPageProps) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-(--color-bg) px-6">
      <div className="w-full max-w-md bg-(--color-surface) border border-(--color-border) rounded-lg shadow-sm p-8 text-center space-y-4">
        <h1 className="text-xl font-semibold text-(--color-text-primary)">
          {title}
        </h1>

        {description && (
          <p className="text-sm text-(--color-text-secondary)">
            {description}
          </p>
        )}

        {children}
      </div>
    </div>
  );
}