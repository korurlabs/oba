import { afterAll, afterEach, beforeEach, describe, expect, mock, test } from "bun:test";
import { QueryClient, QueryObserver } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

// A static render cannot open the menu or click a row, so the Select is
// replaced by a passthrough that keeps the `onValueChange` the selector
// registers; a test calls it the way Radix does when a row is chosen. The
// session, the current user and both mutations are recorders. The query
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
const actualClear = {
  ...(await import("../../features/users/hooks/use-clear-default-organization")),
};

const PERSONAL_VALUE = "__personal__";

let onValueChange: (value: string) => Promise<void>;
let defaultOrganizationId: string | null;
let clearResult: () => Promise<unknown>;
const cleared: number[] = [];
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
  Select: (props: { children?: ReactNode; onValueChange: (value: string) => Promise<void> }) => {
    onValueChange = props.onValueChange;
    return <div>{props.children}</div>;
  },
  SelectContent: passthrough,
  SelectGroup: passthrough,
  SelectItem: passthrough,
  SelectLabel: passthrough,
  SelectSeparator: () => null,
  SelectTrigger: passthrough,
  SelectValue: passthrough,
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
      data: {
        organizations: [
          { id: "org-1", name: "Korur", role: "MEMBER", organizationStatus: "ACTIVE" },
          { id: "org-2", name: "Sensey", role: "MEMBER", organizationStatus: "ACTIVE" },
        ],
      },
    },
    isFetching: false,
  }),
}));
mock.module("../../features/users/hooks/use-update-me", () => ({
  ...actualUpdateMe,
  useUpdateMe: () => ({
    mutateAsync: async (data: unknown) => {
      updatedMe.push(data);
      return { success: true };
    },
  }),
}));
mock.module("../../features/users/hooks/use-clear-default-organization", () => ({
  ...actualClear,
  useClearDefaultOrganization: () => ({
    mutateAsync: async () => {
      cleared.push(1);
      return clearResult();
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
  mock.module("../../features/users/hooks/use-clear-default-organization", () => actualClear);
});

const { OrganizationSelector } = await import("./organization-selector");
const { invalidateAllOrganizationQueries } = await import("../../hooks/use-invalidate-org-queries");

function renderSelector(): string {
  return renderToStaticMarkup(<OrganizationSelector />);
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
  clearResult = async () => ({ success: true, data: { defaultOrganizationId: null } });
  for (const list of [cleared, updatedMe, sessionUpdates, errorToasts, fetches]) {
    list.length = 0;
  }
  await mountPageQuery();
});

afterEach(() => {
  unsubscribePageQuery();
  queryClient.clear();
});

describe("OrganizationSelector", () => {
  test("Personal clears the default organization and stores null in the session", async () => {
    renderSelector();

    await onValueChange(PERSONAL_VALUE);

    expect(cleared).toHaveLength(1);
    expect(updatedMe).toHaveLength(0);
    expect(sessionUpdates).toEqual([{ user: { id: "u1", defaultOrganizationId: null } }]);
    expect(pageQueryState()?.isInvalidated).toBe(true);
    expect(errorToasts).toHaveLength(0);
  });

  test("an organization is saved as the default and stored in the session", async () => {
    renderSelector();

    await onValueChange("org-2");

    expect(updatedMe).toEqual([{ defaultOrganizationId: "org-2" }]);
    expect(cleared).toHaveLength(0);
    expect(sessionUpdates).toEqual([{ user: { id: "u1", defaultOrganizationId: "org-2" } }]);
    expect(pageQueryState()?.isInvalidated).toBe(true);
  });

  test("Personal sends nothing when no default organization is set", async () => {
    defaultOrganizationId = null;
    const html = renderSelector();

    await onValueChange(PERSONAL_VALUE);

    expect(html).toContain("personalWorkspace");
    expect(cleared).toHaveLength(0);
    expect(updatedMe).toHaveLength(0);
    expect(sessionUpdates).toHaveLength(0);
  });

  test("a switch to Personal sends no organization-scoped request", async () => {
    renderSelector();

    await onValueChange(PERSONAL_VALUE);
    // The old page is still mounted when the handler ends.
    expect(fetches).toEqual([]);

    // Personal has no organization, so the gate disables the page query.
    await renderNewSession(false);

    expect(fetches).toEqual([]);
    expect(pageQueryState()?.data).toBe("boards of org-1");
    expect(pageQueryState()?.isInvalidated).toBe(true);
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

  test("a refused clear shows the switch error and keeps the session", async () => {
    clearResult = async () => {
      throw new Error("DELETE /users/me/default-organization was rejected");
    };
    const originalConsoleError = console.error;
    console.error = () => undefined;
    try {
      renderSelector();
      await onValueChange(PERSONAL_VALUE);
    } finally {
      console.error = originalConsoleError;
    }

    expect(cleared).toHaveLength(1);
    expect(errorToasts).toEqual(["organizationSwitchFailed"]);
    expect(sessionUpdates).toHaveLength(0);
    expect(pageQueryState()?.isInvalidated).toBe(false);
    expect(fetches).toHaveLength(0);
  });
});
