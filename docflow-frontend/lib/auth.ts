import { apiFetch } from "@/lib/apiFetch";
import type { AuthUser } from "@/types/AuthUser";
import { ForbiddenError, UnauthenticatedError } from "./apiErrors";

export type AuthResult =
  | { state: "AUTH"; user: AuthUser }
  | { state: "ANON" }
  | { state: "BLOCKED"; errorCode: string };

export async function getCurrentUser(
  { redirect = false, retry = true } = {}
): Promise<AuthResult> {
  try {
    const user = await apiFetch<AuthUser>("/api/auth/me");
    return { state: "AUTH", user };
  } catch (err) {
    if (err instanceof UnauthenticatedError) {
      if (retry) {
        // Give Spring Security time to finalize the session
        await new Promise((r) => setTimeout(r, 300));
        return getCurrentUser({ redirect, retry: false });
      }

      if (redirect) {
        window.location.href =
          `${process.env.NEXT_PUBLIC_API_BASE_URL}/oauth2/authorization/keycloak`;
      }

      return { state: "ANON" };
    }

    if (err instanceof ForbiddenError) {
      return { state: "BLOCKED", errorCode: err.errorCode };
    }

    throw err;
  }
}


export function login() {
  window.location.href = `${process.env.NEXT_PUBLIC_API_BASE_URL}/oauth2/authorization/keycloak`;
}

export async function logout() {
  const form = document.createElement("form");
  form.method = "POST";
  form.action = `${process.env.NEXT_PUBLIC_API_BASE_URL}/api/auth/logout`;

  document.body.appendChild(form);
  form.submit();
}
