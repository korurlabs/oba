import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";

// The user and the router are stand-ins; the tab row, the role check, the
// icons and the messages are real.
let pathname: string;
let role: "OWNER" | "MEMBER";

const actualMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };

mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualMe,
  useGetMe: () => ({
    data: { data: { organizations: [{ id: "org-1", role, organizationStatus: "ACTIVE" }] } },
  }),
}));
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
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualMe);
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
    const label = (match[2] ?? "").replace(/<[^>]*>/g, "");
    return [href, label, attributes.includes('aria-current="page"')];
  });
}

beforeEach(() => {
  pathname = "/settings/columns";
  role = "MEMBER";
});

describe("AccountNav", () => {
  test("ends with the Download CLI tab to /downloads", () => {
    const rows = tabs(markup());
    expect(rows.at(-1)?.[0]).toBe("/downloads");
    expect(rows.at(-1)?.[1]).toBe(en.sidebar.downloads);
  });

  test("keeps Download CLI last also for a manager, after Organizations and Alerts", () => {
    role = "OWNER";
    const labels = tabs(markup()).map((row) => row[1]);
    expect(labels.at(-1)).toBe(en.sidebar.downloads);
    expect(labels).toContain(en.sidebar.organizations);
    expect(labels.indexOf(en.sidebar.alerts)).toBeLessThan(labels.indexOf(en.sidebar.downloads));
  });

  test("never marks Download CLI active, not even on its own page", () => {
    pathname = "/downloads";
    const rows = tabs(markup());
    expect(rows.find((row) => row[0] === "/downloads")?.[2]).toBe(false);
    expect(rows.some((row) => row[2])).toBe(false);
  });

  test("marks the settings tab of the page in view and nothing else", () => {
    pathname = "/settings/alerts";
    const active = tabs(markup()).filter((row) => row[2]);
    expect(active.map((row) => row[0])).toEqual(["/settings/alerts"]);
    expect(active[0]?.[1]).toBe(en.sidebar.alerts);
  });

  test("hides Organizations from a member and shows it to a manager", () => {
    expect(tabs(markup()).map((row) => row[1])).not.toContain(en.sidebar.organizations);
    role = "OWNER";
    expect(tabs(markup()).map((row) => row[1])).toContain(en.sidebar.organizations);
  });
});
