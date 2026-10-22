import type { NextConfig } from "next";

// Same-origin API: the browser calls /api/* on the web app and Next proxies to the gateway,
// so auth cookies are first-party. Rewrites are resolved at build time (the Docker build sets GATEWAY_URL).
const gateway = process.env.GATEWAY_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${gateway}/api/:path*` }];
  },
};

export default nextConfig;
