import { apiFetch } from "@/lib/apiFetch";
import type { AuthUser } from "@/types/AuthUser";


export async function getCurrentUser({ redirect = false } = {}) {
  try {
    return await apiFetch<AuthUser>("/api/auth/me");
  } catch {
    if (redirect) login();
    return null;
  }
}

export function login() {
  window.location.href =
    `${process.env.NEXT_PUBLIC_API_BASE_URL}/oauth2/authorization/keycloak`;
}

export async function logout() {
  await fetch(
    `${process.env.NEXT_PUBLIC_API_BASE_URL}/api/auth/logout`,
    {
      method: "POST",
      credentials: "include",
    }
  );
}
