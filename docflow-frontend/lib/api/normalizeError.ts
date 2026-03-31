import { ApiError } from "@/lib/apiErrors";

export function normalizeError(error: unknown): ApiError {
  // Already correct
  if (error instanceof ApiError) {
    return error;
  }

  // Native error (network, runtime, etc.)
  if (error instanceof Error) {
    return new ApiError(error.message, 0);
  }

  // Unknown shape
  return new ApiError("Unexpected error occurred", 0);
}