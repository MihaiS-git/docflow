"use client";

import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";

type Props = {
  from: string;
  to: string;
  size: number;
  onFromChange: (v: string) => void;
  onToChange: (v: string) => void;
  onSizeChange: (v: number) => void;
  onQuery: () => void;
  loading: boolean;
};

export function AuditRangePanel({
  from,
  to,
  size,
  onFromChange,
  onToChange,
  onSizeChange,
  onQuery,
  loading,
}: Props) {
  return (
    <div className="flex flex-wrap items-end gap-4">
      <Input
        label="From"
        type="datetime-local"
        step={1}
        value={from}
        onChange={(e) => onFromChange(e.target.value)}
        className="w-full sm:w-56"
      />

      <Input
        label="To"
        type="datetime-local"
        step={1}
        value={to}
        onChange={(e) => onToChange(e.target.value)}
        className="w-full sm:w-56"
      />

      <Input
        label="Size"
        type="number"
        min={1}
        max={500}
        value={String(size)}
        onChange={(e) => onSizeChange(Number(e.target.value))}
        className="w-full sm:w-32"
      />

      <Button 
      variant="outline"
      type="button" 
      loading={loading} 
      onClick={onQuery}
      >
        Query
      </Button>
    </div>
  );
}