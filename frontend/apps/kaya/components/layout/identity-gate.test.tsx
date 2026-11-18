import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ComponentProps } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { KayaApiError } from "@/lib/api/errors";
import type { UserMeResponse } from "@/lib/identity/schemas";

// The gate reads the session, the URL and the query cache; each is replaced by
// a value the test sets. The sign-in redirect runs in an effect, which a server
// render does not run, so these tests cover which page the gate chooses. The
// buttons a page renders are recorded, so a test can click one by its label.
type SessionState = {
  status: "authenticated" | "unauthenticated" | "loading";
  data: { error?: string } | null;
};
type MeState = { data?: UserMeResponse; error: Error | null };

let session: SessionState;
let me: MeState;
let search = "";
let buttons: ComponentProps<"button">[] = [];
let logouts = 0;

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualQuery = { ...(await import("@tanstack/react-query")) };
const actualAuth = { ...(await import("next-auth/react")) };
const actualNavigation = { ...(await import("next/navigation")) };
const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
const actualButton = { ...(await import("@oba/ui/sky/button")) };
const actualLogoutClient = { ...(await import("@/lib/auth/logout-client")) };

mock.module("@tanstack/react-query", () => ({
  ...actualQuery,
  useQuery: () => ({ ...me, refetch: async () => undefined }),
}));
mock.module("next-auth/react", () => ({
  ...actualAuth,
  useSession: () => session,
}));
mock.module("next/navigation", () => ({
  ...actualNavigation,
  usePathname: () => "/plans",
  useSearchParams: () => new URLSearchParams(search),
}));
mock.module("@/lib/identity/identity-context", () => ({
  ...actualIdentity,
  IdentityProvider: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="provider">{children}</div>
  ),
}));

mock.module("@oba/ui/sky/button", () => ({
  ...actualButton,
  Button: (props: ComponentProps<typeof actualButton.Button>) => {
    buttons.push(props);
    return actualButton.Button(props);
  },
}));
mock.module("@/lib/auth/logout-client", () => ({
  ...actualLogoutClient,
  submitLogout: () => {
    logouts += 1;
  },
}));

afterAll(() => {
  mock.module("@tanstack/react-query", () => actualQuery);
  mock.module("next-auth/react", () => actualAuth);
  mock.module("next/navigation", () => actualNavigation);
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
  mock.module("@oba/ui/sky/button", () => actualButton);
  mock.module("@/lib/auth/logout-client", () => actualLogoutClient);
});

const { IdentityGate } = await import("./identity-gate");

const MEMBER_OF_ONE: UserMeResponse = {
  id: "u1",
  email: "dev@example.com",
  userStatus: "ACTIVE",
  displayName: null,
  defaultOrganizationId: null,
  memberships: [{ organizationId: "org-1", name: "Korur", role: "MEMBER" }],
  kayaWithheld: false,
  managesOrganizations: false,
};

function render(): string {
  buttons = [];
  return renderToStaticMarkup(
    <IdentityGate>
      <p>the dashboard</p>
    </IdentityGate>
  );
}

const NO_APP_TITLE = "Kaya is not open to you";
const NOT_MEMBER_TITLE = "You are not a member of an organization on Kaya";

/** Clicks the rendered button whose text contains the label, and fails if there is none. */
function click(label: string): void {
  const button = buttons.find((props: ComponentProps<"button">) =>
    renderToStaticMarkup(<>{props.children}</>).includes(label)
  );
  if (button?.onClick === undefined) {
    throw new Error(`No button "${label}" with a click handler was rendered`);
  }
  button.onClick({} as Parameters<NonNullable<ComponentProps<"button">["onClick"]>>[0]);
}

beforeEach(() => {
  session = { status: "authenticated", data: {} };
  me = { data: undefined, error: null };
  search = "";
  logouts = 0;
});

