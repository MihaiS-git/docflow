"use client";

import { Moon, Sun } from "lucide-react";
import { memo, useCallback, useSyncExternalStore } from "react";

function subscribe(callback: () => void) {
  const observer = new MutationObserver(callback);

  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["class"],
  });

  return () => observer.disconnect();
}

function getSnapshot() {
  return document.documentElement.classList.contains("dark");
}

function getServerSnapshot() {
  return false;
}

function ThemeToggleButtonComponent() {
  const dark = useSyncExternalStore(
    subscribe,
    getSnapshot,
    getServerSnapshot
  );

  const toggle = useCallback(() => {
    const root = document.documentElement;
    const next = !root.classList.contains("dark");

    root.classList.toggle("dark", next);
    root.style.colorScheme = next ? "dark" : "light";

    localStorage.setItem("theme", next ? "dark" : "light");

    document.cookie = `theme=${next ? "dark" : "light"}; Path=/; Max-Age=31536000; SameSite=Lax`;
  }, []);

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label="Toggle theme"
      className="rounded-md p-2 transition hover:bg-(--color-surface-alt) cursor-pointer"
    >
      {dark ? <Sun size={16} /> : <Moon size={16} />}
    </button>
  );
}

export default memo(ThemeToggleButtonComponent);