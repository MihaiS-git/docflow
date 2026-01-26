export type AuthErrorEvent =
  | { type: "401" }
  | {
      type: "403";
      errorCode:
        | "USER_LOCKED"
        | "USER_DISABLED"
        | "TENANT_SUSPENDED"
        | "ACCESS_DENIED"
        | string;
    };

let handler: ((event: AuthErrorEvent) => void) | null = null;

export function setAuthErrorHandler(
  next: ((event: AuthErrorEvent) => void) | null,
) {
  handler = next;
}

export function emitAuthError(event: AuthErrorEvent) {
  handler?.(event);
}
