import Link from "next/link";

export default function AuditConsolePage() {
  const streams = [
    { name: "Authentication", path: "authentication" },
    { name: "Credential Lifecycle", path: "credential-lifecycle" },
    { name: "Lifecycle Denied", path: "lifecycle-denied" },
    { name: "RBAC Denied", path: "rbac-denied" },
    { name: "Sensitive Access", path: "sensitive-access" },
    { name: "Admin", path: "admin" },
    { name: "Onboarding", path: "onboarding" },
    { name: "Unauthenticated Access", path: "unauthenticated" },
    { name: "Identity Projection", path: "identity-projection" },
    { name: "Retention Policies", path: "retention" },
    { name: "Legal Holds", path: "legal-holds" },
  ];

  return (
    <div className="p-6 space-y-6">
      <h1 className="text-xl font-bold">Audit Console</h1>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {streams.map((stream) => (
          <Link
            key={stream.path}
            href={`/console/audit/${stream.path}`}
            className="border rounded p-4 hover:bg-gray-50 transition"
          >
            <div className="font-semibold">{stream.name}</div>
            <div className="text-sm text-gray-500">
              View and validate {stream.name} audit stream
            </div>
          </Link>
        ))}

        <Link
          href="/console/audit/exports"
          className="border rounded p-4 hover:bg-gray-50 transition"
        >
          <div className="font-semibold">Export Snapshots</div>
          <div className="text-sm text-gray-500">
            Registry and verification of sealed JSONL exports
          </div>
        </Link>

        {/* 🔐 Signing Keys Management */}
        <Link
          href="/console/security/audit-keys"
          className="border rounded p-4 hover:bg-gray-50 transition border-red-200"
        >
          <div className="font-semibold">
            Export Signing Keys
          </div>
          <div className="text-sm text-gray-500">
            Register, rotate and activate JSONL export signing keys
          </div>
        </Link>
      </div>
    </div>
  );
}