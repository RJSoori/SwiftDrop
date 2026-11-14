import type { Metadata } from "next";
import { Providers } from "./providers";
import { ServiceWorkerRegister } from "@/components/service-worker-register";
import "./globals.css";

export const metadata: Metadata = {
  title: "SwiftDrop",
  description: "Parcel delivery across Sri Lanka",
  appleWebApp: {
    capable: true,
    title: "SwiftDrop",
    statusBarStyle: "default",
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className="min-h-dvh antialiased">
        <Providers>{children}</Providers>
        <ServiceWorkerRegister />
      </body>
    </html>
  );
}
