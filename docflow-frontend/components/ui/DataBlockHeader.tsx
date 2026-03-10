import { ReactNode } from "react";

type DataBlockHeaderProps = {
  title?: string;
  actions?: ReactNode;
};

export function DataBlockHeader({ title, actions }: DataBlockHeaderProps) {
  return (
    <div className="flex items-center justify-between px-4 py-3 border-b border-(--color-border) bg-(--color-surface-alt)">
      {title && (
        <h2 className="text-sm font-medium text-(--color-text-primary)">
          {title}
        </h2>
      )}

      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}