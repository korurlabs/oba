import { Messages, hasLocale } from "next-intl";
import { getRequestConfig } from "next-intl/server";
import { routing } from "./routing";

export const createIntlRequest = (appMessages: Record<string, Messages>) =>
  getRequestConfig(async function createRequestConfig({ requestLocale }) {
    // This typically corresponds to the `[locale]` segment
    const requested = await requestLocale;
    const locale = hasLocale(routing.locales, requested) ? requested : routing.defaultLocale;

    // Load shared messages from the package
    let sharedMessages = {};
    try {
      sharedMessages = (await import(`../../locales/${locale}.json`)).default;
    } catch (error) {
      console.error(`Failed to load shared messages for locale: ${locale}`, error);
    }

    const localeAppMessages = appMessages[locale] ?? appMessages[routing.defaultLocale];

    return {
      locale,
      // Pinned so server output does not depend on the host's zone: a pod runs in
      // UTC and a laptop in the developer's zone, which hid zone bugs locally.
      // The browser zone is applied on the client by `BrowserTimeZoneProvider`.
      timeZone: "UTC",
      messages: {
        ...sharedMessages,
        ...localeAppMessages,
      },
    };
  });
