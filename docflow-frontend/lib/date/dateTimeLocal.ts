function pad2(n: number): string {
  return String(n).padStart(2, "0");
}

function pad3(n: number): string {
  return String(n).padStart(3, "0");
}

/**
 * For <input type="datetime-local">
 * Produces: YYYY-MM-DDTHH:mm:ss (local time)
 */
export function toDateTimeLocalString(date: Date): string {
  return (
    date.getFullYear() +
    "-" +
    pad2(date.getMonth() + 1) +
    "-" +
    pad2(date.getDate()) +
    "T" +
    pad2(date.getHours()) +
    ":" +
    pad2(date.getMinutes()) +
    ":" +
    pad2(date.getSeconds())
  );
}

/**
 * For audit table display (deterministic, local time)
 * Produces: YYYY-MM-DD HH:mm:ss.SSS
 */
export function formatAuditTimestamp(isoUtc: string): string {
  const d = new Date(isoUtc);

  return (
    d.getFullYear() +
    "-" +
    pad2(d.getMonth() + 1) +
    "-" +
    pad2(d.getDate()) +
    " " +
    pad2(d.getHours()) +
    ":" +
    pad2(d.getMinutes()) +
    ":" +
    pad2(d.getSeconds()) +
    "." +
    pad3(d.getMilliseconds())
  );
}
