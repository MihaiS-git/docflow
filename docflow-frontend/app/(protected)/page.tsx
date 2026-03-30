"use client";

import { useAuth } from "@/lib/auth/useAuth";
import Link from "next/link";

export default function Home() {
  const { status, identity, login, logout, refresh } = useAuth();
  const isAuthenticated = status === "AUTH";

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-50 font-sans dark:bg-black">
      <main className="flex min-h-screen w-full max-w-3xl flex-col items-center justify-between py-32 px-16 bg-white dark:bg-black sm:items-start">
        <h1>DocFlow</h1>

        {status === "LOADING" && <div>Loading…</div>}

        {status === "BOOTSTRAP" && (
          <div className="mt-6 space-y-2">
            <p className="opacity-80">
              This system has not been initialized yet.
            </p>
            <a
              href="/bootstrap/activate"
              className="inline-block px-4 py-2 bg-green-600 hover:bg-green-700 rounded text-white"
            >
              Initialize system
            </a>
          </div>
        )}

        {identity?.roles.includes("ADMIN") && (
          <>
            <Link href="/console/users">Admin</Link>
            <Link href="/console/invites">Invites</Link>
            <Link href="/console/tenants">Tenants</Link>
          </>
        )}

        {identity?.roles.includes("AUDITOR") && (
          <>
            <a href="/console/audit">Audit</a>
          </>
        )}

        {isAuthenticated && (
          <div className="flex items-center gap-4">
            <button onClick={logout}>Logout</button>
            <button onClick={refresh}>Refresh identity</button>
          </div>
        )}

        {status === "ANON" && <button onClick={login}>Login</button>}

        {identity && (
          <pre className="mt-6 text-xs opacity-80">
            Current User: {JSON.stringify(identity, null, 2)}
          </pre>
        )}
      </main>
    </div>
  );
}
