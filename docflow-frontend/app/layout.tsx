import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";

import AuthLifecycleGuard from "@/components/AuthLifecycleGuard";
import { AuthProvider } from "@/lib/auth/AuthProvider";
import { Toaster } from "sonner";

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
    <html lang="en">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        <AuthProvider>
          <AuthLifecycleGuard>
            {children}
            <Toaster position="bottom-right" richColors closeButton theme="system" expand/>
          </AuthLifecycleGuard>
        </AuthProvider>
      </body>
    </html>
  );
}
