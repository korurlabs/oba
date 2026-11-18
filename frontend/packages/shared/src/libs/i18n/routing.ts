import { defineRouting } from "next-intl/routing";
import { sessionCookieDomain } from "../auth/session-cookie";
import { availableLanguages, Locale } from "./available-languages";
import { getRuntimeConfig } from "../../lib/runtime-config";

/** Name of the locale cookie that every Oba app reads and writes. */
export const LOCALE_COOKIE_NAME = "OBA_LOCALE";

/** One year in seconds, so the language choice survives a browser restart. */
const LOCALE_COOKIE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;

/**
 * The next-intl `localeCookie` option: one language choice for every Oba app.
 *
 * The cookie lives on the parent domain of the app (".korur.com"), the same
 * domain as the shared session, so a choice made in one app is the language of
 * the others at their next load. On localhost the domain is left out: browsers
 * do not isolate cookies by port, so the local apps already share the cookie.
 *
 * The name is not next-intl's default `NEXT_LOCALE`, because older deploys left
 * a host-only `NEXT_LOCALE` per app. A domain cookie of the same name would sit
 * beside it in the request, in an order the server cannot choose.
 *
 * `domain` is omitted, not set to undefined: next-intl's client writes every key
 * of this object into `document.cookie`, and "domain=undefined" makes the
 * browser reject the cookie.
 */
export function localeCookieConfig(baseUrl?: string) {
  const domain = sessionCookieDomain(baseUrl);
  return {
    name: LOCALE_COOKIE_NAME,
    maxAge: LOCALE_COOKIE_MAX_AGE_SECONDS,
    ...(domain && { domain }),
  };
}

/**
 * The app's public origin, for the locale cookie domain. In the browser it is
 * the page's own origin. This module loads before any provider renders, so it
 * cannot read the run-time config there.
 */
function publicOrigin(): string | undefined {
  return typeof window === "undefined" ? getRuntimeConfig().baseUrl : window.location.origin;
}

export const routing = defineRouting({
  // A list of all locales that are supported
  locales: availableLanguages,

  // Used when no locale matches
  defaultLocale: Locale.EN,
  localePrefix: "never",
  localeCookie: localeCookieConfig(publicOrigin()),
  // localeDetection: false,
});
