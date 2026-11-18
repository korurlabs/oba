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
    apps: ["KAIZEN", "KAYA", "KALKAN", "OTAK"],
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
  baseUrl: "https://kaizen.example.test",
  appUrls: {
    kaizen: "https://kaizen.example.test",
    kaya: "https://kaya.example.test",
    kalkan: "https://kalkan.example.test",
    otak: "https://otak.example.test",
    admin: "https://admin.example.test",
    account: "https://account.example.test",
  },
};
const OBA_APPS = obaApps(CONFIG.appUrls);

function renderSwitcher(switcher: React.ReactElement): string {
  return renderToStaticMarkup(<RuntimeConfigProvider config={CONFIG}>{switcher}</RuntimeConfigProvider>);
}

beforeEach(() => {
  meResult = { data: { success: true, data: { organizations: MANAGER } } };
});

describe("AppSwitcher", () => {
  test("links every app except the current one", () => {
    const html = renderSwitcher(<AppSwitcher currentAppId="admin" />);

    for (const app of OBA_APPS) {
      const link = `href="${app.url}"`;
      if (app.id === "admin") expect(html).not.toContain(link);
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

  test("lists Admin and Account first, then the product apps in catalogue order", () => {
    const html = renderSwitcher(<AppSwitcher currentAppId="kaizen" />);
    const order = ["admin", "account", "kaizen", "kaya", "kalkan", "otak"];
    const positions = order.map((id) => html.indexOf(`${id}.name`));

    expect(positions.every((position) => position >= 0)).toBe(true);
    expect(positions).toEqual([...positions].sort((a, b) => a - b));
  });

  test("offers the switch label on the trigger", () => {
    const html = renderSwitcher(<AppSwitcher currentAppId="kaizen" />);

    expect(html).toContain('aria-label="switchApp"');
  });
});

describe("AppSwitcher admin entry", () => {
  const adminLink = `href="${OBA_APPS.find((app) => app.id === "admin")?.url}"`;

  test("lists admin for a user who manages an organization", () => {
    expect(renderSwitcher(<AppSwitcher currentAppId="kaizen" />)).toContain(adminLink);
  });

  test("lists admin as the current app even for a member-only user", () => {
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
              apps: ["KAIZEN", "KAYA"],
            },
          ],
        },
      },
    };

    const html = renderSwitcher(<AppSwitcher currentAppId="admin" />);

    expect(html).toContain("admin.name");
    expect(html.match(/aria-current="page"/g)).toHaveLength(1);
  });

  test("hides admin from a member-only user", () => {
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
              apps: ["KAIZEN", "KAYA"],
            },
          ],
        },
      },
    };

    const html = renderSwitcher(<AppSwitcher currentAppId="kaizen" />);

    expect(html).not.toContain("admin.name");
    expect(html).not.toContain(adminLink);
    expect(html).toContain("kaya.name");
  });

  test("hides admin from a user whose only owned organization is DISABLED", () => {
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

    expect(renderSwitcher(<AppSwitcher currentAppId="kaizen" />)).not.toContain("admin.name");
  });

  test("hides admin while the current user is unknown", () => {
    meResult = {};

    expect(renderSwitcher(<AppSwitcher currentAppId="kaizen" />)).not.toContain("admin.name");
  });
});

describe("AppSwitcher app access", () => {
  const kaizenLink = `href="${OBA_APPS.find((app) => app.id === "kaizen")?.url}"`;
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

  test("hides kaizen from a user whose organizations do not grant KAIZEN", () => {
    meResult = withOrganizations([member(["KAYA"])]);

    const html = renderSwitcher(<AppSwitcher currentAppId="account" />);

    expect(html).not.toContain(kaizenLink);
    expect(html).toContain(kayaLink);
  });

  test("hides kaizen and kaya when the API sends no apps", () => {
    meResult = withOrganizations([member()]);

    const html = renderSwitcher(<AppSwitcher currentAppId="account" />);

    expect(html).not.toContain(kaizenLink);
    expect(html).not.toContain(kayaLink);
  });

  test("hides kalkan and otak from a user whose organizations do not grant them", () => {
    meResult = withOrganizations([member(["KAIZEN", "KAYA"])]);

    const html = renderSwitcher(<AppSwitcher currentAppId="account" />);

    expect(html).not.toContain(kalkanLink);
    expect(html).not.toContain(otakLink);
    expect(html).toContain(kaizenLink);
  });

  test("lists kalkan and otak for a user whose organization grants them", () => {
    meResult = withOrganizations([member(["KALKAN", "OTAK"])]);

    const html = renderSwitcher(<AppSwitcher currentAppId="account" />);

    expect(html).toContain(kalkanLink);
    expect(html).toContain(otakLink);
    expect(html).not.toContain(kaizenLink);
  });

  test("hides kalkan and otak when the API sends no apps", () => {
    meResult = withOrganizations([member()]);

    const html = renderSwitcher(<AppSwitcher currentAppId="account" />);

    expect(html).not.toContain(kalkanLink);
    expect(html).not.toContain(otakLink);
  });

  test("hides an app that only a DISABLED organization grants", () => {
    meResult = withOrganizations([member(["KAIZEN"], "DISABLED")]);

    expect(renderSwitcher(<AppSwitcher currentAppId="account" />)).not.toContain(kaizenLink);
  });

  test("keeps the granted apps while the current user loads, and still hides admin", () => {
    meResult = {};

    const html = renderSwitcher(<AppSwitcher currentAppId="account" />);

    expect(html).toContain(kaizenLink);
    expect(html).toContain(kayaLink);
    expect(html).toContain(kalkanLink);
    expect(html).toContain(otakLink);
    expect(html).not.toContain("admin.name");
  });

  test("lists the current app even when no organization grants it", () => {
    meResult = withOrganizations([member()]);

    const html = renderSwitcher(<AppSwitcher currentAppId="kaizen" />);

    expect(html).toContain("kaizen.name");
    expect(html.match(/aria-current="page"/g)).toHaveLength(1);
  });

  test("never lists admin for a user who only holds app grants", () => {
    meResult = withOrganizations([member(["KAIZEN", "KAYA"])]);

    expect(renderSwitcher(<AppSwitcher currentAppId="kaizen" />)).not.toContain("admin.name");
  });
});
