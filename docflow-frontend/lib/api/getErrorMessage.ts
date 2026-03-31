import { normalizeError } from "./normalizeError";

export function getErrorMessage(error: unknown): string {
  const err = normalizeError(error);

  return err.response?.message ?? err.message;
}
