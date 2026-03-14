"use client";

import { RefObject, useEffect } from "react";

type Params = {
  open: boolean;
  onClose: () => void;
  triggerRef?: RefObject<HTMLElement | null>;
  contentRef?: RefObject<HTMLElement | null>;
};

export function useDismissibleLayer({
  open,
  onClose,
  triggerRef,
  contentRef,
}: Params) {
  useEffect(() => {
    if (!open) return;

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target as Node | null;
      if (!target) return;

      if (contentRef?.current?.contains(target)) return;
      if (triggerRef?.current?.contains(target)) return;

      onClose();
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };

    document.addEventListener("pointerdown", handlePointerDown, true);
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown, true);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, onClose, triggerRef, contentRef]);
}