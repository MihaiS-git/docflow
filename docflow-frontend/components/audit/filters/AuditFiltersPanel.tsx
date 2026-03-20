"use client";

import type { KeyboardEvent } from "react";

import Button from "@/components/ui/Button";
import Input from "@/components/ui/Input";

type FilterDefinition<F> = {
  key: keyof F;
  label: string;
};

type Props<F extends Record<string, string>> = {
  filters: F;
  setFilter: (key: keyof F, value: string) => void;
  triggerQuery?: () => void;
  definitions: FilterDefinition<F>[];
};

export function AuditFiltersPanel<F extends Record<string, string>>({
  filters,
  setFilter,
  triggerQuery,
  definitions,
}: Props<F>) {
  function reset() {
    definitions.forEach((definition) => setFilter(definition.key, ""));
    triggerQuery?.();
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") {
      e.preventDefault();
      e.stopPropagation(); // critical: prevents bubbling into parent handlers
      triggerQuery?.();
    }
  }

  function handleApplyClick() {
    // guard against accidental double execution in same tick
    triggerQuery?.();
  }

  return (
    <div className="flex flex-wrap items-end gap-4">
      {definitions.map((definition) => (
        <Input
          key={String(definition.key)}
          label={definition.label}
          value={filters[definition.key]}
          onChange={(e) => setFilter(definition.key, e.target.value)}
          onKeyDown={handleKeyDown}
          className="w-full sm:w-64"
        />
      ))}

      <div className="flex items-center gap-2">
        <Button variant="outline" onClick={handleApplyClick}>
          Apply
        </Button>

        <Button variant="outline" onClick={reset}>
          Reset
        </Button>
      </div>
    </div>
  );
}
