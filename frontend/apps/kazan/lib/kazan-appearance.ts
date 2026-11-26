/**
 * Kazan's appearance: which layout frames the pages and which background
 * fills the page area. Both are saved per user under one key of
 * `userPreferences`, so they follow the user to every browser. The theme
 * (system, light, dark) is the Oba-wide `theme` key, shared with the other
 * apps, and is not part of this record.
 *
 * Not a "use client" module: the server layout reads it to render the right
 * layout on the first paint.
 */

/** Modern is the sky shell with the floating menu; Classic the inset sidebar. */
export const KAZAN_LAYOUTS = ["modern", "classic"] as const;
export type KazanLayout = (typeof KAZAN_LAYOUTS)[number];

/** The page area behind boards, lists and chat. */
export const KAZAN_BACKGROUNDS = ["grid", "plain", "mist", "slate"] as const;
export type KazanBackground = (typeof KAZAN_BACKGROUNDS)[number];

/** The `userPreferences` key of the appearance record. */
export const KAZAN_APPEARANCE_KEY = "kazanAppearance";

export interface KazanAppearance {
  layout: KazanLayout;
  background: KazanBackground;
}

/** What is stored: either value may be missing until the user picks it. */
export type StoredKazanAppearance = Partial<KazanAppearance>;

export const DEFAULT_KAZAN_LAYOUT: KazanLayout = "modern";

/**
 * The background a layout shows until the user picks one: the grid is part
 * of the modern look, and the classic layout had a plain page.
 */
export function defaultKazanBackground(layout: KazanLayout): KazanBackground {
  return layout === "classic" ? "plain" : "grid";
}

function isLayout(value: unknown): value is KazanLayout {
  return typeof value === "string" && (KAZAN_LAYOUTS as readonly string[]).includes(value);
}

function isBackground(value: unknown): value is KazanBackground {
  return typeof value === "string" && (KAZAN_BACKGROUNDS as readonly string[]).includes(value);
}

/** The stored record as written, with unknown values dropped. */
export function storedKazanAppearance(
  prefs: Record<string, unknown> | null | undefined
): StoredKazanAppearance {
  const raw = prefs?.[KAZAN_APPEARANCE_KEY];
  if (!raw || typeof raw !== "object") return {};
  const record = raw as Record<string, unknown>;
  return {
    ...(isLayout(record.layout) ? { layout: record.layout } : {}),
    ...(isBackground(record.background) ? { background: record.background } : {}),
  };
}

/**
 * The appearance to show for the user's preferences. A missing or unknown
 * layout is Modern; a missing or unknown background is the layout's default.
 */
export function normalizeKazanAppearance(
  prefs: Record<string, unknown> | null | undefined
): KazanAppearance {
  const stored = storedKazanAppearance(prefs);
  const layout = stored.layout ?? DEFAULT_KAZAN_LAYOUT;
  return { layout, background: stored.background ?? defaultKazanBackground(layout) };
}
