import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

// The popover mounts its content only while open, so it is replaced by a
// passthrough and the test sees the list of apps the switcher builds.
// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualPopover = { ...(await import("@oba/ui/components/popover")) };
const actualIntl = { ...(await import("next-intl")) };
const actualGetMe = { ...(await import("../../features/users/hooks/use-get-me")) };

mock.module("@oba/ui/components/popover", () => ({
  ...actualPopover,
  Popover: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  PopoverTrigger: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  PopoverContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
// What the current-user query answers; each test sets it. By default the user
// manages an organization that grants every app, so every app is listed.
type Membership = {
  id: string;
  name: string;
  role: string;
  organizationStatus: string;
  apps?: string[];
};
const MANAGER: Membership[] = [
  {
    id: "org-1",
    name: "Korur",
    role: "OWNER",
    organizationStatus: "ACTIVE",
    apps: ["KAZAN", "KAYA", "KALKAN", "OTAK"],
  },
];
let meResult: { data?: { success: boolean; data?: { organizations: Membership[] } } };

mock.module("../../features/users/hooks/use-get-me", () => ({
  ...actualGetMe,
  useGetMe: () => meResult,
}));

afterAll(() => {
  mock.module("@oba/ui/components/popover", () => actualPopover);
  mock.module("next-intl", () => actualIntl);
  mock.module("../../features/users/hooks/use-get-me", () => actualGetMe);
});

const { AppSwitcher } = await import("./app-switcher");
const { obaApps } = await import("../../constants/apps");
const { RuntimeConfigProvider } = await import("../../providers/RuntimeConfigProvider");

// Origins of a deployment other than Korur's, so a link proves it came from the
// run-time config and not from a default.
const CONFIG = {
  apiBaseUrl: "https://api.example.test",
  baseUrl: "https://kazan.example.test",
  appUrls: {
    kazan: "https://kazan.example.test",
    kaya: "https://kaya.example.test",
    kalkan: "https://kalkan.example.test",
    otak: "https://otak.example.test",
    ulak: "https://ulak.example.test",
    account: "https://account.example.test",
  },
};
const OBA_APPS = obaApps(CONFIG.appUrls);

function renderSwitcher(switcher: React.ReactElement): string {
  return renderToStaticMarkup(
    <RuntimeConfigProvider config={CONFIG}>{switcher}</RuntimeConfigProvider>
  );
}

beforeEach(() => {
  meResult = { data: { success: true, data: { organizations: MANAGER } } };
});

describe("AppSwitcher", () => {
  test("links every app except the current one", () => {
    const html = renderSwitcher(<AppSwitcher currentAppId="ulak" />);

    for (const app of OBA_APPS) {
      const link = `href="${app.url}"`;
      if (app.id === "ulak") expect(html).not.toContain(link);
      else expect(html).toContain(link);
    }
  });

  test("marks the current app as the current page and names every app", () => {
    const html = renderSwitcher(<AppSwitcher currentAppId="kaya" />);

    expect(html.match(/aria-current="page"/g)).toHaveLength(1);
    for (const app of OBA_APPS) {
      expect(html).toContain(`${app.id}.name`);
      expect(html).toContain(`${app.id}.description`);
    }
  });

  test("lists Ulak and Account first, then the product apps in catalogue order", () => {
    const html = renderSwitcher(<AppSwitcher currentAppId="kazan" />);
    const order = ["ulak", "account", "kazan", "kaya", "kalkan", "otak"];
    const positions = order.map((id) => html.indexOf(`${id}.name`));

    expect(positions.every((position) => position >= 0)).toBe(true);
    expect(positions).toEqual([...positions].sort((a, b) => a - b));
  });

  test("marks the current tile like the current organization and links the others in the grid look", () => {
    const html = renderSwitcher(<AppSwitcher currentAppId="kaya" />);
    const current = html.match(
      /<div aria-current="page" data-state="checked" class="([^"]*)"/
    )?.[1];

    expect(current).toContain("data-[state=checked]:bg-[rgb(26_100_184/0.10)]");
    expect(current).toContain("data-[state=checked]:before:w-0.5");
    const links = [...html.matchAll(/<a href="[^"]*" class="([^"]*)"/g)].map((m) => m[1] ?? "");
    expect(links.length).toBeGreaterThan(0);
    for (const link of links) {
      expect(link).toContain("rounded-none");
      expect(link).toContain("focus:bg-[rgb(47_143_233/0.08)]");
    }
  });

  test("heads the grid with the ops group heading that labels it", () => {
    const html = renderSwitcher(<AppSwitcher currentAppId="kaya" />);
    const id = html.match(/<p id="([^"]+)" class="[^"]*font-mono[^"]*">switchApp<\/p>/)?.[1];

    expect(id).toBeDefined();
    expect(html).toContain(`role="group" aria-labelledby="${id}"`);
  });

  test("parts the tiles by hairlines inside the grid, never on its outer edge", () => {
    const html = renderSwitcher(<AppSwitcher currentAppId="kaya" />);
    const tiles = [
      ...html.matchAll(
        /<(?:a href="[^"]*"|div aria-current="page" data-state="checked") class="([^"]*)"/g
      ),
    ].map((m) => (m[1] ?? "").split(" "));

    expect(tiles).toHaveLength(6);
    // Two columns of three rows: the left column draws the vertical rule, the
    // last row draws no bottom rule.
    tiles.forEach((classes, index) => {
      expect(classes.includes("border-e")).toBe(index % 2 === 0);
      expect(classes.includes("border-b-0")).toBe(index >= 4);
    });
  });

  test("offers the switch label on the trigger", () => {
    const html = renderSwitcher(<AppSwitcher currentAppId="kazan" />);

    expect(html).toContain('aria-label="switchApp"');
  });
});

describe("AppSwitcher ulak entry", () => {
  const ulakLink = `href="${OBA_APPS.find((app) => app.id === "ulak")?.url}"`;

  test("lists ulak for a user who manages an organization", () => {
    expect(renderSwitcher(<AppSwitcher currentAppId="kazan" />)).toContain(ulakLink);
  });

  test("lists ulak as the current app even for a member-only user", () => {
    meResult = {
      data: {
        success: true,
        data: {
          organizations: [
            {
              id: "o",
              name: "Joined",
              role: "MEMBER",
              organizationStatus: "ACTIVE",
              apps: ["KAZAN", "KAYA"],
            },
          ],
        },
      },
    };

    const html = renderSwitcher(<AppSwitcher currentAppId="ulak" />);

    expect(html).toContain("ulak.name");
    expect(html.match(/aria-current="page"/g)).toHaveLength(1);
  });

  test("hides ulak from a member-only user", () => {
    meResult = {
      data: {
        success: true,
        data: {
          organizations: [
            {
              id: "o",
              name: "Joined",
              role: "MEMBER",
              organizationStatus: "ACTIVE",
              apps: ["KAZAN", "KAYA"],
            },
          ],
        },
      },
    };

    const html = renderSwitcher(<AppSwitcher currentAppId="kazan" />);

    expect(html).not.toContain("ulak.name");
    expect(html).not.toContain(ulakLink);
    expect(html).toContain("kaya.name");
  });

  test("hides ulak from a user whose only owned organization is DISABLED", () => {
    meResult = {
      data: {
        success: true,
        data: {
          organizations: [
            { id: "o", name: "Closed", role: "OWNER", organizationStatus: "DISABLED" },
          ],
        },
      },
    };

    expect(renderSwitcher(<AppSwitcher currentAppId="kazan" />)).not.toContain("ulak.name");
  });

  test("hides ulak while the current user is unknown", () => {
    meResult = {};

    expect(renderSwitcher(<AppSwitcher currentAppId="kazan" />)).not.toContain("ulak.name");
  });
});

describe("AppSwitcher app access", () => {
  const kazanLink = `href="${OBA_APPS.find((app) => app.id === "kazan")?.url}"`;
  const kayaLink = `href="${OBA_APPS.find((app) => app.id === "kaya")?.url}"`;
  const kalkanLink = `href="${OBA_APPS.find((app) => app.id === "kalkan")?.url}"`;
  const otakLink = `href="${OBA_APPS.find((app) => app.id === "otak")?.url}"`;
  const withOrganizations = (organizations: Membership[]): typeof meResult => ({
    data: { success: true, data: { organizations } },
  });
  const member = (apps?: string[], organizationStatus = "ACTIVE"): Membership => ({
    id: "o",
    name: "Joined",
    role: "MEMBER",
    organizationStatus,
    apps,
  });

  test("hides kazan from a user whose organizations do not grant KAZAN", () => {
    meResult = withOrganizations([member(["KAYA"])]);

    const html = renderSwitcher(<AppSwitcher currentAppId="account" />);

    expect(html).not.toContain(kazanLink);
    expect(html).toContain(kayaLink);
  });

  test("hides kazan and kaya when the API sends no apps", () => {
    meResult = withOrganizations([member()]);

    const html = renderSwitcher(<AppSwitcher currentAppId="account" />);

    expect(html).not.toContain(kazanLink);
    expect(html).not.toContain(kayaLink);
  });

  test("hides kalkan and otak from a user whose organizations do not grant them", () => {
    meResult = withOrganizations([member(["KAZAN", "KAYA"])]);

    const html = renderSwitcher(<AppSwitcher currentAppId="account" />);

    expect(html).not.toContain(kalkanLink);
    expect(html).not.toContain(otakLink);
    expect(html).toContain(kazanLink);
  });

  test("lists kalkan and otak for a user whose organization grants them", () => {
    meResult = withOrganizations([member(["KALKAN", "OTAK"])]);

    const html = renderSwitcher(<AppSwitcher currentAppId="account" />);

    expect(html).toContain(kalkanLink);
    expect(html).toContain(otakLink);
    expect(html).not.toContain(kazanLink);
  });

  test("hides kalkan and otak when the API sends no apps", () => {
    meResult = withOrganizations([member()]);

    const html = renderSwitcher(<AppSwitcher currentAppId="account" />);

    expect(html).not.toContain(kalkanLink);
    expect(html).not.toContain(otakLink);
  });

  test("hides an app that only a DISABLED organization grants", () => {
    meResult = withOrganizations([member(["KAZAN"], "DISABLED")]);

    expect(renderSwitcher(<AppSwitcher currentAppId="account" />)).not.toContain(kazanLink);
  });

  test("keeps the granted apps while the current user loads, and still hides ulak", () => {
    meResult = {};

    const html = renderSwitcher(<AppSwitcher currentAppId="account" />);

    expect(html).toContain(kazanLink);
    expect(html).toContain(kayaLink);
    expect(html).toContain(kalkanLink);
    expect(html).toContain(otakLink);
    expect(html).not.toContain("ulak.name");
  });

  test("lists the current app even when no organization grants it", () => {
    meResult = withOrganizations([member()]);

    const html = renderSwitcher(<AppSwitcher currentAppId="kazan" />);

    expect(html).toContain("kazan.name");
    expect(html.match(/aria-current="page"/g)).toHaveLength(1);
  });

  test("never lists ulak for a user who only holds app grants", () => {
    meResult = withOrganizations([member(["KAZAN", "KAYA"])]);

    expect(renderSwitcher(<AppSwitcher currentAppId="kazan" />)).not.toContain("ulak.name");
  });
});
