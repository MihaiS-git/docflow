"use client";

type Props = {
  widths: (string | number)[];
};

export default function TableColumnWidths({ widths }: Props) {
  return (
    <colgroup>
      {widths.map((w, i) => (
        <col
          key={i}
          style={{
            width: typeof w === "number" ? `${w}px` : w,
          }}
        />
      ))}
    </colgroup>
  );
}