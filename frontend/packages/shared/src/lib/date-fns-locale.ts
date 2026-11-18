// The date-fns locale of an app locale, for the date pickers and the date-fns
// formats of the Kazan app. Kept apart from `format-instant` and `date-only`,
// which use `Intl` and import no date library.

import { enUS, nl, tr, type Locale } from "date-fns/locale";

function languageOf(tag: string): string {
  return (tag.split("-")[0] ?? "").toLowerCase();
}

/**
 * The date-fns locale for an app locale (`en`, `nl`, `tr`), for date pickers and
 * for calendar days formatted with date-fns. An unknown locale falls back to
 * US English, the app's default language.
 */
export function getDateFnsLocale(locale: string): Locale {
  switch (languageOf(locale)) {
    case "nl":
      return nl;
    case "tr":
      return tr;
    default:
      return enUS;
  }
}
