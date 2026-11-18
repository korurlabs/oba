/** The format the server accepts for a board, label, project or milestone colour. */
export const HEX_COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/;

const SHORT_HEX_PATTERN = /^#?([0-9A-Fa-f])([0-9A-Fa-f])([0-9A-Fa-f])$/;
const LONG_HEX_PATTERN = /^#?([0-9A-Fa-f]{6})$/;

/**
 * Brings a hex colour typed or picked by a user into the stored `#RRGGBB`
 * form, expanding the three-digit shorthand. Returns null for anything else,
 * so the caller never sends the server a value it rejects.
 */
export function normalizeHexColor(input: string | null | undefined): string | null {
  if (!input) return null;
  const trimmed = input.trim();
  const long = LONG_HEX_PATTERN.exec(trimmed)?.[1];
  if (long) return `#${long.toUpperCase()}`;
  const short = SHORT_HEX_PATTERN.exec(trimmed);
  if (!short) return null;
  const [, r = "", g = "", b = ""] = short;
  return `#${r}${r}${g}${g}${b}${b}`.toUpperCase();
}

/** Case-insensitive equality of two colours, treating a missing value as no match. */
export function isSameColor(a: string | null | undefined, b: string | null | undefined): boolean {
  return !!a && !!b && a.toLowerCase() === b.toLowerCase();
}

function channelToLinear(channel: number): number {
  const c = channel / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

/** WCAG relative luminance of a `#RRGGBB` colour, from 0 (black) to 1 (white). */
export function getRelativeLuminance(hex: string): number {
  const value = hex.slice(1);
  const r = channelToLinear(parseInt(value.slice(0, 2), 16));
  const g = channelToLinear(parseInt(value.slice(2, 4), 16));
  const b = channelToLinear(parseInt(value.slice(4, 6), 16));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

const DARK_TEXT = "#0A0A0A";
const LIGHT_TEXT = "#FFFFFF";

/**
 * The foreground (text or icon) colour that reads best on a user-chosen
 * background: near-black or white, whichever has the higher WCAG contrast
 * ratio. The choice depends only on the background, so it holds in both the
 * light and the dark theme. An unparsable background falls back to
 * `currentColor`, inheriting the theme's own text colour.
 */
export function getReadableTextColor(background: string | null | undefined): string {
  const hex = normalizeHexColor(background);
  if (!hex) return "currentColor";
  const luminance = getRelativeLuminance(hex);
  const darkLuminance = getRelativeLuminance(DARK_TEXT);
  const contrastWithDark = (luminance + 0.05) / (darkLuminance + 0.05);
  const contrastWithLight = 1.05 / (luminance + 0.05);
  return contrastWithDark >= contrastWithLight ? DARK_TEXT : LIGHT_TEXT;
}
