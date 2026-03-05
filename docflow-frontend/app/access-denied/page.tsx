import AccessDeniedClient from "./AccessDeniedClient";

export default async function AccessDeniedPage({
  searchParams,
}: {
  searchParams: Promise<{ role?: string }>;
}) {
  const params = await searchParams;

  return <AccessDeniedClient role={params.role ?? null} />;
}