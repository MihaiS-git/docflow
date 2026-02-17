export function toIsoOrThrow(dtLocal: string, fieldName: string): string {
  const d = new Date(dtLocal);
  if (Number.isNaN(d.getTime())) {
    throw new Error(`Invalid ${fieldName} datetime value`);
  }
  return d.toISOString();
}

export function buildRangeQueryParams(params: {
  from: string;
  to: string;
}): URLSearchParams {
  const qs = new URLSearchParams();

  qs.set("from", toIsoOrThrow(params.from, "from"));
  qs.set("to", toIsoOrThrow(params.to, "to"));

  return qs;
}
