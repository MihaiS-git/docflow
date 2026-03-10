"use client";

import { AuthUser } from "@/types/auth/AuthUser";
import Link from "next/link";

type MenuProps = {
  open: boolean;
  identity: AuthUser | null;
  isAuthenticated: boolean;
  login: () => void;
  logout: () => void;
  onNavigate: () => void;
};

export default function MobileMenu({
  open,
  identity,
  isAuthenticated,
  login,
  logout,
  onNavigate,
}: MenuProps) {
  if (!open) return null;

  return (
    <div className="sm:hidden border-t border-(--color-border) bg-(--color-surface) px-2 pb-3 pt-2 space-y-1">
      {identity?.roles.includes("ADMIN") && (
        <>
          <Link
            href="/console/users"
            onClick={onNavigate}
            className="block rounded-md px-3 py-2 text-base font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary)"
          >
            Users
          </Link>

          <Link
            href="/console/invites"
            onClick={onNavigate}
            className="block rounded-md px-3 py-2 text-base font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary)"
          >
            Invites
          </Link>

          <Link
            href="/console/tenants"
            onClick={onNavigate}
            className="block rounded-md px-3 py-2 text-base font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary)"
          >
            Tenants
          </Link>
        </>
      )}

      {identity?.roles.includes("AUDITOR") && (
        <Link
          href="/console/audit"
          onClick={onNavigate}
          className="block rounded-md px-3 py-2 text-base font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary)"
        >
          Audit
        </Link>
      )}

      {isAuthenticated && (
        <button
          onClick={() => {
            onNavigate();
            logout();
          }}
          className="block w-full rounded-md px-3 py-2 text-left text-base font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary)"
        >
          Logout
        </button>
      )}

      {!isAuthenticated && (
        <button
          onClick={() => {
            onNavigate();
            login();
          }}
          className="block w-full rounded-md px-3 py-2 text-left text-base font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary)"
        >
          Login
        </button>
      )}
    </div>
  );
}