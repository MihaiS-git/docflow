import ClientShell from "./ClientShell";

export default function ProtectedLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <ClientShell>{children}</ClientShell>;
}
