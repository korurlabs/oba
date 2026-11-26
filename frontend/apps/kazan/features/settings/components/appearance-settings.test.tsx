import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";

const actualHooks = { ...(await import("../hooks/use-kazan-appearance")) };
const idle = { isPending: false, mutate: () => {} };
mock.module("../hooks/use-kazan-appearance", () => ({
  ...actualHooks,
  useKazanAppearance: () => ({ layout: "classic", background: "plain" }),
  useUpdateKazanAppearance: () => idle,
  useUpdateTheme: () => idle,
}));
afterAll(() => {
  mock.module("../hooks/use-kazan-appearance", () => actualHooks);
});

const { AppearanceSettings } = await import("./appearance-settings");

const html = renderToStaticMarkup(
  <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
    <AppearanceSettings />
  </NextIntlClientProvider>
);

/** The label of each checked tile. */
function checkedLabels(): string[] {
  return [
    ...html.matchAll(/aria-checked="true"[\s\S]*?<span class="text-foreground[^"]*">([^<]+)</g),
  ].map((match) => match[1] ?? "");
}

describe("AppearanceSettings", () => {
  test("offers the layout, the theme and the background as three radio groups", () => {
    expect(html.match(/role="radiogroup"/g)?.length).toBe(3);
    for (const label of [
      en.appearance.layout.modern,
      en.appearance.layout.classic,
      en.appearance.theme.system,
      en.appearance.theme.dark,
      en.appearance.background.grid,
      en.appearance.background.slate,
    ]) {
      expect(html).toContain(label);
    }
  });

  test("marks the saved layout and background, and no theme before the browser knows it", () => {
    expect(checkedLabels()).toEqual([en.appearance.layout.classic, en.appearance.background.plain]);
  });

  test("lets the tab key reach each group once, at its chosen tile", () => {
    expect(html.match(/tabindex="0"/g)?.length).toBe(3);
  });
});
