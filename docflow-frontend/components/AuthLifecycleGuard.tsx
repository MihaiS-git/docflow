"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";

import { useAuthSelector } from "@/hooks/useAuthSelector";
import { useAuth } from "@/lib/auth/useAuth";

function mapBlocked(code: string): { title: string; message: string } {
  switch (code) {
    case "USER_LOCKED":
      return {
        title: "Account locked",
        message:
          "Your account has been locked by an administrator. Please contact support.",
      };
    case "USER_DISABLED":
      return {
        title: "Account disabled",
        message: "Your account has been disabled. Please contact support.",
      };
    case "TENANT_SUSPENDED":
      return {
        title: "Organization suspended",
        message: "Your organization is currently suspended.",
      };
    default:
      return {
        title: "Access denied",
        message: "You no longer have access.",
      };
  }
}

export default function AuthLifecycleGuard({
  children,
}: {
  children: React.ReactNode;
}) {
  const path = usePathname();
  const router = useRouter();

  const status = useAuthSelector((s) => s.status);
  const blockedCode = useAuthSelector((s) => s.blockedCode);

  const { clearBlocked } = useAuth();

  const blocked =
    status === "BLOCKED" && blockedCode ? mapBlocked(blockedCode) : null;

  useEffect(() => {
    if (status === "BOOTSTRAP" && path !== "/bootstrap/activate") {
      router.replace("/bootstrap/activate");
    }
  }, [status, path, router]);

  if (status === "LOADING") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-black text-white">
        Loading session…
      </div>
    );
  }

  if (status === "BOOTSTRAP") {
    if (path !== "/bootstrap/activate") {
      return null;
    }
    return <>{children}</>;
  }

  if (blocked) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-black text-white">
        <div className="max-w-md text-center space-y-4">
          <h1 className="text-2xl font-bold">{blocked.title}</h1>

          <p className="opacity-80">{blocked.message}</p>

          <button
            className="mt-6 rounded bg-red-600 px-4 py-2 hover:bg-red-700"
            onClick={clearBlocked}
          >
            Go to login
          </button>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}