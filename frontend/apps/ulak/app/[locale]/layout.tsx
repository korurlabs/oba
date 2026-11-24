import type { Metadata } from "next";
import { Alexandria } from "next/font/google";
import { connection } from "next/server";
import { getTranslations } from "next-intl/server";
import { APP_TITLE, PAGE_TITLE_TEMPLATE } from "@/lib/page-title";
import { routing } from "@oba/shared/libs/i18n/routing";
import "../globals.css";
import { Providers } from "@/providers";
import { Locale } from "@oba/shared/types";
import { readRuntimeConfig } from "@oba/shared/lib/runtime-config";

// The sky design sets its text in Alexandria and reads it from this variable.
const alexandria = Alexandria({
  subsets: ["latin", "latin-ext"],
  weight: ["300", "400", "500", "600", "700", "800"],
  variable: "--font-alexandria",
  display: "swap",
});

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("metadata");
  return {
    title: { default: APP_TITLE, template: PAGE_TITLE_TEMPLATE },
    description: t("description"),
  };
}

export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }));
}

export default async function LocaleLayout({
  children,
  params,
}: Readonly<{
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}>) {
  const locale = (await params).locale as Locale;
  // The public URLs belong to the deployment, not the build: waiting for the
  // request keeps Next.js from prerendering them into a page at build time.
  await connection();
  const runtimeConfig = readRuntimeConfig();

  return (
    <html lang={locale} suppressHydrationWarning>
      <body className={`${alexandria.variable} font-sans antialiased`}>
        <Providers locale={locale} runtimeConfig={runtimeConfig}>
          {children}
        </Providers>
      </body>
    </html>
  );
}
