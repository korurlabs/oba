import { afterAll, afterEach, beforeEach, describe, expect, mock, test } from "bun:test";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { OPS_GRID_SURFACE } from "@oba/ui/components/ops-grid";
import { SidebarMenu, SidebarProvider } from "@oba/ui/sky/sidebar";
import en from "@/locales/en.json";
import { cleanup, renderLive, type Screen } from "@/lib/testing/live-render";
import { PAGE_LIMITS, type PageListItem } from "../schemas";

// The create hook and the router are stand-ins; the node, its field, the tree
// helpers, the sidebar primitives and the messages are real.
let requests: unknown[];

const actualPages = { ...(await import("../hooks/use-pages")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };

mock.module("../hooks/use-pages", () => ({
  ...actualPages,
  useCreatePage: () => ({
    isPending: false,
    mutate: (request: unknown) => {
      requests.push(request);
    },
  }),
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ push: () => undefined, replace: () => undefined }),
}));

afterAll(() => {
  mock.module("../hooks/use-pages", () => actualPages);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
});

const { PageTreeNode } = await import("./page-tree-node");

function page(overrides: Partial<PageListItem> & Pick<PageListItem, "id" | "slug">): PageListItem {
  return {
    parentId: null,
    rootId: null,
    path: overrides.slug,
    position: 0,
    compliance: false,
    titles: { EN: overrides.slug },
    level: "EDIT",
    ...overrides,
  };
}

const ROOT = page({ id: "root", slug: "policies", titles: { EN: "Policy" } });
const CHILD = page({
  id: "child",
  slug: "access",
  parentId: "root",
  rootId: "root",
  path: "policies/access",
  titles: { EN: "Access control" },
});
const GRANDCHILD = page({
  id: "grand",
  slug: "review",
  parentId: "child",
  rootId: "root",
  path: "policies/access/review",
  titles: { EN: "Review" },
});

function Link({ href, children, ...rest }: { href: string; children?: ReactNode }) {
  return (
    <a href={href} {...rest}>
      {children}
    </a>
  );
}

type Options = {
  pages: PageListItem[];
  node?: PageListItem;
  canEdit?: boolean;
  open?: string[];
  pathname?: string;
  toggled?: Array<[string, boolean]>;
  section?: boolean;
};

async function show(options: Options): Promise<Screen> {
  const toggled = options.toggled ?? [];
  return renderLive(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <SidebarProvider>
        <SidebarMenu>
          <PageTreeNode
            page={options.node ?? ROOT}
            pages={options.pages}
            canEdit={options.canEdit ?? true}
            openIds={new Set(options.open ?? [])}
            onToggle={(id: string, open: boolean) => toggled.push([id, open])}
            pathname={options.pathname ?? "/"}
            link={Link}
            section={
              options.section === false
                ? undefined
                : { label: "Policy", href: "/policies", icon: () => null }
            }
          />
        </SidebarMenu>
      </SidebarProvider>
    </NextIntlClientProvider>
  );
}

const chevron = (screen: Screen, name: string, open: boolean) =>
  screen.byAttribute(
    "aria-label",
    (open ? en.sidebar.collapse : en.sidebar.expand).replace("{name}", name)
  )[0];
const plus = (screen: Screen, name: string) =>
  screen.byAttribute("aria-label", en.sidebar.addChild.replace("{name}", name))[0];

beforeEach(() => {
  requests = [];
});

afterEach(cleanup);

describe("PageTreeNode chevron", () => {
  test("is absent on a page without children", async () => {
    const screen = await show({ pages: [ROOT] });
    expect(chevron(screen, "Policy", false)).toBeUndefined();
    expect(chevron(screen, "Policy", true)).toBeUndefined();
  });

  test("shows on a page with children, collapsed, and asks to open it", async () => {
    const toggled: Array<[string, boolean]> = [];
    const screen = await show({ pages: [ROOT, CHILD], toggled });
    const button = chevron(screen, "Policy", false);
    expect(button?.attribute("aria-expanded")).toBe("false");
    expect(screen.text()).not.toContain("Access control");
    await button?.click();
    expect(toggled).toEqual([["root", true]]);
  });

  test("shows the children of an open page and asks to close it", async () => {
    const toggled: Array<[string, boolean]> = [];
    const screen = await show({ pages: [ROOT, CHILD], open: ["root"], toggled });
    const button = chevron(screen, "Policy", true);
    expect(button?.attribute("aria-expanded")).toBe("true");
    expect(screen.text()).toContain("Access control");
    await button?.click();
    expect(toggled).toEqual([["root", false]]);
  });

  test("shows a deeper level only when its own parent is open too", async () => {
    const pages = [ROOT, CHILD, GRANDCHILD];
    const closed = await show({ pages, open: ["root"] });
    expect(closed.text()).toContain("Access control");
    expect(closed.text()).not.toContain("Review");
    await cleanup();
    const open = await show({ pages, open: ["root", "child"] });
    expect(open.text()).toContain("Review");
  });
});

