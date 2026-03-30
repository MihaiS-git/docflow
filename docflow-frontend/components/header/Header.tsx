"use client";

import Link from "next/link";
import { useState } from "react";

import ThemeToggleButton from "../theme/ThemeToggleButton";
import MobileMenuButton from "./MobileMenuButton";
import MobileMenu from "../mobile-menu/MobileMenu";

import { useAuthSelector } from "@/hooks/useAuthSelector";
import { useAuth } from "@/lib/auth/useAuth";
import MainMenu from "../main-menu/MainMenu";
import Button from "../ui/Button";

export default function Header() {
  const [mobileOpen, setMobileOpen] = useState(false);

  const status = useAuthSelector((s) => s.status);
  const identity = useAuthSelector((s) => s.identity);

  const { login, logout } = useAuth();

  return (
    <nav className="relative bg-(--color-surface) border-b border-(--color-border)">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="flex h-16 items-center justify-between">
          {status === "LOADING" && <div>Loading…</div>}

          <Link href="/">
            <h1 className="font-semibold text-(--color-text-primary)">
              DocFlow
            </h1>
          </Link>

          <MainMenu identity={identity} />

          <div className="flex items-center gap-3">
            <ThemeToggleButton />

            {status === "AUTH" && <Button variant="outline" onClick={logout}>Logout</Button>}

            {status === "ANON" && <Button variant="outline" onClick={login}>Login</Button>}

            <MobileMenuButton
              mobileOpen={mobileOpen}
              setMobileOpen={setMobileOpen}
            />
          </div>
        </div>
      </div>

      <MobileMenu
        open={mobileOpen}
        identity={identity}
        isAuthenticated={status === "AUTH"}
        login={login}
        logout={logout}
        onNavigate={() => setMobileOpen(false)}
      />
    </nav>
  );
}