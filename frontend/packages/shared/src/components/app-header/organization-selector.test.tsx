import { afterAll, afterEach, beforeEach, describe, expect, mock, test } from "bun:test";
import { QueryClient, QueryObserver } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

// A static render cannot open the menu or click a row, so the Select is
// replaced by a passthrough that keeps the `onValueChange` the selector
// registers; a test calls it the way Radix does when a row is chosen. The
// session, the current user and the save are recorders. The query
// client is real: one observer stands for an organization-scoped query of the
// page, and its fetches record the organization the API client would send.
// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualSelect = { ...(await import("@oba/ui/components/select")) };
const actualIntl = { ...(await import("next-intl")) };
const actualAuthReact = { ...(await import("next-auth/react")) };
const actualReactQuery = { ...(await import("@tanstack/react-query")) };
const actualSonner = { ...(await import("sonner")) };
const actualNavigation = { ...(await import("../../libs/i18n/navigation")) };
const actualGetMe = { ...(await import("../../features/users/hooks/use-get-me")) };
const actualUpdateMe = { ...(await import("../../features/users/hooks/use-update-me")) };

type MeOrganization = {
  id: string;
  name: string;
  role: "OWNER" | "ADMIN" | "MEMBER" | "GUEST";
  organizationStatus: "ACTIVE" | "DISABLED";
};

const DEFAULT_ORGANIZATIONS: MeOrganization[] = [
  { id: "org-1", name: "Korur", role: "MEMBER", organizationStatus: "ACTIVE" },
  { id: "org-2", name: "Sensey", role: "MEMBER", organizationStatus: "ACTIVE" },
];

let onValueChange: (value: string) => Promise<void>;
// The value the selector hands the Select, so a row can be rendered checked.
let selectValue: string | undefined;
let meOrganizations: MeOrganization[];
let defaultOrganizationId: string | null;
let updateResult: () => Promise<unknown>;
const updatedMe: unknown[] = [];
const sessionUpdates: unknown[] = [];
const errorToasts: string[] = [];
// The organization the API client sends as X-Organization-ID: the session
// default, which the switch changes through `update`.
let requestOrganization: string | null;
// The organization of every fetch of the page query.
const fetches: (string | null)[] = [];
let queryClient: QueryClient;
let pageQuery: QueryObserver<string>;
let unsubscribePageQuery: () => void;
const PAGE_QUERY_KEY = ["kazan", "boards"];

const passthrough = ({ children }: { children?: ReactNode }) => <div>{children}</div>;
mock.module("@oba/ui/components/select", () => ({
  ...actualSelect,
  Select: (props: {
    children?: ReactNode;
    value?: string;
    onValueChange: (value: string) => Promise<void>;
  }) => {
    onValueChange = props.onValueChange;
    selectValue = props.value;
    return <div>{props.children}</div>;
  },
  SelectContent: passthrough,
  SelectGroup: passthrough,
  // A row carries its value, its classes and the state Radix gives it.
  SelectItem: (props: { children?: ReactNode; value: string; className?: string }) => (
    <div
      data-value={props.value}
      data-state={props.value === selectValue ? "checked" : "unchecked"}
      className={props.className}
    >
      {props.children}
    </div>
  ),
  SelectLabel: passthrough,
  SelectSeparator: () => null,
  SelectTrigger: (props: { children?: ReactNode; title?: string; className?: string }) => (
    <div data-slot="trigger" title={props.title} className={props.className}>
      {props.children}
    </div>
  ),
  // Like Radix, the placeholder takes the children's place while the value is empty.
  SelectValue: (props: { children?: ReactNode; placeholder?: ReactNode }) => (
    <div data-slot="value">{selectValue === "" ? props.placeholder : props.children}</div>
  ),
}));
mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("next-auth/react", () => ({
  ...actualAuthReact,
  useSession: () => ({
    data: { user: { id: "u1", defaultOrganizationId } },
    status: "authenticated",
    update: async (data: { user: { defaultOrganizationId: string | null } }) => {
      sessionUpdates.push(data);
      requestOrganization = data.user.defaultOrganizationId;
      return data;
    },
  }),
}));
mock.module("@tanstack/react-query", () => ({
  ...actualReactQuery,
  useQueryClient: () => queryClient,
}));
mock.module("sonner", () => ({
  ...actualSonner,
  toast: {
    ...actualSonner.toast,
    error: (message: string) => {
      errorToasts.push(message);
    },
  },
}));
mock.module("../../libs/i18n/navigation", () => ({
  ...actualNavigation,
  usePathname: () => "/dashboard",
  useRouter: () => ({ push: () => undefined }),
}));
mock.module("../../features/users/hooks/use-get-me", () => ({
  ...actualGetMe,
  useGetMe: () => ({
    data: {
      success: true,
      data: { organizations: meOrganizations },
    },
    isFetching: false,
  }),
}));
mock.module("../../features/users/hooks/use-update-me", () => ({
  ...actualUpdateMe,
  useUpdateMe: () => ({
    mutateAsync: async (data: unknown) => {
      updatedMe.push(data);
      return updateResult();
    },
  }),
}));

