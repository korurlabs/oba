import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ApiError } from "@oba/shared/api/axios";
import en from "@/locales/en.json";
import tr from "@/locales/tr.json";
import type { PageDetail, PageListItem, PageTrashItem } from "../schemas";

// The page, the tree, the trash and the role are fixed values; the view, its
// states and the messages are real. The editor body and the attachment list
// are stand-ins: the first loads Tiptap lazily in the browser only, the second
// is the shared component with its own tests.
type PageState = { data?: PageDetail; error?: unknown };

let pageState: PageState;
let trash: PageTrashItem[];
let tree: PageListItem[];
let isMember: boolean;

const actualPages = { ...(await import("../hooks/use-pages")) };
const actualRoles = { ...(await import("@/hooks/use-is-organization-admin")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualBody = { ...(await import("./page-markdown-body")) };
const actualAttachments = { ...(await import("./page-attachments-card")) };

const idleMutation = { mutate: () => undefined, isPending: false, error: null };

mock.module("../hooks/use-pages", () => ({
  ...actualPages,
  usePage: () => ({
    data: pageState.data ? { data: pageState.data } : undefined,
    isError: pageState.error !== undefined,
    error: pageState.error ?? null,
    refetch: async () => undefined,
  }),
  usePageTree: () => ({ data: { data: tree } }),
  useTrash: () => ({ data: { data: trash } }),
  useDeletePage: () => idleMutation,
  useRestorePage: () => idleMutation,
}));
mock.module("@/hooks/use-is-organization-admin", () => ({
  ...actualRoles,
  useIsOrganizationAdmin: () => false,
  useIsOrganizationMember: () => isMember,
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ replace: () => undefined, push: () => undefined }),
  Link: ({
    href,
    children,
    ...rest
  }: {
    href: string | { pathname: string; query?: Record<string, string> };
    children: ReactNode;
  }) => {
    const target =
      typeof href === "string"
        ? href
        : `${href.pathname}?${new URLSearchParams(href.query ?? {}).toString()}`;
    return (
      <a href={target} {...rest}>
        {children}
      </a>
    );
  },
}));
mock.module("./page-markdown-body", () => ({
  ...actualBody,
  PageMarkdownBody: ({ markdown }: { markdown: string }) => <div data-body="">{markdown}</div>,
}));
mock.module("./page-attachments-card", () => ({
  ...actualAttachments,
  PageAttachmentsCard: ({ pageId }: { pageId: string }) => <div data-attachments={pageId} />,
}));

afterAll(() => {
  mock.module("../hooks/use-pages", () => actualPages);
  mock.module("@/hooks/use-is-organization-admin", () => actualRoles);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("./page-markdown-body", () => actualBody);
  mock.module("./page-attachments-card", () => actualAttachments);
});

const { PageView } = await import("./page-view");

const ROOT: PageListItem = {
  id: "root",
  parentId: null,
  rootId: null,
  slug: "policies",
  path: "policies",
  position: 0,
  compliance: false,
  titles: { EN: "Policy" },
  level: "EDIT",
};
const PARENT: PageListItem = {
  ...ROOT,
  id: "parent",
  parentId: "root",
  rootId: "root",
  slug: "access",
  path: "policies/access",
  titles: { EN: "Access control", TR: "Erişim kontrolü" },
  level: null,
};
const CHILD: PageListItem = { ...PARENT, id: "child", parentId: "parent", slug: "review" };

function detail(overrides: Partial<PageDetail> = {}): PageDetail {
  return {
    id: "child",
    parentId: "parent",
    rootId: "root",
    slug: "review",
    path: "policies/access/review",
    compliance: true,
    version: 3,
    level: "EDIT",
    updatedAt: "2026-11-20T10:00:00Z",
    translations: [
      {
        locale: "EN",
        title: "Access review",
        body: "# Quarterly review",
        updatedAt: "2026-11-20T10:00:00Z",
        updatedByName: "Ayla Demir",
      },
      {
        locale: "TR",
        title: "Erişim gözden geçirmesi",
        body: "# Üç aylık gözden geçirme",
        updatedAt: "2026-11-20T10:00:00Z",
        updatedByName: null,
      },
    ],
    ...overrides,
  };
}

