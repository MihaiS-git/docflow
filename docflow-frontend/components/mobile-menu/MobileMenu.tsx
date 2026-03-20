"use client";

import { memo } from "react";
import Link from "next/link";
import { AuthUser } from "@/types/auth/AuthUser";

type MenuProps = {
  open: boolean;
  identity: AuthUser | null;
  isAuthenticated: boolean;
  login: () => void;
  logout: () => void;
  onNavigate: () => void;
};

function MobileMenuComponent({
  open,
  identity,
  isAuthenticated,
  login,
  logout,
  onNavigate,
}: MenuProps) {
  if (!open) return null;

  const roles = identity?.roles ?? [];

  const isAdmin = roles.includes("ADMIN");
  const isAuditor = roles.includes("AUDITOR");

  return (
    <div className="sm:hidden border-t border-(--color-border) bg-(--color-surface) px-2 pb-3 pt-2 space-y-1">
      {isAdmin && (
        <>
          <Link
            href="/console/users"
            onClick={onNavigate}
            className="block rounded-md px-3 py-2 text-base font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary) cursor-pointer"
          >
            Users
          </Link>

          <Link
            href="/console/invites"
            onClick={onNavigate}
            className="block rounded-md px-3 py-2 text-base font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary) cursor-pointer"
          >
            Invites
          </Link>

          <Link
            href="/console/tenants"
            onClick={onNavigate}
            className="block rounded-md px-3 py-2 text-base font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary) cursor-pointer"
          >
            Tenants
          </Link>
        </>
      )}

      {isAuditor && (
        <Link
          href="/console/audit"
          onClick={onNavigate}
          className="block rounded-md px-3 py-2 text-base font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary) cursor-pointer"
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
          className="block w-full rounded-md px-3 py-2 text-left text-base font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary) cursor-pointer"
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
          className="block w-full rounded-md px-3 py-2 text-left text-base font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary) cursor-pointer"
        >
          Login
        </button>
      )}
    </div>
  );
}

export default memo(MobileMenuComponent);