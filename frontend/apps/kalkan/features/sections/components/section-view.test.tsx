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
// states and the messages are real. The tree and the deleted-pages card are
// stand-ins with their own tests.
type TrashState = { data?: PageTrashItem[]; error?: unknown };

let root: PageListItem | null;
let trashState: TrashState;
let trashEnabled: boolean | undefined;
let isAdmin: boolean;
let isMember: boolean;

const actualSectionRoot = { ...(await import("@/features/pages/hooks/use-section-root")) };
const actualPages = { ...(await import("@/features/pages/hooks/use-pages")) };
const actualRoles = { ...(await import("@/hooks/use-is-organization-admin")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualTree = { ...(await import("./section-tree")) };
const actualDeleted = { ...(await import("./deleted-pages-card")) };

const idleMutation = { mutate: () => undefined, isPending: false, error: null };

mock.module("@/features/pages/hooks/use-section-root", () => ({
  ...actualSectionRoot,
  useSectionRoot: () => ({
    section: { root, children: [] },
    data: { data: root ? [root] : [] },
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
mock.module("./section-tree", () => ({ ...actualTree, SectionTree: () => <div data-tree="" /> }));
mock.module("./deleted-pages-card", () => ({
  ...actualDeleted,
  DeletedPagesCard: () => <div data-deleted="" />,
}));

afterAll(() => {
  mock.module("@/features/pages/hooks/use-section-root", () => actualSectionRoot);
  mock.module("@/features/pages/hooks/use-pages", () => actualPages);
  mock.module("@/hooks/use-is-organization-admin", () => actualRoles);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("./section-tree", () => actualTree);
  mock.module("./deleted-pages-card", () => actualDeleted);
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
});

describe("section without a live root", () => {
  test("a root in the trash is offered for restore, not set up again", () => {
    trashState = { data: [DELETED_ROOT] };
    const html = render();
    expect(html).toContain("This section is in the trash");
    expect(html).toContain("Ayla Demir");
    expect(html).toContain("Restore section");
    expect(html).toContain('href="/trash"');
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
    root = {
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
