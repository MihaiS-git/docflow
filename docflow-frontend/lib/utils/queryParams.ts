export function safeParam(
  value: string | undefined,
  shouldBlock: boolean,
): string | undefined {
  if (shouldBlock) return undefined;

  if (!value) return undefined;

  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}