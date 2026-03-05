"use client";

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
    definitions.forEach((d) => setFilter(d.key, ""));
    triggerQuery?.();
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") {
      e.preventDefault();
      triggerQuery?.();
    }
  }

  return (
    <div className="border rounded p-4 grid gap-4 md:grid-cols-3">
      {definitions.map((d) => (
        <div key={String(d.key)} className="flex flex-col gap-1">
          <label className="text-xs font-semibold">{d.label}</label>

          <input
            className="border rounded px-2 py-1"
            value={filters[d.key]}
            onChange={(e) => setFilter(d.key, e.target.value)}
            onKeyDown={handleKeyDown}
          />
        </div>
      ))}

      <div className="md:col-span-3 flex justify-end gap-2">
        <button
          type="button"
          className="border rounded px-3 py-1 text-sm"
          onClick={() => triggerQuery?.()}
        >
          Apply Filters
        </button>

        <button
          type="button"
          className="border rounded px-3 py-1 text-sm"
          onClick={reset}
        >
          Reset Filters
        </button>
      </div>
    </div>
  );
}