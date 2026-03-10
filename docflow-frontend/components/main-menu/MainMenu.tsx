"use client";

import { AuthUser } from "@/types/auth/AuthUser";
import Link from "next/link";

type MenuProps = {
  identity: AuthUser | null;
};

export default function MainMenu({ identity }: MenuProps) {
  return (
    <div className="hidden sm:flex space-x-4">
      {identity?.roles.includes("ADMIN") && (
        <>
          <Link
            href="/console/users"
            className="rounded-md px-3 py-2 text-sm font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary)"
          >
            Users
          </Link>
          <Link
            href="/console/invites"
            className="rounded-md px-3 py-2 text-sm font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary)"
          >
            Invites
          </Link>
          <Link
            href="/console/tenants"
            className="rounded-md px-3 py-2 text-sm font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary)"
          >
            Tenants
          </Link>
        </>
      )}

      {identity?.roles.includes("AUDITOR") && (
        <Link
          href="/console/audit"
          className="rounded-md px-3 py-2 text-sm font-medium text-(--color-text-secondary) hover:bg-(--color-surface-alt) hover:text-(--color-text-primary)"
        >
          Audit
        </Link>
      )}
    </div>
  );
}