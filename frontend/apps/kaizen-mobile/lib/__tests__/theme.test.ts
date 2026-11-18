import { describe, expect, it, mock } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import type { ColorScheme } from "@/lib/theme";

// lib/theme.ts takes the navigation base themes from @react-navigation/native,
// which loads react-native's Flow source; Bun cannot parse it. THEME does not
// depend on them, so plain stand-ins are enough here.
mock.module("@react-navigation/native", () => ({
  DefaultTheme: { dark: false, colors: {}, fonts: {} },
  DarkTheme: { dark: true, colors: {}, fonts: {} },
}));
const { THEME } = await import("@/lib/theme");

const css: string = readFileSync(join(import.meta.dir, "..", "..", "global.css"), "utf8");

/** The `--name: value;` pairs of one CSS block, excluding --radius and --scrim-alpha. */
function cssVariables(selector: string): Map<string, string> {
  const start: number = css.indexOf(`${selector} {`);
  expect(start).toBeGreaterThan(-1);
  const block: string = css.slice(start, css.indexOf("}", start));
  const variables = new Map<string, string>();
  for (const match of block.matchAll(/--([a-z0-9-]+):\s*([^;]+);/g)) {
    const [, name, value] = match;
    if (name !== "radius" && name !== "scrim-alpha") variables.set(name, value.trim());
  }
  return variables;
}

function cssAlpha(selector: string): Record<string, number> {
  const start: number = css.indexOf(`${selector} {`);
  const block: string = css.slice(start, css.indexOf("}", start));
  const scrimAlpha: number = Number(/--scrim-alpha:\s*([0-9.]+);/.exec(block)?.[1]);
  return { bar: 0.92, "notification-surface": 0.86, scrim: scrimAlpha };
}

const camelCase = (name: string): string =>
  name.replace(/-([a-z0-9])/g, (_, letter: string) => letter.toUpperCase());

/** The raw colour string THEME must hold for a CSS triple. */
function expectedThemeValue(triple: string, alpha: number | undefined): string {
  if (alpha === undefined) return `hsl(${triple})`;
  const [hue, saturation, lightness] = triple.split(" ");
  return `hsla(${hue}, ${saturation}, ${lightness}, ${alpha})`;
}

const SCHEMES: [ColorScheme, string][] = [
  ["light", ":root"],
  ["dark", ".dark:root"],
];

describe("THEME mirrors global.css", () => {
  for (const [scheme, selector] of SCHEMES) {
    it(`${scheme}: every CSS colour variable has the same value in THEME`, () => {
      const variables: Map<string, string> = cssVariables(selector);
      const alpha: Record<string, number> = cssAlpha(selector);
      const theme: Record<string, string> = THEME[scheme];

      expect(variables.size).toBeGreaterThan(80);
      for (const [name, triple] of variables) {
        expect({ name, value: theme[camelCase(name)] }).toEqual({
          name,
          value: expectedThemeValue(triple, alpha[name]),
        });
      }
      expect(Object.keys(theme).sort()).toEqual([...variables.keys()].map(camelCase).sort());
    });
  }

  it("light and dark define the same variables", () => {
    expect([...cssVariables(".dark:root").keys()].sort()).toEqual(
      [...cssVariables(":root").keys()].sort()
    );
  });
});
