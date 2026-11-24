import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";

// The router is a stand-in; the tab row, the icons and the messages are real.
let pathname: string;

const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };

mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  usePathname: () => pathname,
  Link: ({ href, children, ...rest }: { href: string; children?: ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

afterAll(() => {
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
});

const { AccountNav } = await import("./account-nav");

function markup(): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <AccountNav />
    </NextIntlClientProvider>
  );
}

/** The tabs as `[href, label, active]`, in order. */
function tabs(html: string): Array<[string, string, boolean]> {
  return [...html.matchAll(/<a ([^>]*)>(.*?)<\/a>/g)].map((match) => {
    const attributes = match[1] ?? "";
    const href = /href="([^"]*)"/.exec(attributes)?.[1] ?? "";
    // The label is the text node right after the icon, before the active indicator.
    const label = ((match[2] ?? "").split("</svg>").at(-1) ?? "").split("<")[0] ?? "";
    return [href, label, attributes.includes('aria-current="page"')];
  });
}

beforeEach(() => {
  pathname = "/settings/columns";
});

describe("AccountNav", () => {
  test("shows only Kazan's tabs: Table columns, Alerts, then Download CLI", () => {
    expect(tabs(markup()).map((row) => [row[0], row[1]])).toEqual([
      ["/settings/columns", en.sidebar.tableColumns],
      ["/settings/alerts", en.sidebar.alerts],
      ["/settings/cli", en.sidebar.downloads],
    ]);
  });

  test("links to no other Oba app", () => {
    expect(tabs(markup()).every((row) => row[0].startsWith("/"))).toBe(true);
  });

  test("marks Download CLI active on its own page and nothing else", () => {
    pathname = "/settings/cli";
    const active = tabs(markup()).filter((row) => row[2]);
    expect(active.map((row) => row[0])).toEqual(["/settings/cli"]);
    expect(active[0]?.[1]).toBe(en.sidebar.downloads);
  });

  test("marks the settings tab of the page in view and nothing else", () => {
    pathname = "/settings/alerts";
    const active = tabs(markup()).filter((row) => row[2]);
    expect(active.map((row) => row[0])).toEqual(["/settings/alerts"]);
    expect(active[0]?.[1]).toBe(en.sidebar.alerts);
  });
});
