import { describe, expect, it, mock } from "bun:test";

import type { ColorScheme } from "@/lib/theme";

// lib/theme.ts takes the navigation base themes from @react-navigation/native,
// which loads react-native's Flow source; Bun cannot parse it. THEME does not
// depend on them, so plain stand-ins are enough here.
mock.module("@react-navigation/native", () => ({
  DefaultTheme: { dark: false, colors: {}, fonts: {} },
  DarkTheme: { dark: true, colors: {}, fonts: {} },
}));
const { THEME } = await import("@/lib/theme");

type ThemeToken = keyof (typeof THEME)["light"];

/** The sRGB channels (0 to 255) of an opaque THEME value, "hsl(H S% L%)". */
function rgb(value: string): [number, number, number] {
  const match = /^hsl\(([\d.]+) ([\d.]+)% ([\d.]+)%\)$/.exec(value);
  if (!match) throw new Error(`Not an opaque hsl() token: ${value}`);
  const hue: number = Number(match[1]);
  const saturation: number = Number(match[2]) / 100;
  const lightness: number = Number(match[3]) / 100;
  const chroma: number = saturation * Math.min(lightness, 1 - lightness);
  const channel = (offset: number): number => {
    const k: number = (offset + hue / 30) % 12;
    return Math.round((lightness - chroma * Math.max(-1, Math.min(k - 3, 9 - k, 1))) * 255);
  };
  return [channel(0), channel(8), channel(4)];
}

/** WCAG 2 relative luminance. */
function luminance([red, green, blue]: [number, number, number]): number {
  const linear = (value: number): number => {
    const s: number = value / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue);
}

/** WCAG 2 contrast ratio of two THEME tokens. */
function contrast(scheme: ColorScheme, a: ThemeToken, b: ThemeToken): number {
  const first: number = luminance(rgb(THEME[scheme][a]));
  const second: number = luminance(rgb(THEME[scheme][b]));
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
}

const SCHEMES: ColorScheme[] = ["light", "dark"];
const TEXT_MINIMUM = 4.5;

describe("contrast helper", () => {
  it("matches the published ratio of the design's #dc2626 on white", () => {
    // #dc2626 is hsl(0 72.2% 50.6%), 4.83:1 on white.
    expect(rgb("hsl(0 72.2% 50.6%)")).toEqual([220, 38, 38]);
    expect(contrast("light", "destructiveFill", "card")).toBeCloseTo(4.83, 2);
  });
});

describe("destructive text and fills reach 4.5:1", () => {
  for (const scheme of SCHEMES) {
    for (const ground of ["screen", "background", "card", "cardBackground"] as const) {
      it(`${scheme}: destructive text on ${ground}`, () => {
        expect(contrast(scheme, "destructive", ground)).toBeGreaterThanOrEqual(TEXT_MINIMUM);
      });
    }

    it(`${scheme}: destructive-foreground on destructive-fill`, () => {
      expect(contrast(scheme, "destructiveForeground", "destructiveFill")).toBeGreaterThanOrEqual(
        TEXT_MINIMUM
      );
    });
  }
});

describe("filled swipe grounds reach 4.5:1 under their foreground", () => {
  for (const scheme of SCHEMES) {
    it(`${scheme}: success-foreground on success-fill`, () => {
      expect(contrast(scheme, "successForeground", "successFill")).toBeGreaterThanOrEqual(
        TEXT_MINIMUM
      );
    });

    it(`${scheme}: primary-foreground on primary-fill`, () => {
      expect(contrast(scheme, "primaryForeground", "primaryFill")).toBeGreaterThanOrEqual(
        TEXT_MINIMUM
      );
    });
  }
});

describe("light muted-foreground reaches 4.5:1 on its tinted grounds", () => {
  for (const ground of [
    "muted",
    "cardBackground",
    "columnInProgress",
    "primaryLight",
    "warningLight",
  ] as const) {
    it(`muted-foreground on ${ground}`, () => {
      expect(contrast("light", "mutedForeground", ground)).toBeGreaterThanOrEqual(TEXT_MINIMUM);
    });
  }
});

describe("light priority glyphs reach 3:1 on white", () => {
  const GRAPHIC_MINIMUM = 3;
  for (const glyph of [
    "priorityLowest",
    "priorityLow",
    "priorityMedium",
    "priorityHigh",
    "priorityHighest",
    "priorityP1",
    "priorityP2",
    "priorityP3",
  ] as const) {
    it(`${glyph} on card`, () => {
      expect(contrast("light", glyph, "card")).toBeGreaterThanOrEqual(GRAPHIC_MINIMUM);
    });
  }
});
