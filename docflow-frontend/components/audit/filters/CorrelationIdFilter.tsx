interface Props {
  value: string;
  onChange: (v: string) => void;
}

export function CorrelationIdFilter({
  value,
  onChange,
}: Props) {
  return (
    <input
      className="border rounded px-2 py-1"
      placeholder="Correlation ID"
      value={value}
      onChange={(e) => onChange(e.target.value)}
    />
  );
}