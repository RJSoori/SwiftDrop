import type { Metadata } from "next";
import { Archivo } from "next/font/google";
import { Providers } from "./providers";
import "./globals.css";

const archivo = Archivo({ subsets: ["latin"], axes: ["wdth"], variable: "--font-sans" });

export const metadata: Metadata = {
  title: "SwiftDrop",
  description: "Parcel delivery across Sri Lanka",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={archivo.variable}>
      <body className="min-h-dvh antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
