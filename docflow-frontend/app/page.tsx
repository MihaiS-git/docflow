"use client";

import { useAuth } from "@/lib/auth/useAuth";

export default function Home() {
  const { status, isAuthenticated, identity, login, logout, refresh } =
    useAuth();

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-50 font-sans dark:bg-black">
      <main className="flex min-h-screen w-full max-w-3xl flex-col items-center justify-between py-32 px-16 bg-white dark:bg-black sm:items-start">
        <h1>DocFlow</h1>

        {status === "LOADING" && <div>Loading…</div>}

        {identity?.roles.includes("ADMIN") && (
          <>
            <a href="/console/users">Admin</a>
            <a href="/console/invites">Invites</a>
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
