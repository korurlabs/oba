import type { ViewStyle } from "react-native";

/*
 * The design shadows as React Native `boxShadow` values. The classic shadow
 * props and Android elevation draw one layer without spread, so the two-layer
 * card shadow and the negative-spread lift and dialog shadows exist only in
 * this form. The same strings are the `boxShadow` entries of
 * tailwind.config.js; components/ui/__tests__/shadow.test.ts keeps them equal.
 */
export const SHADOW = {
  /** A ticket card or section card on `screen`. */
  card: "0 4px 12px rgba(0, 0, 0, 0.05), 0 1px 3px rgba(74, 163, 255, 0.1)",
  /** A card lifted by a drag. */
  "card-hover": "0 12px 24px -8px rgba(0, 0, 0, 0.12)",
  /** Sheets, alerts and toasts. */
  dialog: "0 25px 50px -12px rgba(0, 0, 0, 0.25)",
  /** The Android floating action button. */
  "primary-glow": "0 8px 20px rgba(47, 143, 233, 0.35)",
} as const;

export type ShadowToken = keyof typeof SHADOW;

/** The style for a shadow token in a colour scheme; dark has no shadows. */
export function shadowStyle(
  token: ShadowToken | null | undefined,
  colorScheme: "light" | "dark" | undefined
): ViewStyle | undefined {
  if (!token || colorScheme === "dark") return undefined;
  return { boxShadow: SHADOW[token] };
}