afterAll(() => {
  mock.module("@oba/ui/components/select", () => actualSelect);
  mock.module("next-intl", () => actualIntl);
  mock.module("next-auth/react", () => actualAuthReact);
  mock.module("@tanstack/react-query", () => actualReactQuery);
  mock.module("sonner", () => actualSonner);
  mock.module("../../libs/i18n/navigation", () => actualNavigation);
  mock.module("../../features/users/hooks/use-get-me", () => actualGetMe);
  mock.module("../../features/users/hooks/use-update-me", () => actualUpdateMe);
});

const { OrganizationSelector } = await import("./organization-selector");
type OrganizationSelectorProps = import("./organization-selector").OrganizationSelectorProps;
const { invalidateAllOrganizationQueries } = await import("../../hooks/use-invalidate-org-queries");

function renderSelector(props: OrganizationSelectorProps = {}): string {
  return renderToStaticMarkup(<OrganizationSelector {...props} />);
}

/** The values of the menu's rows, in order. */
function rowValues(html: string): string[] {
  return [...html.matchAll(/data-value="([^"]+)"/g)].map((match) => match[1] ?? "");
}

/** Runs `action` with `console.error` silenced, for a path that logs a failure on purpose. */
async function withoutConsoleError(action: () => Promise<void>): Promise<void> {
  const originalConsoleError = console.error;
  console.error = () => undefined;
  try {
    await action();
  } finally {
    console.error = originalConsoleError;
  }
}

/** The page query, mounted and enabled while the page shows organization `org-1`. */
async function mountPageQuery(): Promise<void> {
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  pageQuery = new QueryObserver<string>(queryClient, {
    queryKey: PAGE_QUERY_KEY,
    queryFn: async () => {
      fetches.push(requestOrganization);
      return `boards of ${requestOrganization}`;
    },
  });
  unsubscribePageQuery = pageQuery.subscribe(() => undefined);
  await queryClient.getQueryCache().find({ queryKey: PAGE_QUERY_KEY })?.promise;
  fetches.length = 0;
}

/**
 * What follows the render of the new session: the app's access gate leaves
 * the page query enabled or not, then `OrganizationSyncProvider` sees the new
 * default organization and refetches through `invalidateAllOrganizationQueries`.
 */
async function renderNewSession(pageQueryEnabled: boolean): Promise<void> {
  pageQuery.setOptions({ ...pageQuery.options, enabled: pageQueryEnabled });
  await invalidateAllOrganizationQueries(queryClient);
}

function pageQueryState() {
  return queryClient.getQueryState(PAGE_QUERY_KEY);
}

beforeEach(async () => {
  defaultOrganizationId = "org-1";
  requestOrganization = "org-1";
  meOrganizations = DEFAULT_ORGANIZATIONS;
  selectValue = undefined;
  updateResult = async () => ({ success: true });
  for (const list of [updatedMe, sessionUpdates, errorToasts, fetches]) {
    list.length = 0;
  }
  await mountPageQuery();
});

afterEach(() => {
  unsubscribePageQuery();
  queryClient.clear();
});

