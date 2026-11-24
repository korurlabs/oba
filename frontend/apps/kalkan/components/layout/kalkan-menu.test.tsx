import { afterAll, afterEach, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import { cleanup, renderLive, type Screen } from "@oba/shared/testing/live-render";
import { SidebarProvider } from "@oba/ui/sky/sidebar";
import en from "@/locales/en.json";
import type { PageCreateRequest, PageListItem } from "@/features/pages/schemas";
import { SECTION_SLUGS } from "@/lib/sections";

// The page tree, the user's role in the organization, the two create hooks and
// the router are stand-ins. The hooks record each request in `calls` and either
// report success or, as a refused request does, leave the field alone. The
// menu, the role checks, its tree nodes and field, the sidebar primitives and
// the messages are real.
type Role = "OWNER" | "ADMIN" | "MEMBER" | "GUEST";

let tree: PageListItem[] | undefined;
let role: Role;
let calls: string[];
let setUpRefused: boolean;
let createRefused: boolean;
let pushed: string[];

const actualPages = { ...(await import("@/features/pages/hooks/use-pages")) };
const actualSectionRoot = { ...(await import("@/features/pages/hooks/use-section-root")) };
const actualOrganizationId = { ...(await import("@/hooks/use-organization-id")) };
const actualMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };

mock.module("@/features/pages/hooks/use-pages", () => ({
  ...actualPages,
  usePageTree: () => ({ data: tree ? { data: tree } : undefined }),
  useCreatePage: () => ({
    isPending: false,
    mutate: (
      request: PageCreateRequest,
      options?: { onSuccess?: (response: { data?: { id: string } }) => void }
    ) => {
      calls.push(`create ${request.translations[0]?.title} under ${request.parentId}`);
      if (!createRefused) options?.onSuccess?.({ data: { id: "new-page" } });
    },
  }),
}));
mock.module("@/features/pages/hooks/use-section-root", () => ({
  ...actualSectionRoot,
  useSetUpSection: () => ({
    isPending: false,
    mutate: (
      slug: string,
      options?: { onSuccess?: (response: { data?: { id: string } }) => void }
    ) => {
      calls.push(`set up ${slug}`);
      if (!setUpRefused) options?.onSuccess?.({ data: { id: `root-${slug}` } });
    },
  }),
}));
mock.module("@/hooks/use-organization-id", () => ({
  ...actualOrganizationId,
  useOrganizationId: () => "org-1",
}));
mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualMe,
  useGetMe: () => ({ data: { data: { organizations: [{ id: "org-1", role }] } } }),
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ push: (href: string) => pushed.push(href), replace: () => undefined }),
}));

