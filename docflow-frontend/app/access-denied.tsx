"use client";

import Link from "next/link";
import { useSearchParams, useRouter } from "next/navigation";

export default function AccessDeniedPage() {
  const params = useSearchParams();
  const router = useRouter();

  const role = params.get("role");

  return (
    <div className="min-h-screen flex items-center justify-center bg-zinc-50 dark:bg-black">
      <div className="max-w-md text-center space-y-4">
        <h1 className="text-2xl font-semibold">Access denied</h1>

        {role && (
          <p className="opacity-70">
            Required role: <span className="font-mono">{role}</span>
          </p>
        )}

        {!role && (
          <p className="opacity-70">
            You do not have permission to access this page.
          </p>
        )}

        <div className="flex gap-3 justify-center pt-4">
          <button
            onClick={() => router.back()}
            className="px-4 py-2 rounded bg-zinc-700 text-white hover:bg-zinc-800"
          >
            Go back
          </button>

          <Link
            href="/"
            className="px-4 py-2 rounded bg-blue-600 text-white hover:bg-blue-700"
          >
            Home
          </Link>
        </div>
      </div>
    </div>
  );
}