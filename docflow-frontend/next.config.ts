import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  allowedDevOrigins: [
    "http://brutecx.test:3000",
    "http://brutecx.test",
    "http://localhost:3000",
    "http://192.168.100.27:3000",
  ],
};

export default nextConfig;
