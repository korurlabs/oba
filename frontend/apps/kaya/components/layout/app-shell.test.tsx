import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { QueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { ULAK_LINKS } from "@oba/shared/constants/apps";
import { renderToStaticMarkup } from "react-dom/server";

// The header's real parts are Kaya's shell and the shared organization
// selector. A static render cannot open a menu or click a row, so the Select
// keeps the `onValueChange` the selector registers and a test calls it as Radix
// does. The identity, the session, the current user and the save are fixed
// values or recorders; the profile menu and app switcher are stand-ins that
// show only what the shell hands them.
type Membership = { organizationId: string; name: string };

let memberships: Membership[];
let managesOrganizations: boolean;
let onValueChange: (value: string) => Promise<void>;
let selectValue: string | undefined;
let updateResult: () => Promise<unknown>;
const updatedMe: unknown[] = [];
const switched: string[] = [];
const order: string[] = [];
const errorToasts: string[] = [];

const actualSelect = { ...(await import("@oba/ui/components/select")) };
const actualIntl = { ...(await import("next-intl")) };
const actualAuthReact = { ...(await import("next-auth/react")) };
const actualReactQuery = { ...(await import("@tanstack/react-query")) };
const actualSonner = { ...(await import("sonner")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualGetMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };
const actualUpdateMe = { ...(await import("@oba/shared/features/users/hooks/use-update-me")) };
const actualSwitcher = { ...(await import("@oba/shared/components/app-header/app-switcher")) };
const actualProfile = { ...(await import("@oba/shared/components/app-header/profile-dropdown")) };
const actualNext = { ...(await import("next/navigation")) };
const actualMenu = { ...(await import("@oba/ui/components/dropdown-menu")) };
const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
const actualChange = { ...(await import("@/features/versions/components/change-control")) };
const actualOpen = { ...(await import("@/features/versions/components/open-change")) };

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
  SelectItem: (props: { children?: ReactNode; value: string }) => (
    <div
      data-value={props.value}
      data-state={props.value === selectValue ? "checked" : "unchecked"}
    >
      {props.children}
    </div>
  ),
  SelectLabel: passthrough,
  SelectSeparator: () => null,
  SelectTrigger: (props: { children?: ReactNode; title?: string }) => (
    <div data-slot="trigger" title={props.title}>
      {props.children}
    </div>
  ),
  SelectValue: passthrough,
}));
mock.module("next/navigation", () => ({ ...actualNext, usePathname: () => "/" }));
// An item draws only inside an open menu; the stand-in renders its child link.
mock.module("@oba/ui/components/dropdown-menu", () => ({
  ...actualMenu,
  DropdownMenuItem: ({ children }: { children?: ReactNode }) => <>{children}</>,
}));
mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("next-auth/react", () => ({
  ...actualAuthReact,
  useSession: () => ({ data: null, status: "authenticated", update: async () => null }),
}));
mock.module("@tanstack/react-query", () => ({
  ...actualReactQuery,
  useQueryClient: () => new QueryClient(),
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
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  usePathname: () => "/",
  useRouter: () => ({ push: () => undefined }),
}));
mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualGetMe,
  useGetMe: () => ({ data: undefined, isFetching: false }),
}));
mock.module("@oba/shared/features/users/hooks/use-update-me", () => ({
  ...actualUpdateMe,
  useUpdateMe: () => ({
    mutateAsync: async (data: unknown) => {
      updatedMe.push(data);
      order.push("saved");
      return updateResult();
    },
  }),
}));
mock.module("@oba/shared/components/app-header/app-switcher", () => ({
  ...actualSwitcher,
  AppSwitcher: () => null,
}));
// The real dropdown draws its content only once open; the stand-in draws the
// extra content in place, so the test sees whether the shell passes any.
mock.module("@oba/shared/components/app-header/profile-dropdown", () => ({
  ...actualProfile,
  ProfileDropdown: (props: { extraMenuContent?: ReactNode }) => (
    <div data-profile="">{props.extraMenuContent}</div>
  ),
}));
mock.module("@/lib/identity/identity-context", () => ({
  ...actualIdentity,
  useIdentity: () => ({
    me: { id: "u-1", memberships, managesOrganizations },
    organization: { organizationId: "org-1" },
    switchOrganization: (organizationId: string) => {
      switched.push(organizationId);
      order.push(`switched ${organizationId}`);
    },
  }),
}));
mock.module("@/features/versions/components/change-control", () => ({
  ...actualChange,
  ChangeControl: () => null,
}));
mock.module("@/features/versions/components/open-change", () => ({
  ...actualOpen,
  OpenChangeProvider: ({ children }: { children?: ReactNode }) => <>{children}</>,
  ChangeModeFrame: () => null,
}));

