export function downloadAuditFile(params: {
  path: string;
  filename: string; // server defines final filename via Content-Disposition
}): void {
  const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
  const url = `${baseUrl}${params.path.startsWith("/") ? params.path : `/${params.path}`}`;

  // Audit-grade behavior:
  // - Single GET
  // - Server streams attachment
  // - No JS access to content
  // - No double export
  // - No additional audit noise
  // - Native browser handling

  window.location.assign(url);
}
