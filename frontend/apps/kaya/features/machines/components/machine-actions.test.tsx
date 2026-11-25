import { afterAll, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import type { MachineResponse } from "../schemas";

// The viewer and the machine mutations are replaced. The row menu is a Radix
// portal that a static render leaves closed, so its parts render in place and
// the markup lists every entry the menu offers. The actions themselves are real.
let viewer = { id: "user-ada", admin: false };

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
const actualHooks = { ...(await import("../hooks/use-machines")) };
const actualMenu = { ...(await import("@oba/ui/sky/dropdown-menu")) };
const mutation = () => ({ isPending: false, mutateAsync: async () => ({}) });
mock.module("@/lib/identity/identity-context", () => ({
  ...actualIdentity,
  useIdentity: () => ({
    me: { id: viewer.id },
    organization: { organizationId: "org-1", name: "Acme", role: "MEMBER" },
    switchOrganization: () => undefined,
    can: () => viewer.admin,
  }),
}));
mock.module("../hooks/use-machines", () => ({
  ...actualHooks,
  useRotateMachineToken: mutation,
  useRevokeMachineToken: mutation,
  useRetireMachine: mutation,
}));
mock.module("@oba/ui/sky/dropdown-menu", () => ({
  ...actualMenu,
  DropdownMenu: ({ children }: { children: ReactNode }) => <div data-menu="">{children}</div>,
  DropdownMenuTrigger: ({ children }: { children: ReactNode }) => <>{children}</>,
  DropdownMenuContent: ({ children }: { children: ReactNode }) => <ul>{children}</ul>,
  DropdownMenuSeparator: () => <hr />,
  DropdownMenuItem: ({ children, variant }: { children: ReactNode; variant?: string }) => (
    <li data-variant={variant}>{children}</li>
  ),
}));
afterAll(() => {
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
  mock.module("../hooks/use-machines", () => actualHooks);
  mock.module("@oba/ui/sky/dropdown-menu", () => actualMenu);
});

const { MachineActions } = await import("./machine-actions");
const { MachinesTable } = await import("./machines-table");

const NOW = Date.parse("2026-10-23T12:25:00Z");

function machine(overrides: Partial<MachineResponse> = {}): MachineResponse {
  return {
    id: "m-1",
    userId: "user-ada",
    userDisplayName: "Ada Lovelace",
    machineName: "ada-laptop",
    claudeCodeVersion: "2.1.0",
    account: {
      id: "acc-1",
      provider: "CLAUDE",
      accountEmail: "ada@example.com",
      accountOrganizationName: "Acme",
      billingType: "stripe_subscription",
      seatTier: null,
    },
    status: "ACTIVE",
    lastSeenAt: "2026-10-23T12:20:00Z",
    token: {
      id: "t-1",
      tokenPrefix: "kyt_abcdefgh",
      createdAt: "2026-10-01T09:00:00Z",
      expiresAt: "2027-10-01T09:00:00Z",
    },
    usage: {
      state: "KNOWN",
      capturedAt: "2026-10-23T12:21:00Z",
      reportedByMachineId: "m-1",
      reportedByMachineName: "ada-laptop",
      sharedWith: [],
      fiveHour: null,
      sevenDay: { usedPercentage: 47, resetsAt: "2026-10-26T09:00:00Z", reset: false },
    },
    sessionCount: 12,
    createdAt: "2026-10-01T09:00:00Z",
    ...overrides,
  };
}

/** The menu entries in order, as "label" or "label (destructive)". */
function menuEntries(html: string): string[] {
  return [...html.matchAll(/<li data-variant="(\w+)">(?:<svg[\s\S]*?<\/svg>)?([^<]*)<\/li>/g)].map(
    ([, variant, label]) => (variant === "destructive" ? `${label} (destructive)` : `${label}`)
  );
}

function renderMenu(subject: MachineResponse = machine()): string {
  return renderToStaticMarkup(<MachineActions machine={subject} layout="menu" />);
}

describe("MachineActions as a row menu", () => {
  test("puts the three actions behind one labelled icon button, Revoke and Retire destructive", () => {
    viewer = { id: "user-ada", admin: false };
    const html = renderMenu();
    expect(html).toContain('<span class="sr-only">Actions for ada-laptop</span>');
    expect(html.match(/<button/g)).toHaveLength(1);
    expect(menuEntries(html)).toEqual([
      "Rotate token",
      "Revoke token (destructive)",
      "Retire (destructive)",
    ]);
    expect(html).toContain("<hr/>");
  });

  test("offers an admin who is not the machine's user no rotation", () => {
    viewer = { id: "user-admin", admin: true };
    expect(menuEntries(renderMenu())).toEqual([
      "Revoke token (destructive)",
      "Retire (destructive)",
    ]);
    expect(renderMenu()).not.toContain("<hr/>");
  });

  test("offers New token, not Revoke, when the machine has no live token", () => {
    viewer = { id: "user-ada", admin: false };
    expect(menuEntries(renderMenu(machine({ token: null })))).toEqual([
      "New token",
      "Retire (destructive)",
    ]);
  });

  test("renders nothing for a viewer who may take no action", () => {
    viewer = { id: "user-other", admin: false };
    expect(renderMenu()).toBe("");
    viewer = { id: "user-ada", admin: false };
    expect(renderMenu(machine({ status: "RETIRED" }))).toBe("");
  });

  test("keeps one button per action on the machine's page", () => {
    viewer = { id: "user-ada", admin: false };
    const html = renderToStaticMarkup(<MachineActions machine={machine()} />);
    expect(html).not.toContain("data-menu");
    expect(html).toContain("Rotate token");
    expect(html).toContain("Revoke token");
    expect(html).toContain("Retire");
    expect(html.match(/<button/g)).toHaveLength(3);
  });
});

describe("MachinesTable", () => {
  test("shows version under the name, one account column and a compact usage cell", () => {
    viewer = { id: "user-ada", admin: false };
    const html = renderToStaticMarkup(<MachinesTable machines={[machine()]} now={NOW} />);
    const headers = [...html.matchAll(/<th [^>]*>([^<]*)/g)].map(([, text]) => text);
    expect(headers).toEqual([
      "Name",
      "User",
      "Account",
      "Usage",
      "Last seen",
      "Sessions",
      "Status",
      "",
    ]);
    expect(html).toContain("Claude Code 2.1.0");
    expect(html).toContain('title="ada@example.com"');
    expect(html).toContain('title="Acme · Subscription"');
    expect(html).not.toContain("stripe_subscription");
    expect(html).toContain(">5h<");
    expect(html).toContain(">Week<");
    expect(html).toContain(">47%<");
    expect(html).toContain("Weekly usage");
    expect(html).toContain("5-hour not reported");
    expect(html).not.toContain("resets");
    expect(html).toContain("Actions for ada-laptop");
  });

  test("says Not reported for a machine without an account", () => {
    viewer = { id: "user-ada", admin: false };
    const html = renderToStaticMarkup(
      <MachinesTable machines={[machine({ account: null, claudeCodeVersion: null })]} now={NOW} />
    );
    expect(html).toContain("Not reported");
    expect(html).not.toContain("Claude Code");
  });
});