afterAll(() => {
  mock.module("@/features/pages/hooks/use-pages", () => actualPages);
  mock.module("@/features/pages/hooks/use-section-root", () => actualSectionRoot);
  mock.module("@/hooks/use-organization-id", () => actualOrganizationId);
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualMe);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
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

function menu(pathname: string) {
  return (
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

/** Clicks the "+" of a section, types the name and presses Enter. */
async function addUnder(screen: Screen, section: string, name: string): Promise<void> {
  await screen
    .byAttribute("aria-label", en.sidebar.addChild.replace("{name}", section))[0]
    ?.click();
  await screen.byAttribute("aria-label", en.sidebar.newChildName)[0]?.change(name);
  await screen.byTag("form")[0]?.submit();
}

const nameField = (screen: Screen) => screen.byAttribute("aria-label", en.sidebar.newChildName)[0];

beforeEach(() => {
  tree = fullTree();
  role = "MEMBER";
  calls = [];
  setUpRefused = false;
  createRefused = false;
  pushed = [];
});

afterEach(cleanup);

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

  test("leaves a section closed while its section page or root page is in view", () => {
    expect(markup("/policies")).not.toContain("Page first");
    expect(markup("/pages/root-policies")).not.toContain("Page first");
    expect(markup("/pages/root-policies/edit")).not.toContain("Page first");
  });

  test("opens the ancestors of a child page and keeps its own children closed", () => {
    const policies = root("policies");
    const access = child(policies, "access");
    const review = child(access, "review");
    tree = [policies, access, review];
    const html = markup(`/pages/${access.id}`);
    expect(html).toContain("Page access");
    expect(html).not.toContain("Page review");
    expect(html).toContain(expandLabel("Page access"));
  });

  test("folds the branch opened for an earlier page once another page is in view", async () => {
    const policies = root("policies");
    const access = child(policies, "access");
    tree = [policies, access];
    const screen = await renderLive(menu(`/pages/${access.id}`));
    expect(screen.text()).toContain("Page access");

    await screen.rerender(menu("/policies"));
    expect(screen.text()).not.toContain("Page access");
  });

  test("keeps a row the user opened open across navigation", async () => {
    const policies = root("policies");
    const access = child(policies, "access");
    tree = [policies, access];
    const screen = await renderLive(menu("/"));
    const policyLabel = en.sidebar.expand.replace("{name}", en.sidebar.sections.policies);
    await screen.byAttribute("aria-label", policyLabel)[0]?.click();
    expect(screen.text()).toContain("Page access");

    await screen.rerender(menu("/controls"));
    expect(screen.text()).toContain("Page access");
  });

  test("opens the branch of a page created from the tree once the refetched tree holds it", async () => {
    const policies = root("policies");
    const access = child(policies, "access");
    const created = child(access, "created");
    tree = [policies, access];
    const screen = await renderLive(menu("/"));
    expect(screen.text()).not.toContain("Page access");

    // The route to the new page commits before the tree refetch returns.
    await screen.rerender(menu(`/pages/${created.id}/edit`));
    expect(screen.text()).not.toContain("Page access");

    tree = [policies, access, created];
    await screen.rerender(menu(`/pages/${created.id}/edit`));
    expect(screen.text()).toContain("Page access");
    expect(screen.text()).toContain("Page created");
  });

  test("keeps a section folded on a page that is not in the tree", () => {
    const html = markup("/pages/unknown");
    expect(html).not.toContain("Page first");
  });

  test("shows a member a section whose root is missing as a plain link without actions", () => {
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
    role = "GUEST";
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

  describe("a section that is not set up", () => {
    const unsetSections = Object.values(en.sidebar.sections);

    for (const admin of ["ADMIN", "OWNER"] as const) {
      test(`shows its plus, and no chevron, to an organization ${admin}`, () => {
        role = admin;
        tree = [];
        const html = markup();
        for (const label of unsetSections) {
          expect(html).toContain(addLabel(label));
          expect(html).not.toContain(expandLabel(label));
        }
        expect(count(html, `aria-label="Add a page under `)).toBe(16);
      });
    }

    for (const reader of ["MEMBER", "GUEST"] as const) {
      test(`shows no plus to an organization ${reader}`, () => {
        role = reader;
        tree = [];
        const html = markup();
        expect(count(html, "Add a page under")).toBe(0);
        expect(html).toContain('href="/monitoring"');
      });
    }

    test("gives Overview, Recent and Trash no plus, also for an admin", () => {
      role = "OWNER";
      tree = [];
      const html = markup();
      for (const name of [en.sidebar.overview, en.sidebar.recent, en.sidebar.trash]) {
        expect(html).not.toContain(addLabel(name));
      }
      // The sixteen sections hold every plus there is.
      expect(count(html, "Add a page under")).toBe(16);
    });

    test("sets the section up first, then adds the page under its new root", async () => {
      role = "ADMIN";
      tree = [];
      const screen = await renderLive(menu("/"));
      await addUnder(screen, en.sidebar.sections.monitoring, "Alerting");
      expect(calls).toEqual(["set up monitoring", "create Alerting under root-monitoring"]);
      expect(pushed).toEqual(["/pages/new-page/edit"]);
      expect(nameField(screen)).toBeUndefined();

      // The refetched tree holds the new root and its page; the section row is open.
      const monitoring = root("monitoring");
      tree = [monitoring, child(monitoring, "alerting")];
      await screen.rerender(menu("/pages/new-page/edit"));
      expect(screen.text()).toContain("Page alerting");
    });

    test("keeps the field and the name and adds nothing when the set-up is refused", async () => {
      role = "ADMIN";
      tree = [];
      setUpRefused = true;
      const screen = await renderLive(menu("/"));
      await addUnder(screen, en.sidebar.sections.monitoring, "Alerting");
      expect(calls).toEqual(["set up monitoring"]);
      expect(pushed).toEqual([]);
      expect(nameField(screen)?.value).toBe("Alerting");
    });

    test("keeps the section set up and the field open when the page is refused after the set-up", async () => {
      role = "ADMIN";
      tree = [];
      createRefused = true;
      const screen = await renderLive(menu("/"));
      await addUnder(screen, en.sidebar.sections.monitoring, "Alerting");
      expect(calls).toEqual(["set up monitoring", "create Alerting under root-monitoring"]);
      expect(pushed).toEqual([]);
      expect(nameField(screen)?.value).toBe("Alerting");

      // The refetched tree holds the new root; the field and its name survive.
      tree = [root("monitoring")];
      await screen.rerender(menu("/"));
      expect(nameField(screen)?.value).toBe("Alerting");

      // The next Enter adds only the page.
      createRefused = false;
      calls = [];
      await screen.byTag("form")[0]?.submit();
      expect(calls).toEqual(["create Alerting under root-monitoring"]);
      expect(pushed).toEqual(["/pages/new-page/edit"]);
    });

    test("a section that is set up adds the page under its root without a set-up", async () => {
      role = "ADMIN";
      const screen = await renderLive(menu("/"));
      await addUnder(screen, en.sidebar.sections.policies, "Access");
      expect(calls).toEqual(["create Access under root-policies"]);
    });
  });
});
