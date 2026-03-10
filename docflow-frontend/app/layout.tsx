import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/lib/auth/AuthProvider";
import AuthLifecycleGuard from "@/components/AuthLifecycleGuard";
import { Toaster } from "sonner";
import Header from "@/components/header/Header";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "DocFlow",
  description: "DocFlow",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `
(function () {
  try {
    const root = document.documentElement;
    const saved = localStorage.getItem("theme");
    const systemDark = window.matchMedia("(prefers-color-scheme: dark)").matches;

    const dark = saved === "dark" || (!saved && systemDark);

    root.classList.toggle("dark", dark);
    root.style.colorScheme = dark ? "dark" : "light";
  } catch (_) {}
})();
`,
          }}
        />
      </head>
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased bg-(--color-bg) text-(--color-text-primary)`}
      >
          <AuthProvider>
            <AuthLifecycleGuard>
              <Header />
              {children}

              <Toaster
                position="bottom-right"
                richColors
                closeButton
                theme="system"
                expand
              />
            </AuthLifecycleGuard>
          </AuthProvider>
      </body>
    </html>
  );
}
