import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "SwiftDrop Courier",
    short_name: "SwiftDrop",
    description: "Manage your SwiftDrop deliveries",
    start_url: "/courier",
    scope: "/",
    display: "standalone",
    background_color: "#0f1226",
    theme_color: "#0f1226",
    icons: [
      {
        src: "/icons/courier-192.svg",
        sizes: "192x192",
        type: "image/svg+xml",
        purpose: "any",
      },
      {
        src: "/icons/courier-512.svg",
        sizes: "512x512",
        type: "image/svg+xml",
        purpose: "maskable",
      },
    ],
  };
}
