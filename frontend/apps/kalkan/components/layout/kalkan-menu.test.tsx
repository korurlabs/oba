import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import { SidebarProvider } from "@oba/ui/sky/sidebar";
import en from "@/locales/en.json";
import type { PageListItem } from "@/features/pages/schemas";
import { SECTION_SLUGS } from "@/lib/sections";

// The page tree and the role are fixed values; the menu, its tree nodes, the
// sidebar primitives and the messages are real.
let tree: PageListItem[] | undefined;
let isMember: boolean;

const actualPages = { ...(await import("@/features/pages/hooks/use-pages")) };
const actualRoles = { ...(await import("@/hooks/use-is-organization-admin")) };

mock.module("@/features/pages/hooks/use-pages", () => ({
  ...actualPages,
  usePageTree: () => ({ data: tree ? { data: tree } : undefined }),
}));
mock.module("@/hooks/use-is-organization-admin", () => ({
  ...actualRoles,
  useIsOrganizationMember: () => isMember,
}));

afterAll(() => {
  mock.module("@/features/pages/hooks/use-pages", () => actualPages);
  mock.module("@/hooks/use-is-organization-admin", () => actualRoles);
});

const { KalkanMenu } = await import("./kalkan-menu");

function Link({ href, children, ...rest }: { href: string; children?: ReactNode }) {
  return (
    <a href={href} {...rest}>
      {children}
    </a>
  );
}

function root(slug: string, overrides: Partial<PageListItem> = {}): PageListItem {
  return {
    id: `root-${slug}`,
    parentId: null,
    rootId: null,
    slug,
    path: slug,
    position: 0,
    compliance: false,
    titles: { EN: slug },
    level: "EDIT",
    ...overrides,
  };
}

function child(parent: PageListItem, slug: string, overrides: Partial<PageListItem> = {}) {
  return root(slug, {
    id: `${parent.id}-${slug}`,
    parentId: parent.id,
    rootId: parent.rootId ?? parent.id,
    path: `${parent.path}/${slug}`,
    titles: { EN: `Page ${slug}` },
    ...overrides,
  });
}

/** Every section set up with one child page, so each can show a chevron. */
function fullTree(): PageListItem[] {
  return SECTION_SLUGS.flatMap((slug: string) => {
    const section = root(slug);
    return [section, child(section, "first")];
  });
}

function markup(pathname = "/"): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <SidebarProvider>
        <KalkanMenu pathname={pathname} link={Link} />
      </SidebarProvider>
    </NextIntlClientProvider>
  );
}

const count = (html: string, needle: string) => html.split(needle).length - 1;
const expandLabel = (name: string) => `aria-label="${en.sidebar.expand.replace("{name}", name)}"`;
const addLabel = (name: string) => `aria-label="${en.sidebar.addChild.replace("{name}", name)}"`;

beforeEach(() => {
  tree = fullTree();
  isMember = true;
});

describe("KalkanMenu", () => {
  test("lists Overview and the four groups with their sections in order", () => {
    const html = markup();
    expect(html).toContain(">Overview<");
    for (const group of Object.values(en.sidebar.groups)) expect(html).toContain(`>${group}<`);
    const positions = Object.values(en.sidebar.sections).map((label) =>
      html.indexOf(`<span>${label}</span>`)
    );
    expect(positions.every((position) => position > -1)).toBe(true);
    expect([...positions].sort((a, b) => a - b)).toEqual(positions);
  });

  test("gives every one of the sixteen sections the same chevron and plus", () => {
    const html = markup();
    for (const label of Object.values(en.sidebar.sections)) {
      expect(html).toContain(expandLabel(label));
      expect(html).toContain(addLabel(label));
    }
    expect(count(html, `aria-label="Show the pages of `)).toBe(16);
    expect(count(html, `aria-label="Add a page under `)).toBe(16);
  });

  test("gives a Compliance, an Inventory, a Knowledge and a Legal section the same actions", () => {
    const html = markup();
    for (const label of [
      en.sidebar.sections.controls,
      en.sidebar.sections.assets,
      en.sidebar.sections.architecture,
      en.sidebar.sections.laws,
    ]) {
      expect(html).toContain(expandLabel(label));
      expect(html).toContain(addLabel(label));
    }
  });

  test("gives Overview neither a chevron nor a plus and links it to the dashboard", () => {
    const html = markup();
    expect(html).not.toContain(expandLabel("Overview"));
    expect(html).not.toContain(addLabel("Overview"));
    expect(html).toContain('href="/"');
  });

  test("has no New page link", () => {
    const html = markup();
    expect(html).not.toContain("New page");
    expect(html).not.toContain("/pages/new");
  });

  test("starts every section collapsed", () => {
    const html = markup();
    expect(html).not.toContain("Page first");
    expect(count(html, 'aria-expanded="false"')).toBeGreaterThanOrEqual(32);
  });

  test("opens the ancestors of the page in view and no other branch", () => {
    const policies = root("policies");
    const access = child(policies, "access");
    const review = child(access, "review");
    const controls = root("controls");
    const other = child(controls, "other");
    tree = [policies, access, review, controls, other];
    const html = markup(`/pages/${review.id}`);
    expect(html).toContain("Page access");
    expect(html).toContain("Page review");
    expect(html).not.toContain("Page other");
  });

  test("keeps a section folded on a page that is not in the tree", () => {
    const html = markup("/pages/unknown");
    expect(html).not.toContain("Page first");
  });

  test("shows a section whose root is missing as a plain link without actions", () => {
    tree = fullTree().filter((page) => page.slug !== "risks" && page.parentId !== "root-risks");
    const html = markup();
    expect(html).toContain("<span>Risk</span>");
    expect(html).not.toContain(expandLabel("Risk"));
    expect(html).not.toContain(addLabel("Risk"));
    expect(html).toContain('href="/risks"');
  });

  test("shows plain links while the tree loads", () => {
    tree = undefined;
    const html = markup();
    expect(html).toContain("<span>Control</span>");
    expect(count(html, "Show the pages of")).toBe(0);
    expect(count(html, "Add a page under")).toBe(0);
  });

  test("hides every plus from a guest and keeps the chevrons", () => {
    isMember = false;
    const html = markup();
    expect(count(html, "Add a page under")).toBe(0);
    expect(count(html, `aria-label="Show the pages of `)).toBe(16);
  });

  test("hides the plus of a section the reader may only view", () => {
    tree = fullTree().map((page) =>
      page.slug === "laws" && page.parentId === null ? { ...page, level: "VIEW" as const } : page
    );
    const html = markup();
    expect(html).not.toContain(addLabel("Law"));
    expect(html).toContain(addLabel("Policy"));
    expect(html).toContain(expandLabel("Law"));
  });
});