describe("identity gate", () => {
  test("shows the dashboard for a signed-in member", () => {
    me = { data: MEMBER_OF_ONE, error: null };
    const html = render();
    expect(html).toContain("the dashboard");
    expect(html).toContain('data-testid="provider"');
  });

  test("shows the splash while the session or the user loads", () => {
    session = { status: "loading", data: null };
    expect(render()).toContain('aria-busy="true"');
    session = { status: "authenticated", data: {} };
    expect(render()).toContain('aria-busy="true"');
    expect(render()).not.toContain("the dashboard");
  });

  test("shows the splash, not the dashboard, while a signed-out visitor is sent to sign in", () => {
    session = { status: "unauthenticated", data: null };
    const html = render();
    expect(html).toContain('aria-busy="true"');
    expect(html).not.toContain("the dashboard");
  });

  test("a session that can no longer be refreshed is sent to sign in", () => {
    session = { status: "authenticated", data: { error: "RefreshTokenError" } };
    expect(render()).toContain('aria-busy="true"');
  });

  test("a 401 from the user call is sent to sign in", () => {
    me = { error: new KayaApiError("x", 401, "GEN_AUT_001") };
    expect(render()).toContain('aria-busy="true"');
  });

  test("a 401 right after a fresh sign-in shows the refusal with a way out, instead of looping", () => {
    me = { error: new KayaApiError("x", 401, "GEN_AUT_001") };
    search = "error=SessionRejected";
    const html = render();
    expect(html).toContain("Kaya did not accept your session");
    expect(html).toContain("Sign out and sign in again");
    expect(html).not.toContain('aria-busy="true"');
    expect(html).not.toContain("Signing in failed");
  });

  test("the refusal page waits for the user call instead of showing before it answers", () => {
    search = "error=SessionRejected";
    expect(render()).toContain('aria-busy="true"');
    me = { data: MEMBER_OF_ONE, error: null };
    expect(render()).toContain("the dashboard");
  });

  test("a failed sign-in shows the reason and offers another try, instead of looping", () => {
    session = { status: "unauthenticated", data: null };
    search = "error=AccessDenied";
    const html = render();
    expect(html).toContain("Signing in failed");
    expect(html).toContain("AccessDenied");
    expect(html).toContain("Try again");
    expect(html).toContain('href="/api/auth/login?callbackUrl=%2F"');
  });

  test("a user with no membership sees the not-a-member page, whose switch signs out on click", () => {
    me = { data: { ...MEMBER_OF_ONE, memberships: [] }, error: null };
    const html = render();
    expect(html).toContain(NOT_MEMBER_TITLE);
    expect(html).toContain("Sign in with another account");
    expect(html).toContain("signs you out of every Oba app");
    expect(html).toContain("Open your account");
    expect(html).toContain('href="https://account.korur.com"');
    expect(html).not.toContain('href="/api/auth/login?callbackUrl=%2F"');
    expect(html).not.toContain("the dashboard");
  });

  test("APP_SEC_001 shows the no-app page, not the not-a-member page", () => {
    me = { error: new KayaApiError("x", 403, "APP_SEC_001") };
    const html = render();
    expect(html).toContain(NO_APP_TITLE);
    expect(html).not.toContain(NOT_MEMBER_TITLE);
    expect(html).not.toContain("the dashboard");
  });

  test("no membership because Kaya is withheld shows the no-app page before the not-a-member page", () => {
    me = { data: { ...MEMBER_OF_ONE, memberships: [], kayaWithheld: true }, error: null };
    const html = render();
    expect(html).toContain(NO_APP_TITLE);
    expect(html).not.toContain(NOT_MEMBER_TITLE);
  });

  test("no membership and nothing withheld still shows the not-a-member page", () => {
    me = { data: { ...MEMBER_OF_ONE, memberships: [], kayaWithheld: false }, error: null };
    const html = render();
    expect(html).toContain(NOT_MEMBER_TITLE);
    expect(html).not.toContain(NO_APP_TITLE);
  });

  test("a withheld flag with a remaining membership keeps the dashboard", () => {
    me = { data: { ...MEMBER_OF_ONE, kayaWithheld: true }, error: null };
    expect(render()).toContain("the dashboard");
  });

  test("a disabled user sees the disabled page before the no-app page", () => {
    me = { data: { ...MEMBER_OF_ONE, userStatus: "DISABLED", memberships: [], kayaWithheld: true }, error: null };
    expect(render()).toContain("Your account is disabled");
  });

  test("a no-app user stays on the refusal page after the session ends", () => {
    session = { status: "unauthenticated", data: null };
    me = { error: new KayaApiError("x", 403, "APP_SEC_001") };
    expect(render()).toContain(NO_APP_TITLE);
  });

  test("every membership refusal shows the not-a-member page", () => {
    const refusals: ReadonlyArray<readonly [number, string]> = [
      [403, "KAY_SEC_002"],
      [403, "ORG_SEC_002"],
      [403, "ORG_SEC_005"],
      [404, "ORG_NOT_001"],
      [404, "ORG_NOT_002"],
    ];
    for (const [status, code] of refusals) {
      me = { error: new KayaApiError("x", status, code) };
      expect(render()).toContain(NOT_MEMBER_TITLE);
    }
  });

  test("a disabled platform user sees the disabled page, even with organizations", () => {
    me = { data: { ...MEMBER_OF_ONE, userStatus: "DISABLED" }, error: null };
    const html = render();
    expect(html).toContain("Your account is disabled");
    expect(html).not.toContain("the dashboard");
  });

  test("the disabled page signs out through Kaya's logout route only when the user clicks", () => {
    me = { data: { ...MEMBER_OF_ONE, userStatus: "DISABLED" }, error: null };
    const html = render();
    expect(html).toContain("Signing out signs you out of every Oba app");
    expect(logouts).toBe(0);
    click("Sign out");
    expect(logouts).toBe(1);
  });

  test("every refusal page's sign-out goes through Kaya's logout route", () => {
    me = { data: { ...MEMBER_OF_ONE, memberships: [] }, error: null };
    render();
    click("Sign in with another account");
    me = { error: new KayaApiError("x", 401, "GEN_AUT_001") };
    search = "error=SessionRejected";
    render();
    click("Sign out and sign in again");
    expect(logouts).toBe(2);
  });

  test("a GEN_SEC_002 naming an organization is a missing membership", () => {
    me = { error: new KayaApiError("x", 403, "GEN_SEC_002", { organizationId: "org-9" }) };
    expect(render()).toContain(NOT_MEMBER_TITLE);
  });

  test("a disabled account sees the disabled page", () => {
    me = { error: new KayaApiError("x", 403, "GEN_SEC_002", { userStatus: "DISABLED" }) };
    const html = render();
    expect(html).toContain("Your account is disabled");
    expect(html).not.toContain(NOT_MEMBER_TITLE);
  });

  test("a refused user stays on the refusal page after the sign-out ends the session", () => {
    session = { status: "unauthenticated", data: null };
    me = { error: new KayaApiError("x", 403, "KAY_SEC_002") };
    expect(render()).toContain(NOT_MEMBER_TITLE);
    search = "error=AccessDenied";
    expect(render()).toContain(NOT_MEMBER_TITLE);
    me = { error: new KayaApiError("x", 403, "GEN_SEC_002", { userStatus: "DISABLED" }) };
    expect(render()).toContain("Your account is disabled");
  });

  test("any other failure of the user call shows its message with a retry", () => {
    me = { error: new KayaApiError("Kaya answered 500", 500, null) };
    const html = render();
    expect(html).toContain("Kaya could not load your account");
    expect(html).toContain("Kaya answered 500");
    expect(html).toContain("Try again");
  });

  test("a refused action of one user (GEN_SEC_003) is an ordinary failure", () => {
    me = { error: new KayaApiError("Only the user", 403, "GEN_SEC_003", { machineId: "a" }) };
    const html = render();
    expect(html).toContain("Kaya could not load your account");
    expect(html).not.toContain("Your account is disabled");
  });
});
