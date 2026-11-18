import type { Metadata, Viewport } from "next";
import { Alexandria } from "next/font/google";
import { connection } from "next/server";
import type { ReactNode } from "react";

import { readRuntimeConfig } from "@oba/shared/lib/runtime-config";

import "./globals.css";
import { Providers } from "./providers";

const alexandria = Alexandria({
  subsets: ["latin", "latin-ext"],
  weight: ["300", "400", "500", "600", "700", "800"],
  variable: "--font-alexandria",
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "Kaya",
    template: "%s · Kaya",
  },
  description:
    "The agents log of your organization: every Claude Code session, its subagents and the skills they loaded.",
  robots: {
    index: false,
    follow: false,
  },
};

export const viewport: Viewport = {
  themeColor: "#f8fbff",
};

export default async function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  // The public URLs belong to the deployment, not the build: waiting for the
  // request keeps Next.js from prerendering them into a page at build time.
  await connection();
  const runtimeConfig = readRuntimeConfig();

  return (
    <html lang="en" data-scroll-behavior="smooth">
      <body className={`${alexandria.variable} font-sans antialiased`}>
        <Providers runtimeConfig={runtimeConfig}>{children}</Providers>
      </body>
    </html>
  );
}
