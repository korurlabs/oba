import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ApiError } from "@oba/shared/api/axios";
import en from "@/locales/en.json";
import { pageErrorText } from "@/features/pages/lib/page-error";
import type { PageListItem, PageTrashItem } from "@/features/pages/schemas";

// The section's tree, the trash and the roles are fixed values; the view, its
// states and the messages are real. The tree is a stand-in with its own
// tests, and the search reads a fixed value instead of the address.
type TrashState = { data?: PageTrashItem[]; error?: unknown };

let root: PageListItem | null;
let children: PageListItem[];
let trashState: TrashState;
let trashEnabled: boolean | undefined;
let isAdmin: boolean;
let isMember: boolean;
let search: string | null;
let treeProps: { visibleIds?: ReadonlySet<string> } | undefined;

const actualSectionRoot = { ...(await import("@/features/pages/hooks/use-section-root")) };
const actualPages = { ...(await import("@/features/pages/hooks/use-pages")) };
const actualRoles = { ...(await import("@/hooks/use-is-organization-admin")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualTree = { ...(await import("./section-tree")) };
const actualNuqs = { ...(await import("nuqs")) };

const idleMutation = { mutate: () => undefined, isPending: false, error: null };

mock.module("@/features/pages/hooks/use-section-root", () => ({
  ...actualSectionRoot,
  useSectionRoot: () => ({
    section: { root, children: root ? children : [] },
    data: { data: root ? [root, ...children] : [] },
    isPending: false,
    isError: false,
    error: null,
    refetch: async () => undefined,
  }),
  useSetUpSection: () => idleMutation,
}));
mock.module("@/features/pages/hooks/use-pages", () => ({
  ...actualPages,
  useTrash: ({ enabled }: { enabled?: boolean } = {}) => {
    trashEnabled = enabled;
    return {
      data: enabled && trashState.data ? { data: trashState.data } : undefined,
      isError: Boolean(enabled) && trashState.error !== undefined,
      error: trashState.error ?? null,
      refetch: async () => undefined,
    };
  },
  useRestorePage: () => idleMutation,
}));
mock.module("@/hooks/use-is-organization-admin", () => ({
  ...actualRoles,
  useIsOrganizationAdmin: () => isAdmin,
  useIsOrganizationMember: () => isMember,
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  Link: ({ href, children, ...rest }: { href: string; children: ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));
mock.module("./section-tree", () => ({
  ...actualTree,
  SectionTree: (props: { visibleIds?: ReadonlySet<string> }) => {
    treeProps = props;
    return <div data-tree="" />;
  },
}));
mock.module("nuqs", () => ({
  ...actualNuqs,
  useQueryState: () => [search, () => Promise.resolve(new URLSearchParams())],
}));

afterAll(() => {
  mock.module("@/features/pages/hooks/use-section-root", () => actualSectionRoot);
  mock.module("@/features/pages/hooks/use-pages", () => actualPages);
  mock.module("@/hooks/use-is-organization-admin", () => actualRoles);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("./section-tree", () => actualTree);
  mock.module("nuqs", () => actualNuqs);
});

const { SectionView } = await import("./section-view");

const DELETED_ROOT: PageTrashItem = {
  id: "root",
  title: "Policies",
  path: "Policies",
  deletedAt: "2026-11-21T09:00:00Z",
  deletedByName: "Ayla Demir",
  descendantCount: 3,
};

const ROOT: PageListItem = {
  id: "root",
  parentId: null,
  rootId: null,
  slug: "policies",
  path: "policies",
  position: 0,
  compliance: false,
  titles: { EN: "Policy" },
  level: "MANAGE",
};

function child(id: string, title: string, position: number): PageListItem {
  return {
    ...ROOT,
    id,
    parentId: ROOT.id,
    rootId: ROOT.id,
    slug: id,
    path: `policies/${id}`,
    position,
    titles: { EN: title },
    level: null,
  };
}

function render(): string {
  return renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
        <SectionView slug="policies" />
      </NextIntlClientProvider>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  root = null;
  trashState = { data: [] };
  trashEnabled = undefined;
  isAdmin = true;
  isMember = true;
  search = null;
  treeProps = undefined;
  children = [];
});

describe("section without a live root", () => {
  test("a root in the trash is offered for restore, not set up again", () => {
    trashState = { data: [DELETED_ROOT] };
    const html = render();
    expect(html).toContain("This section is in the trash");
    expect(html).toContain("Ayla Demir");
    expect(html).toContain("Restore section");
    expect(html).not.toContain('href="/trash"');
    expect(html).not.toContain("Set up section");
  });

  test("a deleted page below another section's root does not count", () => {
    trashState = { data: [{ ...DELETED_ROOT, id: "other", path: "policies-old" }] };
    const html = render();
    expect(html).toContain("Set up section");
    expect(html).not.toContain("This section is in the trash");
  });

  test("an admin whose trash does not hold the root may set the section up", () => {
    expect(render()).toContain("Set up section");
  });

  test("nothing is offered while the trash loads", () => {
    trashState = {};
    const html = render();
    expect(html).toContain('aria-label="Loading the section"');
    expect(html).not.toContain("Set up section");
  });

  test("a trash that fails to load is shown with a retry", () => {
    trashState = { error: new ApiError("Boom", { status: 500 }) };
    const html = render();
    expect(html).toContain("The trash could not be loaded");
    expect(html).toContain("Try again");
    expect(html).not.toContain("Set up section");
  });

  test("a guest is not sent to the trash it may not read", () => {
    isAdmin = false;
    isMember = false;
    const html = render();
    expect(trashEnabled).toBe(false);
    expect(html).toContain("This section is not set up yet");
  });

  test("a section with a live root does not read the trash for its root", () => {
    root = ROOT;
    render();
    expect(trashEnabled).toBe(false);
  });
});

describe("a refused set-up", () => {
  test("shows the server's text for a second root", () => {
    const error = new ApiError("The section already has a root page.", {
      status: 409,
      code: "KLK_STA_004",
    });
    expect(pageErrorText(error, (key) => key, "toast.sectionSetUpError")).toBe(
      "The section already has a root page."
    );
  });
});

describe("section with a live root", () => {
  beforeEach(() => {
    root = ROOT;
    children = [child("access", "Access control", 0), child("backup", "Backups", 1)];
  });

  test("offers a search and a new page, and no link to the root page or its deleted pages", () => {
    const html = render();
    expect(html).toContain('aria-label="Search pages"');
    expect(html).toContain("New page");
    expect(html).not.toContain("Section page");
    expect(html).not.toContain("Deleted pages");
    expect(treeProps?.visibleIds).toBeUndefined();
  });

  test("a MANAGE holder gets the root page's history and access", () => {
    const html = render();
    expect(html).toContain('href="/pages/root/history"');
    expect(html).toContain('href="/pages/root/permissions"');
    expect(html).toContain("History");
    expect(html).toContain("Access");
  });

  test("a reader gets the history but not the access screen", () => {
    root = { ...ROOT, level: "VIEW" };
    const html = render();
    expect(html).toContain('href="/pages/root/history"');
    expect(html).not.toContain('href="/pages/root/permissions"');
  });

  test("a guest with MANAGE on the root is not offered the access screen", () => {
    isAdmin = false;
    isMember = false;
    const html = render();
    expect(html).toContain('href="/pages/root/history"');
    expect(html).not.toContain('href="/pages/root/permissions"');
  });

  test("narrows the tree to the pages that answer the search", () => {
    search = "backup";
    render();
    expect([...(treeProps?.visibleIds ?? [])]).toEqual(["backup"]);
  });

  test("a search without a hit offers to clear it, not to add a page", () => {
    search = "risk";
    const html = render();
    expect(html).toContain("No pages match your search");
    expect(html).toContain("Clear search");
    expect(html).not.toContain("data-tree");
  });
});
