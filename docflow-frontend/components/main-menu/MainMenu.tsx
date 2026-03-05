"use client";

import { useAuth } from "@/lib/auth/useAuth";
import Link from "next/link";
import { useState } from "react";

export default function MainMenu() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { status, isAuthenticated, identity, login, logout } =
    useAuth();

  return (
    <nav className="relative bg-gray-800/50 border-b border-white/10">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="flex h-16 items-center justify-between">
          {status === "LOADING" && <div>Loading…</div>}

          {/* Logo */}
          <div className="flex items-center gap-4">
            <Link href="/">
              <h1 className="text-gray-200">Docflow</h1>
            </Link>
            {/* Desktop navigation */}
            <div className="hidden sm:flex space-x-4">
              {identity?.roles.includes("ADMIN") && (
                <>
                  <Link
                    href="/console/users"
                    className="rounded-md px-3 py-2 text-sm font-medium text-gray-300 hover:bg-white/5 hover:text-white"
                  >
                    Admin
                  </Link>
                  <Link
                    href="/console/invites"
                    className="rounded-md px-3 py-2 text-sm font-medium text-gray-300 hover:bg-white/5 hover:text-white"
                  >
                    Invites
                  </Link>
                  <Link
                    href="/console/tenants"
                    className="rounded-md px-3 py-2 text-sm font-medium text-gray-300 hover:bg-white/5 hover:text-white"
                  >
                    Tenants
                  </Link>
                </>
              )}
              {identity?.roles.includes("AUDITOR") && (
                <>
                  <Link
                    href="/console/audit"
                    className="rounded-md px-3 py-2 text-sm font-medium text-gray-300 hover:bg-white/5 hover:text-white"
                  >
                    Audit
                  </Link>
                </>
              )}
            </div>
          </div>

          {/* Auth buttons */}
          {isAuthenticated && (
            <div className="flex items-center gap-4">
              <button onClick={logout}>Logout</button>
            </div>
          )}
          {status === "ANON" && <button onClick={login}>Login</button>}

          {/* Mobile menu button */}
          <button
            onClick={() => setMobileOpen(!mobileOpen)}
            className="sm:hidden inline-flex items-center justify-center rounded-md p-2 text-gray-400 hover:bg-white/5 hover:text-white"
          >
            {mobileOpen ? (
              /* X icon */
              <svg
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth="1.5"
                className="h-6 w-6"
                fill="none"
              >
                <path
                  d="M6 18L18 6M6 6l12 12"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            ) : (
              /* Menu icon */
              <svg
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth="1.5"
                className="h-6 w-6"
                fill="none"
              >
                <path
                  d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            )}
          </button>
        </div>
      </div>

      {/* Mobile menu */}
      {mobileOpen && (
        <>
          <div className="sm:hidden border-t border-white/10 px-2 pb-3 pt-2 space-y-1">
            {identity?.roles.includes("ADMIN") && (
              <>
                <Link
                  href="/console/users"
                  className="block rounded-md px-3 py-2 text-base font-medium text-gray-300 hover:bg-white/5 hover:text-white"
                >
                  Admin
                </Link>
                <Link
                  href="/console/invites"
                  className="block rounded-md px-3 py-2 text-base font-medium text-gray-300 hover:bg-white/5 hover:text-white"
                >
                  Invites
                </Link>
                <Link
                  href="/console/tenants"
                  className="block rounded-md px-3 py-2 text-base font-medium text-gray-300 hover:bg-white/5 hover:text-white"
                >
                  Tenants
                </Link>
              </>
            )}

            {identity?.roles.includes("AUDITOR") && (
              <>
                <Link
                  href="/console/audit"
                  className="block rounded-md px-3 py-2 text-base font-medium text-gray-300 hover:bg-white/5 hover:text-white"
                >
                  Audit
                </Link>
              </>
            )}

            {isAuthenticated && (
              <div className="flex items-center gap-4">
                <button onClick={logout}>Logout</button>
              </div>
            )}

            {status === "ANON" && <button onClick={login}>Login</button>}
          </div>
        </>
      )}
    </nav>
  );
}