describe("PageTreeNode plus", () => {
  test("shows for a reader who may edit and is labelled with the page", async () => {
    const screen = await show({ pages: [ROOT] });
    expect(plus(screen, "Policy")?.attribute("aria-expanded")).toBe("false");
  });

  test("is absent for a guest or any reader without EDIT on the section", async () => {
    const screen = await show({ pages: [ROOT], canEdit: false });
    expect(plus(screen, "Policy")).toBeUndefined();
  });

  test("shows on a page at depth 9 and not at the deepest level of 10", async () => {
    const at = (depth: number) =>
      page({
        id: `p${depth}`,
        slug: `p${depth}`,
        parentId: "root",
        path: Array.from({ length: depth }, (_, index) => `s${index}`).join("/"),
        titles: { EN: `Depth ${depth}` },
      });
    expect(PAGE_LIMITS.maxDepth).toBe(10);
    const nine = await show({ pages: [ROOT], node: at(9), section: false });
    expect(plus(nine, "Depth 9")).toBeDefined();
    await cleanup();
    const ten = await show({ pages: [ROOT], node: at(10), section: false });
    expect(plus(ten, "Depth 10")).toBeUndefined();
  });

  test("carries the selector panel's grid surface classes", async () => {
    const screen = await show({ pages: [ROOT] });
    const className = plus(screen, "Policy")?.attribute("className") ?? "";
    // The accent colour replaces the surface's ink colour on purpose, so the
    // plus draws in the azure of the selector's marks.
    for (const token of ["rounded-none", "border-[#1a64b8]", "bg-[#f6fbff]", "text-[#1a64b8]"]) {
      expect(className).toContain(token);
    }
    expect(OPS_GRID_SURFACE).toContain("bg-[#f6fbff]");
  });

  test("opens a name field under the row, which carries the grid surface classes too", async () => {
    const screen = await show({ pages: [ROOT] });
    await plus(screen, "Policy")?.click();
    const field = screen.byAttribute("aria-label", en.sidebar.newChildName)[0];
    expect(field).toBeDefined();
    const className = field?.attribute("className") ?? "";
    for (const token of ["rounded-none", "border-[#1a64b8]", "bg-[#f6fbff]", "text-[#0b2a47]"]) {
      expect(className).toContain(token);
    }
    expect(plus(screen, "Policy")?.attribute("aria-expanded")).toBe("true");
  });

  test("Escape closes the field and returns the focus to the plus", async () => {
    const screen = await show({ pages: [ROOT] });
    await plus(screen, "Policy")?.click();
    await screen.byAttribute("aria-label", en.sidebar.newChildName)[0]?.keyDown("Escape");
    expect(screen.byAttribute("aria-label", en.sidebar.newChildName)).toEqual([]);
    expect(plus(screen, "Policy")?.focused()).toBe(true);
  });

  test("a refused create keeps the field open", async () => {
    const screen = await show({ pages: [ROOT, CHILD] });
    await plus(screen, "Policy")?.click();
    await screen.byAttribute("aria-label", en.sidebar.newChildName)[0]?.change("Network");
    await screen.byTag("form")[0]?.submit();
    expect(requests).toHaveLength(1);
    expect(screen.byAttribute("aria-label", en.sidebar.newChildName)[0]?.value).toBe("Network");
  });

  test("hands the sibling slugs to the field so the new slug avoids them", async () => {
    const screen = await show({ pages: [ROOT, CHILD] });
    await plus(screen, "Policy")?.click();
    await screen.byAttribute("aria-label", en.sidebar.newChildName)[0]?.change("Access");
    await screen.byTag("form")[0]?.submit();
    expect(requests[0]).toMatchObject({ parentId: "root", slug: "access-2" });
  });
});

describe("PageTreeNode row", () => {
  test("a section root links to the section's route with its label", async () => {
    const screen = await show({ pages: [ROOT] });
    expect(screen.byAttribute("href", "/policies")).not.toHaveLength(0);
    expect(screen.text()).toContain("Policy");
  });

  test("a page below the root links to its own page route", async () => {
    const screen = await show({ pages: [ROOT, CHILD], open: ["root"] });
    expect(screen.byAttribute("href", "/pages/child")).not.toHaveLength(0);
  });
});
