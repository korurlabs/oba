import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

// The history lists a page's revisions and shows the newest one below. The
// page, revision list and revision hooks are replaced; the translator prints
// its key and values, and the dates print their raw value.
type QueryState = { data?: { success: boolean; data: unknown }; isError: boolean; error?: unknown };

let page: QueryState;
let revisions: QueryState;
let revision: QueryState;
let revisionRequests: (string | undefined)[];

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
  usePage: () => ({ ...page, refetch: async () => undefined }),
  usePageRevisions: () => ({ ...revisions, refetch: async () => undefined }),
  usePageRevision: (_pageId: string, revisionId: string | undefined) => {
    revisionRequests.push(revisionId);
    return { ...revision, refetch: async () => undefined };
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

const { HistoryView } = await import("./history-view");
const { RevisionCompare } = await import("./revision-compare");

const loaded = (data: unknown): QueryState => ({ data: { success: true, data }, isError: false });

const listItem = (id: string, version: number, locale: "TR" | "EN") => ({
  id,
  locale,
  title: `Policy v${version}`,
  version,
  createdAt: `2026-11-0${version}T10:00:00Z`,
  createdByName: version === 1 ? null : "Ali",
});

const currentPage = {
  id: "page-1",
  parentId: "root",
  rootId: "root",
  slug: "policy",
  path: "policies/policy",
  compliance: true,
  version: 3,
  level: "VIEW",
  translations: [
    {
      locale: "EN",
      title: "Policy",
      body: "# Policy\ncurrent line",
      updatedAt: "2026-11-03T10:00:00Z",
      updatedByName: "Ali",
    },
  ],
  updatedAt: "2026-11-03T10:00:00Z",
};

beforeEach(() => {
  page = loaded(currentPage);
  revisions = { isError: false };
  revision = { isError: false };
  revisionRequests = [];
});

describe("HistoryView", () => {
  test("names the page, links back to it and shows the loading outline", () => {
    const html = renderToStaticMarkup(<HistoryView pageId="page-1" />);

    expect(html).toContain("Policy");
    expect(html).toContain('href="/pages/page-1"');
    expect(html).toContain('aria-label="loading"');
  });

  test("shows the empty state for a page without revisions", () => {
    revisions = loaded([]);

    expect(renderToStaticMarkup(<HistoryView pageId="page-1" />)).toContain("emptyHint");
  });

  test("shows a retry when the revisions failed to load", () => {
    revisions = { isError: true, error: new Error("server down") };

    const html = renderToStaticMarkup(<HistoryView pageId="page-1" />);

    expect(html).toContain('role="alert"');
    expect(html).toContain("server down");
  });

  test("lists locale, title, version, time and author, and opens the newest revision", () => {
    revisions = loaded([listItem("rev-2", 2, "EN"), listItem("rev-1", 1, "TR")]);
    revision = loaded({ ...listItem("rev-2", 2, "EN"), body: "# Policy\nold line" });

    const html = renderToStaticMarkup(<HistoryView pageId="page-1" />);

    expect(html).toContain('aria-label="tableLabel"');
    expect(html).toContain("locales.EN");
    expect(html).toContain("locales.TR");
    expect(html).toContain("Policy v1");
    expect(html).toContain("dateTime:2026-11-02T10:00:00Z");
    expect(html).toContain("unknownUser");
    expect(revisionRequests).toContain("rev-2");
    expect(html).toContain("revisionHeading(2,locales.EN)");
    expect(html).toContain("old line");
    expect(html).toContain("modeCompare");
  });

  test("shows the revision's own loading and error states", () => {
    revisions = loaded([listItem("rev-2", 2, "EN")]);

    expect(renderToStaticMarkup(<HistoryView pageId="page-1" />)).toContain(
      'aria-label="revisionLoading"'
    );

    revision = { isError: true, error: new Error("revision gone") };
    const html = renderToStaticMarkup(<HistoryView pageId="page-1" />);
    expect(html).toContain("revisionLoadError");
    expect(html).toContain("revision gone");
  });
});

describe("RevisionCompare", () => {
  test("puts the revision beside the current text and marks the changed lines", () => {
    const html = renderToStaticMarkup(
      <RevisionCompare revisionBody={"# Policy\nold line"} currentBody={"# Policy\ncurrent line"} />
    );

    expect(html).toContain("thisRevision");
    expect(html).toContain("current");
    expect(html).toContain('data-kind="same"');
    expect(html).toContain('data-kind="changed"');
    expect(html).toContain("old line");
    expect(html).toContain("current line");
    expect(html).toContain("bg-error-light");
    expect(html).toContain("bg-success-light");
  });
});
