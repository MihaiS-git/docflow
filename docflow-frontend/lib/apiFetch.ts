import { ApiError, ForbiddenError, UnauthenticatedError } from "./apiErrors";
import { emitAuthError } from "./authEvents";

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();

  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    await ensureCsrf();
  }

  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
  const url = `${baseUrl}${path.startsWith("/") ? path : `/${path}`}`;

  const csrfToken = getCookie("XSRF-TOKEN");

  const res = await fetch(url, {
    ...init,
    credentials: "include",
    headers: {
      ...(init.headers ?? {}),
      ...(csrfToken ? { "X-XSRF-TOKEN": csrfToken } : {}),
      ...(init.body ? { "Content-Type": "application/json" } : {}),
    },
  });

  if (res.status === 401) {
    emitAuthError({ type: "401" });
    throw new UnauthenticatedError();
  }

  if (res.status === 403) {
    let errorCode = "ACCESS_DENIED";

    try {
      const body = (await res.json()) as { errorCode?: string };
      errorCode = body.errorCode ?? errorCode;
    } catch {
      // CSRF or filter-level 403s may have no body
    }

    emitAuthError({ type: "403", errorCode });
    throw new ForbiddenError(errorCode);
  }

  if (!res.ok) {
    throw new ApiError(`API error ${res.status}`, res.status);
  }

  // support 204 No Content
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

function getCookie(name: string): string | undefined {
  const raw = document.cookie
    .split("; ")
    .find((row) => row.startsWith(name + "="))
    ?.split("=")[1];

  return raw ? decodeURIComponent(raw) : undefined;
}

let csrfPrimed = false;

async function ensureCsrf() {
  if (csrfPrimed) return;

  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
  const res = await fetch(`${baseUrl}/api/csrf`, {
    method: "GET",
    credentials: "include",
    cache: "no-store",
  });

  if (!res.ok) {
    throw new ApiError(`Failed to prime CSRF (${res.status})`, res.status);
  }

  csrfPrimed = true;
}
