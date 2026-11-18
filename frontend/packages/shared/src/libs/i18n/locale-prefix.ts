// Matches a leading locale segment only at a path boundary, so `/enabled` is
// NOT mistaken for the `en` locale ( `/en`, `/en/x` match; `/enabled` does not).
// The proxy and the language switcher both read it, so they always agree on
// which addresses carry a locale prefix.
export const LOCALE_PREFIX_REGEX = /^\/(en|nl|tr)(\/|$)/;

/** The path without its locale prefix; `/` when only the prefix was there. */
export function stripLocale(pathname: string): string {
  return pathname.replace(LOCALE_PREFIX_REGEX, "/");
}

/** The locale of the prefix, or null when the path has none. */
export function extractLocale(pathname: string): string | null {
  return pathname.match(LOCALE_PREFIX_REGEX)?.[1] ?? null;
}
