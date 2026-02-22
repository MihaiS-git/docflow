"use client";

import { ApiError } from "@/lib/apiErrors";
import { ErrorResponse } from "@/lib/api/ErrorResponse";
import { toast } from "sonner";

function parseFilenameFromContentDisposition(
  disposition: string | null,
): string | null {
  if (!disposition) return null;

  // Prefer RFC 5987: filename*=UTF-8''...
  // Example: attachment; filename*=UTF-8''audit-export.jsonl
  const filenameStar = disposition.match(/filename\*\s*=\s*([^;]+)/i)?.[1];
  if (filenameStar) {
    const v = filenameStar.trim();
    const m = /^UTF-8''(.+)$/.exec(v);
    const encoded = (m?.[1] ?? v).replace(/^"(.*)"$/, "$1");
    try {
      return decodeURIComponent(encoded);
    } catch {
      // fall back to raw
      return encoded;
    }
  }

  // Fallback: filename="..."
  const filename = disposition.match(/filename\s*=\s*("?)([^"]+)\1/i)?.[2];
  return filename?.trim() ?? null;
}

export async function downloadAuditFile(params: {
  path: string;
  filename: string;
}): Promise<void> {
  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
  const url = `${baseUrl}${params.path.startsWith("/") ? params.path : `/${params.path}`}`;

  try {
    const res = await fetch(url, {
      method: "GET",
      credentials: "include",
    });

    if (!res.ok) {
      let body: ErrorResponse | undefined;

      try {
        body = (await res.json()) as ErrorResponse;
      } catch {
        // ignore parsing failure
      }

      throw new ApiError(
        body?.message ?? `Export failed (${res.status})`,
        res.status,
        body,
      );
    }

    const blob = await res.blob();

    const disposition = res.headers.get("Content-Disposition");
    const serverFilename = parseFilenameFromContentDisposition(disposition);

    const filename = serverFilename || params.filename;

    const objectUrl = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = objectUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(objectUrl);
  } catch (e) {
    if (e instanceof ApiError) {
      toast.error(e.message);
    } else {
      toast.error("Export failed");
    }
  }
}