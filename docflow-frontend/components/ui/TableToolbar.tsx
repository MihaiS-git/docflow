"use client";

import { ReactNode } from "react";

type Props = {
  filters?: ReactNode;
  actions?: ReactNode;
};

export default function TableToolbar({ filters, actions }: Props) {
  return (
    <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
      {filters && <div className="flex flex-wrap items-end gap-3">{filters}</div>}

      {actions && <div className="flex items-center gap-2 lg:ml-auto">{actions}</div>}
    </div>
  );
}