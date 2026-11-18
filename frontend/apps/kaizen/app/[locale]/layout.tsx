import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import { connection } from 'next/server';
import { getTranslations } from 'next-intl/server';
import { SkipToContent } from '@/components/shared/skip-to-content';
import { APP_TITLE, PAGE_TITLE_TEMPLATE } from '@/lib/page-title';
import { routing } from '@oba/shared/libs/i18n/routing';
import '../globals.css';
import { Providers } from '@/providers';
import { Locale } from '@oba/shared/types';
import { readRuntimeConfig } from '@oba/shared/lib/runtime-config';

const inter = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
});

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('metadata');
  return {
    title: { default: APP_TITLE, template: PAGE_TITLE_TEMPLATE },
    description: t('description'),
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
  const t = await getTranslations('common');
  // The public URLs belong to the deployment, not the build: waiting for the
  // request keeps Next.js from prerendering them into a page at build time.
  await connection();
  const runtimeConfig = readRuntimeConfig();

  return (
    <html lang={locale} suppressHydrationWarning>
      <body className={`${inter.variable} font-sans antialiased`}>
        <SkipToContent label={t('skipToContent')} />
        <Providers locale={locale} runtimeConfig={runtimeConfig}>{children}</Providers>
      </body>
    </html>
  );
}
