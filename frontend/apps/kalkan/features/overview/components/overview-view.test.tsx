import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

// The overview reads the page tree and the recent list. Both hooks are
// replaced, the translator prints its key and values, and the dates print
// their raw value, so the test reads which state each part shows.
type QueryState = {
  data?: { success: boolean; data: unknown[] };
  isError: boolean;
  error?: unknown;
};

let tree: QueryState;
let recent: QueryState;
let recentLimits: number[];

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualIntl = { ...(await import("next-intl")) };
const actualPages = { ...(await import("@/features/pages/hooks/use-pages")) };
const actualDates = { ...(await import("@oba/shared/i18n/use-date-format")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };

mock.module("next-intl", () => ({
  ...actualIntl,
  useLocale: () => "en",
  useTranslations: () => (key: string, values?: Record<string, unknown>) =>
    values ? `${key}(${Object.values(values).join(",")})` : key,
}));
mock.module("@/features/pages/hooks/use-pages", () => ({
  ...actualPages,
  usePageTree: () => ({ ...tree, refetch: async () => undefined }),
  useRecentPages: (limit: number) => {
    recentLimits.push(limit);
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
  Link: ({
    href,
    children,
    className,
  }: {
    href: string;
    children: ReactNode;
    className?: string;
  }) => (
    <a href={href} className={className}>
      {children}
    </a>
  ),
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("@/features/pages/hooks/use-pages", () => actualPages);
  mock.module("@oba/shared/i18n/use-date-format", () => actualDates);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
});

const { OverviewView } = await import("./overview-view");

function treePage(id: string, slug: string, parentId: string | null) {
  return {
    id,
    parentId,
    rootId: parentId === null ? null : "root-policies",
    slug,
    path: slug,
    position: 0,
    compliance: false,
    titles: {},
    level: parentId === null ? "VIEW" : null,
  };
}

const loading: QueryState = { isError: false };
const loaded = (data: unknown[]): QueryState => ({ data: { success: true, data }, isError: false });

beforeEach(() => {
  tree = loading;
  recent = loading;
  recentLimits = [];
});

describe("OverviewView", () => {
  test("shows the loading outline while the tree and the recent list load", () => {
    const html = renderToStaticMarkup(<OverviewView />);

    expect(html).toContain('aria-label="loading"');
    expect(html).toContain('aria-label="recentLoading"');
    expect(recentLimits).toEqual([10]);
  });

  test("shows a card per section with its page count, grouped like the sidebar", () => {
    tree = loaded([
      treePage("root-policies", "policies", null),
      treePage("p1", "access", "root-policies"),
      treePage("p2", "backup", "p1"),
    ]);
    recent = loaded([]);

    const html = renderToStaticMarkup(<OverviewView />);

    for (const group of ["compliance", "inventory", "knowledge", "legal"]) {
      expect(html).toContain(`groups.${group}`);
    }
    expect(html).toContain('href="/policies"');
    expect(html).toContain("pageCount(2)");
    expect(html).toContain('href="/incentives"');
    expect(html).toContain("notSetUp");
    expect(html).toContain("recentEmpty");
  });

  test("lists the recent changes with section, time and author, linking to each page", () => {
    tree = loaded([]);
    recent = loaded([
      {
        id: "page-1",
        title: "Access policy",
        path: "policies/access",
        rootSlug: "Policies",
        updatedAt: "2026-11-20T10:00:00Z",
        updatedByName: "Ayşe",
      },
      {
        id: "page-2",
        title: null,
        path: "isms/scope",
        rootSlug: "isms",
        updatedAt: "2026-11-19T10:00:00Z",
        updatedByName: null,
      },
    ]);

    const html = renderToStaticMarkup(<OverviewView />);

    expect(html).toContain('href="/pages/page-1"');
    expect(html).toContain("Access policy");
    expect(html).toContain("sections.policies");
    expect(html).toContain("dateTime:2026-11-20T10:00:00Z");
    expect(html).toContain("Ayşe");
    expect(html).toContain(">scope<");
    expect(html).toContain("unknownUser");
    // The sidebar leads to the full list, so the card repeats no link to it.
    expect(html).not.toContain('href="/recent"');
  });

  test("shows a retry for each part that failed to load", () => {
    tree = { isError: true, error: new Error("tree down") };
    recent = { isError: true, error: new Error("recent down") };

    const html = renderToStaticMarkup(<OverviewView />);

    expect(html).toContain("tree down");
    expect(html).toContain("recentLoadError");
    expect(html).toContain("recent down");
  });
});
