import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/lib/auth/AuthProvider";
import AuthLifecycleGuard from "@/components/AuthLifecycleGuard";
import { Toaster } from "sonner";
import MainMenu from "@/components/main-menu/MainMenu";

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

            <MainMenu />
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
