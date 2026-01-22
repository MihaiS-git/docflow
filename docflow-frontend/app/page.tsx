"use client";

import { getCurrentUser, login, logout } from "@/lib/auth";
import { AuthState } from "@/types/AuthState";
import { useEffect, useState } from "react";

export default function Home() {
  const [authState, setAuthState] = useState<AuthState>("ANON");

  useEffect(() => {
    const checkAuth = async () => {
      const result = await getCurrentUser({ redirect: false });

      console.log("Auth check result:", result);

      switch (result.state) {
        case "AUTH":
          setAuthState("AUTH");
          break;
        case "BLOCKED":
          setAuthState("BLOCKED");
          break;
        case "ANON":
          setAuthState("ANON");
          break;
      }
    };

    checkAuth();
  }, []);

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-50 font-sans dark:bg-black">
      <main className="flex min-h-screen w-full max-w-3xl flex-col items-center justify-between py-32 px-16 bg-white dark:bg-black sm:items-start">
        <h1>DocFlow</h1>

        {authState === "AUTH" && <button onClick={logout}>Logout</button>}

        {authState === "ANON" && <button onClick={login}>Login</button>}

        {authState === "BLOCKED" && (
          <div className="text-red-600">
            <p>Your account is locked.</p>
            <button onClick={logout} className="ml-4">
              Logout
            </button>
          </div>
        )}

      </main>
    </div>
  );
}
