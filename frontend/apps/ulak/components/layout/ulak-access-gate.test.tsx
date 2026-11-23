import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

// The gate redirects from an effect, which a server render does not run. The
// effect hook records its callbacks instead, and the test runs them after the
// render, so it reads where the gate sent the user.
type Membership = { id: string; name: string; role: string; organizationStatus: string };
type MeResult = {
  data?: { success: boolean; data?: { organizations?: Membership[] } };
  isSuccess: boolean;
  isError?: boolean;
};

let me: MeResult;
let replaced: string[];
let effects: Array<() => void>;

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualReact = { ...(await import("react")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualUseGetMe = { ...(await import("@oba/shared/features/users/hooks/use-get-me")) };

mock.module("react", () => ({
  ...actualReact,
  useEffect: (effect: () => void) => {
    effects.push(effect);
  },
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ replace: (target: string) => replaced.push(target) }),
}));
mock.module("@oba/shared/features/users/hooks/use-get-me", () => ({
  ...actualUseGetMe,
  useGetMe: () => me,
}));

afterAll(() => {
  mock.module("react", () => actualReact);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("@oba/shared/features/users/hooks/use-get-me", () => actualUseGetMe);
});

const { UlakAccessGate } = await import("./ulak-access-gate");

function membership(role: string, organizationStatus = "ACTIVE"): Membership {
  return { id: crypto.randomUUID(), name: "Org", role, organizationStatus };
}

function succeeded(organizations: Membership[] | undefined): MeResult {
  return { data: { success: true, data: { organizations } }, isSuccess: true };
}

/** Renders the gate, runs its effects, and returns its markup. */
function renderGate(): string {
  effects = [];
  const html = renderToStaticMarkup(<UlakAccessGate />);
  for (const effect of effects) effect();
  return html;
}

beforeEach(() => {
  me = succeeded([membership("OWNER")]);
  replaced = [];
  effects = [];
});

describe("UlakAccessGate", () => {
  test("replaces the route with /no-access for a member-only user", () => {
    me = succeeded([membership("MEMBER"), membership("GUEST")]);

    renderGate();

    expect(replaced).toEqual(["/no-access"]);
  });

  test("replaces the route for a user whose only managed organization is DISABLED", () => {
    me = succeeded([membership("OWNER", "DISABLED")]);

    renderGate();

    expect(replaced).toEqual(["/no-access"]);
  });

  test("replaces the route for a user without organizations", () => {
    me = succeeded(undefined);

    renderGate();

    expect(replaced).toEqual(["/no-access"]);
  });

  test("stays for an ACTIVE OWNER or ADMIN", () => {
    me = succeeded([membership("MEMBER"), membership("ADMIN")]);

    renderGate();

    expect(replaced).toEqual([]);
  });

  test("does nothing while the user loads", () => {
    me = { isSuccess: false };

    renderGate();

    expect(replaced).toEqual([]);
  });

  test("does nothing when loading the user failed", () => {
    me = { isSuccess: false, isError: true };

    renderGate();

    expect(replaced).toEqual([]);
  });

  test("does nothing when the successful answer carries no user", () => {
    me = { data: { success: true }, isSuccess: true };

    renderGate();

    expect(replaced).toEqual([]);
  });

  test("renders nothing", () => {
    expect(renderGate()).toBe("");
  });
});
