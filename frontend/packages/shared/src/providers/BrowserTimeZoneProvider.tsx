"use client";

import { NextIntlClientProvider, useLocale, useTimeZone, type Locale } from "next-intl";
import { useSyncExternalStore } from "react";

// The browser zone does not change while a page is open in any way the page can
// observe, so there is nothing to subscribe to.
function subscribe(): () => void {
  return () => undefined;
}

/**
 * Replaces the request's zone with the browser's for all next-intl formatting
 * below it.
 *
 * The server renders in UTC (`createIntlRequest` pins UTC, so a laptop renders
 * the same text as a pod), so every `useFormatter().dateTime` and
 * `useDateFormat` would otherwise show UTC times. The server snapshot is the
 * inherited zone, which keeps hydration identical to the server HTML; React
 * then re-renders once with the browser zone. Messages, formats and the locale
 * are inherited from the parent provider unchanged.
 */
export function BrowserTimeZoneProvider({ children }: { children: React.ReactNode }) {
  const locale: Locale = useLocale();
  const inheritedTimeZone: string | undefined = useTimeZone();
  const timeZone: string | undefined = useSyncExternalStore(
    subscribe,
    () => Intl.DateTimeFormat().resolvedOptions().timeZone || inheritedTimeZone,
    () => inheritedTimeZone,
  );

  return (
    <NextIntlClientProvider locale={locale} timeZone={timeZone}>
      {children}
    </NextIntlClientProvider>
  );
}
