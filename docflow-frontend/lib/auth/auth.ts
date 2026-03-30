import { apiFetch } from "@/lib/apiFetch";
import { AuthUser } from "@/types/auth/AuthUser";
import { LocalUser } from "@/types/auth/LocalUser";
import { AuthUserSchema } from "../validation/auth.schema";

// Identity projection (Keycloak claims + roles)
export async function fetchIdentity(): Promise<AuthUser | null> {
  const res = await apiFetch<unknown>("/api/auth/me", {
     allow401: true,
   });

  if (!res) return null;

  const parsed = AuthUserSchema.parse(res);

  return parsed;
}

// IMPORTANT:
// - returns LocalUser when ACTIVE
// - returns undefined on 204 (BOOTSTRAP)
export async function fetchLocalUser(): Promise<LocalUser | undefined> {
  return apiFetch<LocalUser | undefined>("/api/users/me");
}

export async function activateBootstrap(): Promise<void> {
  return apiFetch<void>("/api/bootstrap/activate", {
    method: "POST",
  });
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
