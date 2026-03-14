"use client";

import { ReactNode, useEffect } from "react";
import { createPortal } from "react-dom";

type Props = {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
};

export default function Dialog({ open, onClose, title, children }: Props) {
  useEffect(() => {
    if (!open) return;

    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      }
    };

    document.addEventListener("keydown", handleKey);

    return () => {
      document.removeEventListener("keydown", handleKey);
    };
  }, [open, onClose]);

  if (!open) return null;

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* backdrop */}
      <div
        className="absolute inset-0 bg-black/40"
        onClick={onClose}
      />

      {/* dialog */}
      <div className="relative w-full max-w-lg rounded-lg bg-(--color-surface) border border-(--color-border) shadow-lg p-6">
        {title && (
          <h2 className="text-lg font-semibold mb-4 text-(--color-text-primary)">
            {title}
          </h2>
        )}

        {children}
      </div>
    </div>,
    document.body
  );
}