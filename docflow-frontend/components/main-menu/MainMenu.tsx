"use client";

import { memo } from "react";
import Link from "next/link";
import { AuthUser } from "@/types/auth/AuthUser";

type MenuProps = {
  identity: AuthUser | null;
};

function MainMenuComponent({ identity }: MenuProps) {
  const roles = identity?.roles ?? [];

  const isAdmin = roles.includes("ADMIN");
  const isAuditor = roles.includes("AUDITOR");

  return (
    <div className="hidden sm:flex space-x-4">
      {isAdmin && (
        <>
          <Link
            href="/console/users"
            className="rounded-md px-3 py-2 text-sm font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary) cursor-pointer"
          >
            Users
          </Link>

          <Link
            href="/console/invites"
            className="rounded-md px-3 py-2 text-sm font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary) cursor-pointer"
          >
            Invites
          </Link>

          <Link
            href="/console/tenants"
            className="rounded-md px-3 py-2 text-sm font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary) cursor-pointer"
          >
            Tenants
          </Link>
        </>
      )}

      {isAuditor && (
        <Link
          href="/console/audit"
          className="rounded-md px-3 py-2 text-sm font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary) cursor-pointer"
        >
          Audit
        </Link>
      )}
    </div>
  );
}

export default memo(MainMenuComponent);