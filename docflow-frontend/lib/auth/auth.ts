import { apiFetch } from "@/lib/apiFetch";
import { AuthUser } from "@/types/auth/AuthUser";
import { LocalUser } from "@/types/auth/LocalUser";

// Identity projection (Keycloak claims + roles)
export async function fetchIdentity(): Promise<AuthUser> {
  return apiFetch<AuthUser>("/api/auth/me");
}

export async function fetchLocalUser(): Promise<LocalUser> {
  return apiFetch<LocalUser>("/api/users/me");
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
