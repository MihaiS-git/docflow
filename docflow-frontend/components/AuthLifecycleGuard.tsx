"use client";

import { AuthContext } from "@/lib/auth/AuthProvider";
import { useRouter } from "next/navigation";
import { useContext, useMemo } from "react";

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
  const rawCtx = useContext(AuthContext);
  const router = useRouter();

  const ctx = useMemo(
    () =>
      rawCtx ?? {
        status: "ANON" as const,
        identity: null,
        localUser: null,
        blockedCode: null,
        login: () => {},
        logout: () => {},
        refresh: async () => {},
        clearBlocked: () => {},
        isAuthenticated: false,
      },
    [rawCtx],
  );

  const blocked = useMemo(() => {
    if (ctx.status !== "BLOCKED" || !ctx.blockedCode) return null;
    return mapBlocked(ctx.blockedCode);
  }, [ctx]);

  if (
    ctx?.status === "AUTH" &&
    ctx.localUser === null && // backend refused /users/me
    ctx.identity
  ) {
    router.replace("/bootstrap/activate");
    return null;
  }

  if (blocked) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-black text-white">
        <div className="max-w-md text-center space-y-4">
          <h1 className="text-2xl font-bold">{blocked.title}</h1>
          <p className="opacity-80">{blocked.message}</p>

          <button
            className="mt-6 px-4 py-2 bg-red-600 hover:bg-red-700 rounded"
            onClick={() => ctx.clearBlocked()}
          >
            Go to login
          </button>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