describe("OrganizationSelector", () => {
  test("an organization is saved as the default and stored in the session", async () => {
    renderSelector();

    await onValueChange("org-2");

    expect(updatedMe).toEqual([{ defaultOrganizationId: "org-2" }]);
    expect(sessionUpdates).toEqual([{ user: { id: "u1", defaultOrganizationId: "org-2" } }]);
    expect(pageQueryState()?.isInvalidated).toBe(true);
    expect(errorToasts).toHaveLength(0);
  });

  test("choosing the current organization sends nothing", async () => {
    renderSelector();

    await onValueChange("org-1");

    expect(updatedMe).toHaveLength(0);
    expect(sessionUpdates).toHaveLength(0);
  });

  test("a switch between two organizations refetches the page for the new one", async () => {
    renderSelector();

    await onValueChange("org-2");
    expect(fetches).toEqual([]);

    // Both organizations grant the app, so the page query stays enabled.
    await renderNewSession(true);

    expect(fetches).toEqual(["org-2"]);
    expect(pageQueryState()?.data).toBe("boards of org-2");
    expect(pageQueryState()?.isInvalidated).toBe(false);
  });

  test("a refused save shows the switch error and keeps the session", async () => {
    updateResult = async () => {
      throw new Error("PUT /users/me was rejected");
    };
    renderSelector();

    await withoutConsoleError(() => onValueChange("org-2"));

    expect(updatedMe).toHaveLength(1);
    expect(errorToasts).toEqual(["organizationSwitchFailed"]);
    expect(sessionUpdates).toHaveLength(0);
    expect(pageQueryState()?.isInvalidated).toBe(false);
    expect(fetches).toHaveLength(0);
  });

  test("the menu lists only organizations, with no Personal row", () => {
    const html = renderSelector();

    expect(rowValues(html)).toEqual(["org-1", "org-2"]);
    expect(html).not.toContain("personalWorkspace");
  });

  test("with no organization chosen no row is marked and the trigger asks for a choice", () => {
    defaultOrganizationId = null;

    const html = renderSelector();

    expect(selectValue).toBe("");
    expect(html).not.toContain('data-state="checked"');
    expect(html).toContain('title="selectOrganization"');
    expect(html).toMatch(/<div data-slot="value">.*selectOrganization.*<\/div>/);
  });

  test("managedOnly lists only the ACTIVE organizations the user owns or administers", () => {
    meOrganizations = [
      { id: "org-1", name: "Korur", role: "OWNER", organizationStatus: "ACTIVE" },
      { id: "org-2", name: "Sensey", role: "MEMBER", organizationStatus: "ACTIVE" },
      { id: "org-3", name: "Tayga", role: "ADMIN", organizationStatus: "DISABLED" },
      { id: "org-4", name: "Alar", role: "ADMIN", organizationStatus: "ACTIVE" },
    ];

    const html = renderSelector({ managedOnly: true });

    expect(rowValues(html)).toEqual(["org-1", "org-4"]);
  });

  describe("controlled", () => {
    const hostOrganizations = [
      { id: "kaya-1", name: "Kaya One" },
      { id: "kaya-2", name: "Kaya Two" },
    ];

    test("shows the host's organization and lists the host's organizations", () => {
      const html = renderSelector({
        controlled: {
          organizationId: "kaya-2",
          organizations: hostOrganizations,
          onSwitch: () => {},
        },
      });

      expect(rowValues(html)).toEqual(["kaya-1", "kaya-2"]);
      expect(html).toContain('title="Kaya Two"');
      expect(html).toContain('data-value="kaya-2" data-state="checked"');
    });

    test("a switch saves the default organization, then hands it to the host", async () => {
      const calls: string[] = [];
      updateResult = async () => {
        calls.push("saved");
        return { success: true };
      };
      renderSelector({
        controlled: {
          organizationId: "kaya-1",
          organizations: hostOrganizations,
          onSwitch: (organizationId) => calls.push(`switched ${organizationId}`),
        },
      });

      await onValueChange("kaya-2");

      expect(updatedMe).toEqual([{ defaultOrganizationId: "kaya-2" }]);
      expect(calls).toEqual(["saved", "switched kaya-2"]);
      expect(sessionUpdates).toHaveLength(0);
      expect(pageQueryState()?.isInvalidated).toBe(false);
    });

    test("a refused save shows the switch error and leaves the host where it was", async () => {
      updateResult = async () => {
        throw new Error("PUT /users/me was rejected");
      };
      const switched: string[] = [];
      renderSelector({
        controlled: {
          organizationId: "kaya-1",
          organizations: hostOrganizations,
          onSwitch: (organizationId) => switched.push(organizationId),
        },
      });

      await withoutConsoleError(() => onValueChange("kaya-2"));

      expect(updatedMe).toHaveLength(1);
      expect(switched).toEqual([]);
      expect(errorToasts).toEqual(["organizationSwitchFailed"]);
      expect(sessionUpdates).toHaveLength(0);
    });

    test("choosing the host's current organization sends nothing", async () => {
      const switched: string[] = [];
      renderSelector({
        controlled: {
          organizationId: "kaya-1",
          organizations: hostOrganizations,
          onSwitch: (organizationId) => switched.push(organizationId),
        },
      });

      await onValueChange("kaya-1");

      expect(updatedMe).toHaveLength(0);
      expect(switched).toEqual([]);
    });
  });

  test("a long name renders whole in its row and in the trigger's title", () => {
    const longName = "Korur Siber Güvenlik Limited Şirketi";
    meOrganizations = [
      { id: "org-1", name: longName, role: "OWNER", organizationStatus: "ACTIVE" },
      ...DEFAULT_ORGANIZATIONS.slice(1),
    ];

    const html = renderSelector();

    expect(html).toContain(`title="${longName}"`);
    const row = html.match(/<div data-value="org-1"[^>]*>([^<]*)<\/div>/);
    expect(row?.[1]).toBe(longName);
    expect(row?.[0]).toContain("whitespace-normal");
    expect(row?.[0]).toContain("break-words");
  });

  test("the trigger shrinks and cuts its name so the top bar's other actions stay in view", () => {
    const html = renderSelector();

    const trigger = html.match(/<div data-slot="trigger"[^>]*class="([^"]*)"/);
    const classes = trigger?.[1].split(" ") ?? [];
    expect(classes).toContain("min-w-0");
    expect(classes).toContain("*:data-[slot=select-value]:min-w-0");
    expect(html).toMatch(/<span class="[^"]*\btruncate\b[^"]*">Korur<\/span>/);
  });

  test("the current row is checked and carries the tint, rail and weight", () => {
    const html = renderSelector();

    const current = html.match(/<div data-value="org-1" data-state="checked" class="([^"]*)"/);
    expect(current).not.toBeNull();
    for (const mark of [
      "data-[state=checked]:bg-[rgb(26_100_184/0.10)]",
      "data-[state=checked]:font-semibold",
      "data-[state=checked]:before:w-0.5",
    ]) {
      expect(current?.[1]).toContain(mark);
    }
    expect(html).toContain('data-value="org-2" data-state="unchecked"');
  });
});
