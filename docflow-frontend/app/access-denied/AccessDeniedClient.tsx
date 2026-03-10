"use client";

import SystemPage from "@/components/system/SystemPage";
import Link from "next/link";
import { useRouter } from "next/navigation";

export default function AccessDeniedClient({ role }: { role: string | null }) {
  const router = useRouter();

  return (
    <SystemPage title="Access denied">
      {role ? (
        <p className="text-sm text-(--color-text-secondary)">
          You do not have permission to access this page. You need the{" "}
          <span className="font-mono text-(--color-text-primary)">
            {role}
          </span>{" "}
          realm role to access this page.
        </p>
      ) : (
        <p className="text-sm text-(--color-text-secondary)">
          You do not have permission to access this page.
        </p>
      )}

      <div className="flex justify-center gap-3 pt-2">
        <button
          onClick={() => router.back()}
          className="px-4 py-2 rounded-md bg-(--color-surface-alt) text-(--color-text-primary) hover:opacity-90"
        >
          Go back
        </button>

        <Link
          href="/"
          className="px-4 py-2 rounded-md bg-(--color-primary) text-white hover:opacity-90"
        >
          Home
        </Link>
      </div>
    </SystemPage>
  );
}