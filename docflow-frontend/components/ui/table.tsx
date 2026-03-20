"use client";

import {
  HTMLAttributes,
  TableHTMLAttributes,
  TdHTMLAttributes,
  ThHTMLAttributes,
} from "react";

/* =========================================================
   TYPES
========================================================= */

type Density = "compact" | "comfortable" | "spacious";
type RowVariant = "default" | "clickable";
type Align = "left" | "center" | "right";

/* =========================================================
   HELPERS
========================================================= */

function densityClasses(density: Density) {
  switch (density) {
    case "compact":
      return "p-1.5";
    case "spacious":
      return "p-3";
    default:
      return "p-2";
  }
}

function alignClasses(align?: Align) {
  switch (align) {
    case "center":
      return "text-center";
    case "right":
      return "text-right";
    default:
      return "text-left";
  }
}

/* =========================================================
   ROOT
========================================================= */

export function TableContainer({
  className = "",
  ...props
}: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`
        overflow-auto
        rounded-md
        border border-(--color-table-border)
        bg-(--color-surface)
        ${className}
      `}
      {...props}
    />
  );
}

export function Table({
  className = "",
  ...props
}: TableHTMLAttributes<HTMLTableElement>) {
  return (
    <table
      className={`
        min-w-full
        text-xs
        ${className}
      `}
      {...props}
    />
  );
}

/* =========================================================
   HEAD
========================================================= */

export function TableHead(props: HTMLAttributes<HTMLTableSectionElement>) {
  return (
    <thead className="sticky top-0 z-10 bg-(--color-table-header)" {...props} />
  );
}

export function TableHeaderRow(props: HTMLAttributes<HTMLTableRowElement>) {
  return <tr {...props} />;
}

type TableHeaderCellProps = ThHTMLAttributes<HTMLTableCellElement> & {
  align?: Align;
  density?: Density;
};

export function TableHeaderCell({
  className = "",
  align,
  density = "comfortable",
  ...props
}: TableHeaderCellProps) {
  return (
    <th
      className={`
        ${densityClasses(density)}
        ${alignClasses(align)}
        font-medium
        text-(--color-text-secondary)
        border-b border-(--color-table-border)
        bg-(--color-table-header)
        ${className}
      `}
      {...props}
    />
  );
}

/* =========================================================
   BODY
========================================================= */

export function TableBody(props: HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody {...props} />;
}

type TableRowProps = HTMLAttributes<HTMLTableRowElement> & {
  variant?: RowVariant;
};

export function TableRow({
  className = "",
  variant = "default",
  ...props
}: TableRowProps) {
  return (
    <tr
      className={`
        even:bg-(--color-table-row)
        transition-colors
        hover:bg-(--color-table-row-hover)
        ${variant === "clickable" ? "cursor-pointer" : ""}
        ${className}
      `}
      {...props}
    />
  );
}

type TableCellProps = TdHTMLAttributes<HTMLTableCellElement> & {
  align?: Align;
  density?: Density;
  width?: string;
};

export function TableCell({
  className = "",
  align,
  density = "comfortable",
  width,
  ...props
}: TableCellProps) {
  return (
    <td
      className={`
        ${densityClasses(density)}
        ${alignClasses(align)}
        border-b border-(--color-table-border)
        ${width ? width : ""}
        ${className}
      `}
      {...props}
    />
  );
}

/* =========================================================
   OPTIONAL (non-breaking utilities)
========================================================= */

export function TableEmptyState({ children }: { children: React.ReactNode }) {
  return (
    <div className="p-4 text-sm text-(--color-text-muted)">{children}</div>
  );
}
