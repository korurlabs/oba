import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

// The Recent page lists the changed pages in a table. The query hook is
// replaced, the translator prints its key, and the dates print their raw value.
type QueryState = {
  data?: { success: boolean; data: unknown[] };
  isError: boolean;
  error?: unknown;
};

let recent: QueryState;
let limits: number[];

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualIntl = { ...(await import("next-intl")) };
const actualPages = { ...(await import("@/features/pages/hooks/use-pages")) };
const actualDates = { ...(await import("@oba/shared/i18n/use-date-format")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };

mock.module("next-intl", () => ({
  ...actualIntl,
  useLocale: () => "en",
  useTranslations: () => (key: string) => key,
}));
mock.module("@/features/pages/hooks/use-pages", () => ({
  ...actualPages,
  useRecentPages: (limit: number) => {
    limits.push(limit);
    return { ...recent, refetch: async () => undefined };
  },
}));
mock.module("@oba/shared/i18n/use-date-format", () => ({
  ...actualDates,
  useDateFormat: () => ({
    formatDate: (value: string) => `date:${value}`,
    formatDateTime: (value: string) => `dateTime:${value}`,
  }),
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  Link: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("@/features/pages/hooks/use-pages", () => actualPages);
  mock.module("@oba/shared/i18n/use-date-format", () => actualDates);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
});

const { RecentView } = await import("./recent-view");

beforeEach(() => {
  recent = { isError: false };
  limits = [];
});

describe("RecentView", () => {
  test("asks for 50 pages and shows the loading outline meanwhile", () => {
    const html = renderToStaticMarkup(<RecentView />);

    expect(limits).toEqual([50]);
    expect(html).toContain('aria-label="loading"');
  });

  test("shows the empty state when nothing changed", () => {
    recent = { data: { success: true, data: [] }, isError: false };

    expect(renderToStaticMarkup(<RecentView />)).toContain("emptyHint");
  });

  test("lists title, section, time and author, linking to the page", () => {
    recent = {
      data: {
        success: true,
        data: [
          {
            id: "page-1",
            title: "Risk register",
            path: "risks/register",
            rootSlug: "risks",
            updatedAt: "2026-11-21T08:30:00Z",
            updatedByName: "Mehmet",
          },
        ],
      },
      isError: false,
    };

    const html = renderToStaticMarkup(<RecentView />);

    expect(html).toContain('aria-label="tableLabel"');
    expect(html).toContain('href="/pages/page-1"');
    expect(html).toContain("Risk register");
    expect(html).toContain("risks/register");
    expect(html).toContain("sections.risks");
    expect(html).toContain("date:2026-11-21T08:30:00Z");
    expect(html).toContain("Mehmet");
  });

  test("shows a retry when the list failed to load", () => {
    recent = { isError: true, error: new Error("server down") };

    const html = renderToStaticMarkup(<RecentView />);

    expect(html).toContain('role="alert"');
    expect(html).toContain("server down");
  });
});
