export function isValidCorrelationId(value: string) {
  return /^[a-zA-Z0-9\-_:]+$/.test(value) && value.length <= 64;
}