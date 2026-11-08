import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  poweredByHeader: false,
  // Preserve the original origin for the BFF mutation check (including loopback hosts).
  skipProxyUrlNormalize: true,
};

export default nextConfig;
