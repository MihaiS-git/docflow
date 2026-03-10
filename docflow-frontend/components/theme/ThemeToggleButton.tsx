"use client";

import { Moon, Sun } from "lucide-react";
import { useState } from "react";

function readTheme() {
  if (typeof document === "undefined") return false;
  return document.documentElement.classList.contains("dark");
}

export default function ThemeToggleButton() {
  const [dark, setDark] = useState(readTheme);

  const toggle = () => {
    const root = document.documentElement;
    const next = !root.classList.contains("dark");

    root.classList.toggle("dark", next);
    root.style.colorScheme = next ? "dark" : "light";

    localStorage.setItem("theme", next ? "dark" : "light");

    setDark(next);
  };

  return (
    <button
      onClick={toggle}
      aria-label="Toggle theme"
      className="p-2 rounded-md hover:bg-muted transition"
    >
      {dark ? <Sun size={16} /> : <Moon size={16} />}
    </button>
  );
}