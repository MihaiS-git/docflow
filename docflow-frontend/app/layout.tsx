import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { cookies } from "next/headers";
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

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const cookieStore = await cookies();
  const theme = cookieStore.get("theme")?.value;
  const dark = theme === "dark";

  return (
    <html
      lang="en"
      className={dark ? "dark" : undefined}
      style={{ colorScheme: dark ? "dark" : "light" }}
    >
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