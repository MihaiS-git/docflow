"use client";

import { useRef, useState } from "react";
import { createPortal } from "react-dom";
import Button from "@/components/ui/Button";
import { useDismissibleLayer } from "@/hooks/useDismissibleLayer";

type Props = {
  children: (ctx: { close: () => void }) => React.ReactNode;
  width?: number;
  menuHeight?: number;
  ariaLabel?: string;
};

export default function RowActionMenu({
  children,
  width = 160,
  menuHeight = 160,
  ariaLabel = "Row actions",
}: Props) {
  const [open, setOpen] = useState(false);
  const [style, setStyle] = useState<React.CSSProperties>({});

  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);

  const close = () => setOpen(false);

  const toggle = () => {
    const rect = buttonRef.current?.getBoundingClientRect();
    if (!rect) return;

    const spaceBelow = window.innerHeight - rect.bottom;
    const openUp = spaceBelow < menuHeight;

    const top = openUp ? rect.top - menuHeight - 4 : rect.bottom + 4;
    const left = rect.right - width;

    setStyle({
      position: "fixed",
      top,
      left,
      width,
      zIndex: 1000,
    });

    setOpen((v) => !v);
  };

  useDismissibleLayer({
    open,
    onClose: close,
    triggerRef: buttonRef,
    contentRef: menuRef,
  });

  return (
    <>
      <Button
        ref={buttonRef}
        size="icon"
        variant="ghost"
        aria-label={ariaLabel}
        onClick={toggle}
      >
        ⋯
      </Button>

      {open &&
        createPortal(
          <div
            ref={menuRef}
            style={style}
            className="rounded-md border border-(--color-border) bg-(--color-surface) shadow-md"
          >
            {children({ close })}
          </div>,
          document.body
        )}
    </>
  );
}