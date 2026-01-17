"use client";

import { getCurrentUser, login, logout } from "@/lib/auth";
import { useEffect, useState } from "react";

export default function Home() {
  const [isAuth, setIsAuth] = useState<boolean>(false);

  useEffect(() => {
    const checkAuth = async () => {
      const user = await getCurrentUser();
      console.log("Current user:", user);
      setIsAuth(!!user);
    };
    checkAuth();
  }, []);

  const handleLogin = () => {
    login();
  };

  const handleLogout = async () => {
    logout();
    setIsAuth(false);
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-50 font-sans dark:bg-black">
      <main className="flex min-h-screen w-full max-w-3xl flex-col items-center justify-between py-32 px-16 bg-white dark:bg-black sm:items-start">
        <h1>DocFlow</h1>

        {isAuth ? (
          <button onClick={handleLogout}>Logout</button>
        ) : (
          <button onClick={handleLogin}>Login</button>
        )}
      </main>
    </div>
  );
}
