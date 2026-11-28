import { afterAll, beforeAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { act } from "react";
import type { Root } from "react-dom/client";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import { GlobalRegistrator } from "@happy-dom/global-registrator";
import en from "@/locales/en.json";

const actualHooks = { ...(await import("../hooks/use-kazan-appearance")) };
/** The appearance saves the mocked mutation received, and whether one is running. */
const saves: unknown[] = [];
let pending = false;
const mutation = () => ({ isPending: pending, mutate: (value: unknown) => saves.push(value) });
mock.module("../hooks/use-kazan-appearance", () => ({
  ...actualHooks,
  useKazanAppearance: () => ({ layout: "classic", background: "plain" }),
  useUpdateKazanAppearance: mutation,
  useUpdateTheme: mutation,
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

describe("AppearanceSettings while a save is pending", () => {
  let createRoot: (container: Element) => Root;
  beforeAll(async () => {
    GlobalRegistrator.register();
    (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    // React DOM decides at load whether a DOM exists, so it loads after the registration.
    ({ createRoot } = await import("react-dom/client"));
  });
  afterAll(async () => {
    await GlobalRegistrator.unregister();
  });

  beforeEach(() => {
    saves.length = 0;
    pending = false;
    document.body.innerHTML = "";
  });

  const mount = async () => {
    const container = document.createElement("div");
    document.body.append(container);
    const root = createRoot(container);
    const ui = () => (
      <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
        <AppearanceSettings />
      </NextIntlClientProvider>
    );
    await act(async () => root.render(ui()));
    const rerender = async () => act(async () => root.render(ui()));
    return { container, rerender };
  };

  /** The Modern and Classic tiles of the layout group. */
  const layoutTiles = (container: HTMLElement) => [
    ...(container
      .querySelector('[role="radiogroup"]')
      ?.querySelectorAll<HTMLButtonElement>('[role="radio"]') ?? []),
  ];

  test("keeps the focus on the chosen tile and ignores clicks and arrow keys", async () => {
    const { container, rerender } = await mount();
    const [modern, classic] = layoutTiles(container);
    classic!.focus();

    pending = true;
    await rerender();

    expect(classic!.hasAttribute("disabled")).toBe(false);
    expect(classic!.getAttribute("aria-disabled")).toBe("true");
    expect(document.activeElement).toBe(classic!);

    await act(async () => {
      classic!.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowLeft", bubbles: true }));
      modern!.click();
    });
    expect(saves).toEqual([]);
    expect(document.activeElement).toBe(classic!);
  });

  test("moves and saves the choice with an arrow key when no save runs", async () => {
    const { container } = await mount();
    const [, classic] = layoutTiles(container);
    classic!.focus();

    await act(async () => {
      classic!.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowLeft", bubbles: true }));
    });
    expect(saves).toEqual([{ layout: "modern" }]);
    expect(document.activeElement).toBe(layoutTiles(container)[0]!);
  });
});