function render(locale: "en" | "tr" = "en"): string {
  return renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      <NextIntlClientProvider locale={locale} messages={locale === "en" ? en : tr} timeZone="UTC">
        <PageView pageId="child" />
      </NextIntlClientProvider>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  pageState = { data: detail() };
  trash = [];
  tree = [ROOT, PARENT, CHILD];
  isMember = true;
});

describe("page view states", () => {
  test("shows the outline while the page loads", () => {
    pageState = {};
    expect(render()).toContain('aria-label="Loading the page"');
  });

  test("answers a missing page with the not-found state and a way back", () => {
    pageState = { error: new ApiError("Not found", { status: 404, code: "KLK_NOT_001" }) };
    const html = render();
    expect(html).toContain("Page not found");
    expect(html).toContain('href="/"');
    expect(html).not.toContain("Restore page");
  });

  test("offers the restore when the missing page is in the caller's trash", () => {
    pageState = { error: new ApiError("Not found", { status: 404, code: "KLK_NOT_001" }) };
    trash = [
      {
        id: "child",
        title: "Access review",
        path: "policies/access/review",
        deletedAt: "2026-11-21T09:00:00Z",
        deletedByName: "Ayla Demir",
        descendantCount: 0,
      },
    ];
    const html = render();
    expect(html).toContain("This page is in the trash");
    expect(html).toContain("Restore page");
  });

  test("shows the no-access state for a refused level", () => {
    pageState = { error: new ApiError("Forbidden", { status: 403, code: "KLK_SEC_001" }) };
    expect(render()).toContain("No access to this page");
  });

  test("shows any other failure with a retry", () => {
    pageState = { error: new ApiError("Boom", { status: 500 }) };
    const html = render();
    expect(html).toContain("The page could not be loaded");
    expect(html).toContain("Try again");
  });
});

describe("page content", () => {
  test("reads the English translation for an English interface", () => {
    const html = render("en");
    expect(html).toContain("Access review");
    expect(html).toContain("# Quarterly review");
    expect(html).toContain("by Ayla Demir");
    expect(html).toContain(">Compliance<");
    expect(html).toContain('data-attachments="child"');
  });

  test("reads the Turkish translation for a Turkish interface", () => {
    const html = render("tr");
    expect(html).toContain("Erişim gözden geçirmesi");
    expect(html).toContain("# Üç aylık gözden geçirme");
  });

  test("falls back to the other language and says so", () => {
    pageState = {
      data: detail({ translations: detail().translations.filter((t) => t.locale === "EN") }),
    };
    const html = render("tr");
    expect(html).toContain("# Quarterly review");
    expect(html).toContain('role="note"');
  });

  test("says when the page has no body yet", () => {
    pageState = {
      data: detail({
        translations: detail().translations.map((translation) => ({ ...translation, body: "" })),
      }),
    };
    expect(render()).toContain("This page has no content yet.");
  });

  test("traces the page from its section through its parent", () => {
    const html = render();
    expect(html).toContain('href="/policies"');
    expect(html).toContain('href="/pages/parent"');
    expect(html.indexOf('href="/policies"')).toBeLessThan(html.indexOf('href="/pages/parent"'));
  });

  test("gives an editor the edit, new subpage and delete actions", () => {
    const html = render();
    expect(html).toContain('href="/pages/child/edit"');
    expect(html).toContain('href="/pages/new?parent=child"');
    expect(html).toContain(">Delete<");
  });

  test("keeps a reader's page read-only", () => {
    pageState = { data: detail({ level: "VIEW" }) };
    const html = render();
    expect(html).not.toContain("/edit");
    expect(html).not.toContain("/pages/new");
    expect(html).not.toContain(">Delete<");
  });

  test("lets only a manager delete a section's root page", () => {
    pageState = { data: detail({ id: "root", parentId: null, rootId: null, level: "EDIT" }) };
    const html = render();
    expect(html).toContain('href="/pages/root/edit"');
    expect(html).not.toContain(">Delete<");
  });
});
