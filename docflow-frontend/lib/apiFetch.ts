import { ApiError, ForbiddenError, UnauthenticatedError } from "./apiErrors";

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
  const url = `${baseUrl}${path.startsWith("/") ? path : `/${path}`}`;

  const res = await fetch(url, {
    ...init,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
  });

  if (res.status === 401) {
    throw new UnauthenticatedError();
  }

  if (res.status === 403) {
    const body = (await res.json()) as { errorCode?: string };
    throw new ForbiddenError(body.errorCode ?? "ACCESS_DENIED");
  }

  if (!res.ok) {
    throw new ApiError(`API error ${res.status}`, res.status);
  }

  return res.json();
}
