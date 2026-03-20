import { ErrorResponse } from "./api/ErrorResponse";
import { ApiError, ForbiddenError, UnauthenticatedError } from "./apiErrors";
import { emitAuthError } from "./auth/authEvents";

function isBodyFormData(body: BodyInit | null | undefined): body is FormData {
  return typeof FormData !== "undefined" && body instanceof FormData;
}

function isBodyBinary(body: BodyInit | null | undefined): boolean {
  return (
    body instanceof Blob ||
    body instanceof ArrayBuffer ||
    (typeof ArrayBuffer !== "undefined" && ArrayBuffer.isView(body))
  );
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit & {
    csrfMode?: "default" | "anonymous";
    allow401?: boolean;
  } = {},
): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();

  if (
    init.csrfMode !== "anonymous" &&
    !["GET", "HEAD", "OPTIONS"].includes(method)
  ) {
    await ensureCsrf();
  }

  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
  const url = `${baseUrl}${path.startsWith("/") ? path : `/${path}`}`;

  const csrfToken = getCookie("XSRF-TOKEN");
  const body = init.body ?? null;

  const shouldSetJsonContentType =
    body != null &&
    typeof body === "string" &&
    !isBodyFormData(body) &&
    !isBodyBinary(body);

  const res = await fetch(url, {
    ...init,
    credentials: "include",
    headers: {
      ...(init.headers ?? {}),
      ...(csrfToken ? { "X-XSRF-TOKEN": csrfToken } : {}),
      ...(shouldSetJsonContentType
        ? { "Content-Type": "application/json" }
        : {}),
    },
    body: body ?? undefined,
  });

  if (res.status === 401) {
    if (init.allow401) {
      return undefined as T;
    }

    emitAuthError({ type: "401" });
    throw new UnauthenticatedError();
  }

  if (res.status === 403) {
    let body: ErrorResponse | undefined;

    try {
      body = (await res.json()) as ErrorResponse;
    } catch {}

    if (!body) {
      body = {
        status: 403,
        error: "Forbidden",
        errorCode: "ACCESS_DENIED",
        message: "Access denied",
        path: path,
      };
    }

    if (body.errorCode !== "SELF_ACTION_FORBIDDEN") {
      emitAuthError({ type: "403", errorCode: body.errorCode });
    }

    throw new ForbiddenError(body);
  }

  if (!res.ok) {
    let body: ErrorResponse | undefined;

    try {
      body = (await res.json()) as ErrorResponse;
    } catch {}

    throw new ApiError(
      body?.message ?? `API error ${res.status}`,
      res.status,
      body,
    );
  }

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
