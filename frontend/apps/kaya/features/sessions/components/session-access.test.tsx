import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import type { OrganizationRole } from "@/lib/identity/schemas";
import { hasRole } from "@/lib/identity/schemas";

import type { SessionDetailResponse } from "../schemas";

// The session view runs in a browser under a session, a router and a query
// cache; the access rules under test only read the role and the signed-in
// user, so those three collaborators are replaced and the rest is real.
const ME = "user-me";
let role: OrganizationRole = "MEMBER";

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualQuery = { ...(await import("@tanstack/react-query")) };
const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
const actualNavigation = { ...(await import("next/navigation")) };

const idle = { isPending: false, mutate: () => undefined, mutateAsync: async () => ({}) };

mock.module("@tanstack/react-query", () => ({
  ...actualQuery,
  useQuery: () => ({ data: undefined, error: null, refetch: async () => undefined }),
  useMutation: () => idle,
  useMutationState: () => [],
  useQueryClient: () => ({}),
}));
mock.module("@/lib/identity/identity-context", () => ({
  ...actualIdentity,
  useIdentity: () => ({
    me: { id: ME },
    organization: { organizationId: "org-1", name: "Korur", role },
    switchOrganization: () => undefined,
    can: (required: OrganizationRole) => hasRole(role, required),
  }),
}));
mock.module("next/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ push: () => undefined, replace: () => undefined }),
}));

afterAll(() => {
  mock.module("@tanstack/react-query", () => actualQuery);
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
  mock.module("next/navigation", () => actualNavigation);
});

const { SessionLinks, DeleteSession } = await import("./session-view");
const { mayDeleteSession } = await import("../hooks/use-session-detail");

function session(overrides: Partial<SessionDetailResponse> = {}): SessionDetailResponse {
  return {
    id: "c1",
    sessionId: "s1",
    userId: "user-ada",
    ended: false,
    eventCount: 3,
    features: [],
    links: [
      {
        id: "l1",
        kind: "KAZAN_TICKET",
        reference: "KAYA-42",
        url: null,
        source: "RECORDER",
        createdBy: null,
        createdAt: "t",
      },
      {
        id: "l2",
        kind: "PULL_REQUEST",
        reference: "https://github.com/senseylabs/village/pull/7",
        url: "https://github.com/senseylabs/village/pull/7",
        source: "MANUAL",
        createdBy: "user-me",
        createdAt: "t",
      },
    ],
    createdAt: "t",
    subagents: [],
    skillUses: [],
    handoffs: [],
    ...overrides,
  };
}

function render(as: OrganizationRole, node: () => React.ReactElement) {
  role = as;
  return renderToStaticMarkup(node());
}

describe("deleting a session", () => {
  test("a member may delete only the sessions of their own user", () => {
    expect(render("MEMBER", () => <DeleteSession detail={session()} />)).toBe("");
    expect(render("MEMBER", () => <DeleteSession detail={session({ userId: ME })} />)).toContain(
      "Delete session"
    );
  });

  test("an admin and an owner may delete anyone's session", () => {
    for (const as of ["ADMIN", "OWNER"] as const) {
      expect(render(as, () => <DeleteSession detail={session()} />)).toContain("Delete session");
    }
  });

  test("a member owns no session that names no user", () => {
    expect(render("MEMBER", () => <DeleteSession detail={session({ userId: null })} />)).toBe("");
    expect(mayDeleteSession({ me: { id: ME }, can: () => false }, { userId: null })).toBe(false);
  });
});

describe("session links", () => {
  test("every member may add a link and remove each one", () => {
    const html = render("MEMBER", () => <SessionLinks detail={session()} />);
    expect(html).toContain("KAYA-42");
    expect(html).toContain("#7");
    expect(html).toContain('aria-label="Remove KAYA-42"');
    expect(html).toContain('aria-label="Remove #7"');
    expect(html).toContain("Add a link");
  });

  test("a session without links says so and still offers the form", () => {
    const html = render("MEMBER", () => <SessionLinks detail={session({ links: [] })} />);
    expect(html).toContain("No pull request or ticket linked yet.");
    expect(html).toContain("Add a link");
  });
});
