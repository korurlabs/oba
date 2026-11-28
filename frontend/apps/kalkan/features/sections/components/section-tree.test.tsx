import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import messages from "@/locales/en.json";
import type { PageListItem } from "@/features/pages/schemas";

// The section's data, the role, the writes and the search in the address are
// replaced by fixed values; the
// tree, the view and the messages are real. Links render as plain anchors, so the
// test reads where each one leads.
type SectionState = {
  isPending: boolean;
  isError: boolean;
  section: { root: PageListItem | null; children: PageListItem[] } | undefined;
  pages: PageListItem[];
};

let state: SectionState;
let isAdmin: boolean;
let isMember: boolean;

const actualSectionRoot = { ...(await import("@/features/pages/hooks/use-section-root")) };
const actualPages = { ...(await import("@/features/pages/hooks/use-pages")) };
const actualRoles = { ...(await import("@/hooks/use-is-organization-admin")) };
const actualMove = { ...(await import("../hooks/use-move-section-page")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualNuqs = { ...(await import("nuqs")) };

const idleMutation = { mutate: () => undefined, isPending: false };

mock.module("@/features/pages/hooks/use-section-root", () => ({
  ...actualSectionRoot,
  useSectionRoot: () => ({
    isPending: state.isPending,
    isError: state.isError,
    error: null,
    refetch: async () => undefined,
    section: state.section,
    data: { data: state.pages },
  }),
  useSetUpSection: () => idleMutation,
}));
mock.module("@/features/pages/hooks/use-pages", () => ({
  ...actualPages,
  useDeletePage: () => idleMutation,
  useRestorePage: () => idleMutation,
  useTrash: () => ({ data: { data: [] }, isError: false }),
}));
mock.module("@/hooks/use-is-organization-admin", () => ({
  ...actualRoles,
  useIsOrganizationAdmin: () => isAdmin,
  useIsOrganizationMember: () => isMember,
}));
mock.module("../hooks/use-move-section-page", () => ({
  ...actualMove,
  useMoveSectionPage: () => idleMutation,
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
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

mock.module("nuqs", () => ({
  ...actualNuqs,
  useQueryState: () => [null, () => Promise.resolve(new URLSearchParams())],
}));

afterAll(() => {
  mock.module("@/features/pages/hooks/use-section-root", () => actualSectionRoot);
  mock.module("@/features/pages/hooks/use-pages", () => actualPages);
  mock.module("@/hooks/use-is-organization-admin", () => actualRoles);
  mock.module("../hooks/use-move-section-page", () => actualMove);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("nuqs", () => actualNuqs);
});

const { SectionTree } = await import("./section-tree");
const { SectionView } = await import("./section-view");

function page(
  id: string,
  parentId: string | null,
  position: number,
  overrides: Partial<PageListItem> = {}
): PageListItem {
  return {
    id,
    parentId,
    rootId: parentId === null ? null : "00000000-0000-4000-8000-000000000000",
    slug: id,
    path: id,
    position,
    compliance: false,
    titles: { EN: `Title ${id}` },
    level: null,
    ...overrides,
  };
}

const ROOT = page("policies", null, 0, { id: "root", level: "EDIT" });
const ACCESS = page("access", "root", 0, { compliance: true });
const ACCESS_REVIEW = page("review", "access", 0);
const BACKUP = page("backup", "root", 1, { titles: { TR: "Yedekleme" } });
const TREE = [ROOT, BACKUP, ACCESS, ACCESS_REVIEW];

function render(node: ReactNode): string {
  return renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      <NextIntlClientProvider locale="en" messages={messages} timeZone="UTC">
        {node}
      </NextIntlClientProvider>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  isAdmin = false;
  isMember = true;
  state = {
    isPending: false,
    isError: false,
    section: { root: ROOT, children: [ACCESS, BACKUP] },
    pages: TREE,
  };
});

describe("section tree", () => {
  test("shows every page below the root in sibling order, nested by level", () => {
    const html = render(<SectionTree pages={TREE} root={ROOT} sectionTitle="Policy" canEdit />);
    expect(html).toContain('role="tree" aria-label="Pages of Policy"');
    const order = ["Title access", "Title review", "Yedekleme"].map((title) => html.indexOf(title));
    expect(order.every((index) => index >= 0)).toBe(true);
    expect([...order].sort((a, b) => a - b)).toEqual(order);
    expect(html).toContain('aria-level="2"');
    expect(html).toContain('href="/pages/review"');
    expect(html).not.toContain("Title policies");
  });

  test("marks compliance pages and offers folding only where a page has children", () => {
    const html = render(<SectionTree pages={TREE} root={ROOT} sectionTitle="Policy" canEdit />);
    expect(html).toContain(">Compliance<");
    expect(html).toContain('aria-label="Collapse Title access"');
    expect(html).not.toContain('aria-label="Collapse Yedekleme"');
  });

  test("gives an editor a menu and a drag handle on every page", () => {
    const html = render(<SectionTree pages={TREE} root={ROOT} sectionTitle="Policy" canEdit />);
    expect(html).toContain('aria-label="Actions for Title access"');
    expect(html).toContain('aria-label="Drag Yedekleme to move it"');
    expect(html).toContain('draggable="true"');
  });

  test("shows only the pages a search keeps, each in its place", () => {
    const html = render(
      <SectionTree
        pages={TREE}
        root={ROOT}
        sectionTitle="Policy"
        canEdit={false}
        visibleIds={new Set([ACCESS.id, ACCESS_REVIEW.id])}
      />
    );
    expect(html).toContain("Title review");
    expect(html).not.toContain("Yedekleme");
  });

  test("shows a reader the tree without any action", () => {
    const html = render(
      <SectionTree pages={TREE} root={ROOT} sectionTitle="Policy" canEdit={false} />
    );
    expect(html).not.toContain("Actions for");
    expect(html).not.toContain("Drag ");
    expect(html).not.toContain('draggable="true"');
  });
});

describe("section view", () => {
  test("shows the outline while the tree loads", () => {
    state = { ...state, isPending: true, section: undefined };
    expect(render(<SectionView slug="policies" />)).toContain('aria-label="Loading the section"');
  });

  test("offers an admin to set up a section that has no root yet", () => {
    isAdmin = true;
    state = { ...state, section: { root: null, children: [] }, pages: [] };
    const html = render(<SectionView slug="policies" />);
    expect(html).toContain("Set up section");
    expect(html).not.toContain("Section page");
  });

  test("tells anyone else to ask an admin", () => {
    state = { ...state, section: { root: null, children: [] }, pages: [] };
    const html = render(<SectionView slug="policies" />);
    expect(html).toContain("Ask an organization admin to set it up.");
    expect(html).not.toContain("Set up section");
  });

  test("invites an editor to add the first page of an empty section", () => {
    state = { ...state, section: { root: ROOT, children: [] }, pages: [ROOT] };
    const html = render(<SectionView slug="policies" />);
    expect(html).toContain("No pages yet");
    expect(html).toContain('href="/pages/new?parent=root"');
  });

  test("shows a reader an empty section without the new page action", () => {
    const readOnlyRoot = { ...ROOT, level: "VIEW" as const };
    state = { ...state, section: { root: readOnlyRoot, children: [] }, pages: [readOnlyRoot] };
    const html = render(<SectionView slug="policies" />);
    expect(html).toContain("Pages added to this section appear here.");
    expect(html).not.toContain("/pages/new");
  });

  test("shows the tree with the search and the new page action, without a link to the root page", () => {
    const html = render(<SectionView slug="policies" />);
    expect(html).toContain('role="tree"');
    expect(html).toContain('aria-label="Search pages"');
    expect(html).not.toContain('href="/pages/root"');
    expect(html).toContain('href="/pages/new?parent=root"');
  });

  test("keeps a guest's tree read-only even with EDIT on the root", () => {
    isMember = false;
    const html = render(<SectionView slug="policies" />);
    expect(html).toContain('role="tree"');
    expect(html).not.toContain("/pages/new");
    expect(html).not.toContain("Actions for");
  });
});
