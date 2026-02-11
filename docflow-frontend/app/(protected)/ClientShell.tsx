"use client";

import AuthLifecycleGuard from "@/components/AuthLifecycleGuard";
import { AuthProvider } from "@/lib/auth/AuthProvider";
import { Toaster } from "sonner";

export default function ClientShell({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <AuthProvider>
      <AuthLifecycleGuard>
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
  );
}
