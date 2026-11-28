import type { Metadata } from "next";
import { Alexandria, Inter } from "next/font/google";
import { connection } from "next/server";
import { getTranslations } from "next-intl/server";
import { SkipToContent } from "@/components/shared/skip-to-content";
import { APP_TITLE, PAGE_TITLE_TEMPLATE } from "@/lib/page-title";
import { routing } from "@oba/shared/libs/i18n/routing";
import "../globals.css";
import { Providers } from "@/providers";
import { Locale } from "@oba/shared/types";
import { readRuntimeConfig } from "@oba/shared/lib/runtime-config";
import { loadServerAppearance } from "@/components/layout/kazan-appearance-server";

const alexandria = Alexandria({
  subsets: ["latin", "latin-ext"],
  weight: ["300", "400", "500", "600", "700", "800"],
  variable: "--font-alexandria",
  display: "swap",
});

// The classic layout's typeface. Only that layout uses it, so it is not
// preloaded: the modern layout never downloads it.
const inter = Inter({
  subsets: ["latin", "latin-ext"],
  variable: "--font-inter",
  display: "swap",
  preload: false,
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
  const t = await getTranslations("common");
  // The public URLs belong to the deployment, not the build: waiting for the
  // request keeps Next.js from prerendering them into a page at build time.
  await connection();
  const runtimeConfig = readRuntimeConfig();
  // The layout and the background are read on the server so the first paint
  // already has them. They sit on <html> because dialogs and menus render in
  // portals outside the shell and must follow them too.
  const { layout, background } = await loadServerAppearance();

  return (
    <html
      lang={locale}
      data-kazan-layout={layout}
      data-kazan-background={background}
      suppressHydrationWarning
    >
      <body className={`${alexandria.variable} ${inter.variable} font-sans antialiased`}>
        <SkipToContent label={t("skipToContent")} />
        <Providers locale={locale} runtimeConfig={runtimeConfig}>
          {children}
        </Providers>
      </body>
    </html>
  );
}