afterAll(() => {
  mock.module("@oba/ui/components/select", () => actualSelect);
  mock.module("next/navigation", () => actualNext);
  mock.module("@oba/ui/components/dropdown-menu", () => actualMenu);
  mock.module("next-intl", () => actualIntl);
  mock.module("next-auth/react", () => actualAuthReact);
  mock.module("@tanstack/react-query", () => actualReactQuery);
  mock.module("sonner", () => actualSonner);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualGetMe);
  mock.module("@oba/shared/features/users/hooks/use-update-me", () => actualUpdateMe);
  mock.module("@oba/shared/components/app-header/app-switcher", () => actualSwitcher);
  mock.module("@oba/shared/components/app-header/profile-dropdown", () => actualProfile);
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
  mock.module("@/features/versions/components/change-control", () => actualChange);
  mock.module("@/features/versions/components/open-change", () => actualOpen);
});

const { AppShell } = await import("./app-shell");

function render(): string {
  return renderToStaticMarkup(
    <AppShell>
      <p>page</p>
    </AppShell>
  );
}

const rowValues = (html: string): string[] =>
  [...html.matchAll(/data-value="([^"]+)"/g)].map((match) => match[1] ?? "");

/** Runs `action` with `console.error` silenced, for a path that logs a failure on purpose. */
async function withoutConsoleError(action: () => Promise<void>): Promise<void> {
  const original = console.error;
  console.error = () => undefined;
  try {
    await action();
  } finally {
    console.error = original;
  }
}

beforeEach(() => {
  memberships = [
    { organizationId: "org-1", name: "Korur" },
    { organizationId: "org-2", name: "Tayga" },
  ];
  managesOrganizations = false;
  selectValue = undefined;
  updateResult = async () => ({ success: true });
  for (const list of [updatedMe, switched, order, errorToasts]) list.length = 0;
});

describe("Kaya header", () => {
  test("the selector lists the memberships and marks the current one", () => {
    const html = render();
    expect(rowValues(html)).toEqual(["org-1", "org-2"]);
    expect(html).toContain('data-value="org-1" data-state="checked"');
    expect(html).toContain("Tayga");
  });

  test("a switch saves the default organization, then switches Kaya to it", async () => {
    render();
    await onValueChange("org-2");
    expect(updatedMe).toEqual([{ defaultOrganizationId: "org-2" }]);
    expect(order).toEqual(["saved", "switched org-2"]);
    expect(switched).toEqual(["org-2"]);
  });

  test("a refused switch shows the error toast and leaves Kaya where it was", async () => {
    updateResult = async () => {
      throw new Error("PUT /users/me was rejected");
    };
    render();
    await withoutConsoleError(() => onValueChange("org-2"));
    expect(updatedMe).toHaveLength(1);
    expect(switched).toEqual([]);
    expect(errorToasts).toEqual(["organizationSwitchFailed"]);
  });

  test("choosing the current organization saves nothing", async () => {
    render();
    await onValueChange("org-1");
    expect(updatedMe).toEqual([]);
    expect(switched).toEqual([]);
  });

  test("the profile menu has an Organization item for a manager, linked to Ulak", () => {
    managesOrganizations = true;
    const html = render();
    expect(html).toContain("<span>Organization</span>");
    expect(html).toContain(`href="${ULAK_LINKS.organizations}"`);
  });

  test("the profile menu has no Organization item for anyone else", () => {
    managesOrganizations = false;
    const html = render();
    expect(html).not.toContain("<span>Organization</span>");
    expect(html).toContain('data-profile=""');
  });

  test("Settings stays at the foot of the menu and the page is shown", () => {
    const html = render();
    expect(html).toContain("<p>page</p>");
    expect(html).toContain("Settings");
  });
});
