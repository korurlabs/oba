import { hasLocale, NextIntlClientProvider as NextIntlClientProviderBase } from "next-intl";
import { setRequestLocale } from "next-intl/server";
import { notFound } from "next/navigation";
import { routing } from "../libs/i18n/routing";
import { BrowserTimeZoneProvider } from "./BrowserTimeZoneProvider";

export function NextIntlClientProvider({
  children,
  locale,
}: {
  children: React.ReactNode;
  locale: string;
}) {
  if (!hasLocale(routing.locales, locale)) {
    notFound();
  }

  setRequestLocale(locale);

  // The request config renders in UTC; the nested client provider switches every
  // app that mounts this provider to the viewer's browser zone after hydration.
  return (
    <NextIntlClientProviderBase locale={locale}>
      <BrowserTimeZoneProvider>{children}</BrowserTimeZoneProvider>
    </NextIntlClientProviderBase>
  );
}
