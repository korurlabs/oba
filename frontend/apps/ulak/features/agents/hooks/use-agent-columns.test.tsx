import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import type { ColumnDef } from "@tanstack/react-table";
import type { Agent } from "../schemas";

// The row menu is a Radix portal that a static render leaves closed. The menu
// and the confirm dialog are replaced by inline elements, so the markup lists
// every entry the row offers; each entry's and the confirm's handlers are
// recorded. Dates are formatted by a marker.
const selected: Record<string, () => void> = {};
const confirmed: (() => void)[] = [];

const actualIntl = { ...(await import("next-intl")) };
const actualMenu = { ...(await import("@oba/ui/sky/dropdown-menu")) };
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
const actualButton = { ...(await import("@oba/ui/sky/button")) };
const actualDateFormat = { ...(await import("@oba/shared/i18n/use-date-format")) };

function textOf(children: ReactNode): string {
  const parts = Array.isArray(children) ? children : [children];
  return parts.filter((part) => typeof part === "string").join("");
}

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string, values?: Record<string, string>) =>
    values ? `${key}:${JSON.stringify(values)}` : key,
}));
mock.module("@oba/shared/i18n/use-date-format", () => ({
  ...actualDateFormat,
  useDateFormat: () => ({ formatDateTime: (value: string) => `at(${value})` }),
}));
mock.module("@oba/ui/sky/dropdown-menu", () => ({
  ...actualMenu,
  DropdownMenu: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuTrigger: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DropdownMenuSeparator: () => <hr />,
  DropdownMenuItem: ({ children, onSelect }: { children: ReactNode; onSelect?: () => void }) => {
    const label = textOf(children);
    if (onSelect) selected[label] = onSelect;
    return <div>{`item:${label}`}</div>;
  },
}));
mock.module("@oba/ui/sky/dialog", () => ({
  ...actualDialog,
  Dialog: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogHeader: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogFooter: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogTitle: ({ children }: { children: ReactNode }) => <h2>{children}</h2>,
  DialogDescription: ({ children }: { children: ReactNode }) => <p>{children}</p>,
  DialogClose: ({ children }: { children: ReactNode }) => <>{children}</>,
}));
mock.module("@oba/ui/sky/button", () => ({
  ...actualButton,
  Button: ({
    children,
    onClick,
    variant,
  }: {
    children: ReactNode;
    onClick?: () => void;
    variant?: string;
  }) => {
    if (variant !== "destructive") return <span>{children}</span>;
    if (onClick) confirmed.push(onClick);
    return <span>{`confirm:${textOf(children)}`}</span>;
  },
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("@oba/shared/i18n/use-date-format", () => actualDateFormat);
  mock.module("@oba/ui/sky/dropdown-menu", () => actualMenu);
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
  mock.module("@oba/ui/sky/button", () => actualButton);
});

const { useAgentColumns } = await import("./use-agent-columns");

const routineAgent: Agent = {
  id: "a-routine",
  name: "Pentest agent",
  flow: "ROUTINE",
  provider: "ANTHROPIC",
  role: "CYBERSECURITY_ENGINEER",
  apps: ["KAZAN"],
  status: "ACTIVE",
  tokenPrefix: "oat_Q2xhdWRl",
  tokenLastUsedAt: "2026-11-24T10:15:30Z",
  tokenExpiresAt: "2027-11-24T10:15:30Z",
  routineId: "trig_01AbCdEf",
  routineSecretPrefix: "sk-ant-oat01",
  lastFiredAt: "2026-11-24T11:00:00Z",
  lastFireStatus: "UNAUTHORIZED",
  lastSessionUrl: null,
};

const apiAgent: Agent = {
  ...routineAgent,
  id: "a-api",
  name: "Sales agent",
  flow: "API",
  provider: "OPENAI",
  role: "SALES_ENGINEER",
  tokenPrefix: null,
  tokenLastUsedAt: null,
  tokenExpiresAt: null,
  routineId: null,
  routineSecretPrefix: null,
  lastFiredAt: null,
  lastFireStatus: null,
};

const calls: string[] = [];
const handlers = {
  onEdit: (agent: Agent) => void calls.push(`edit:${agent.id}`),
  onManageToken: (agent: Agent) => void calls.push(`token:${agent.id}`),
  onManageRoutine: (agent: Agent) => void calls.push(`routine:${agent.id}`),
  onStatusChange: (agentId: string, status: string) =>
    void calls.push(`status:${agentId}:${status}`),
  onDelete: (agentId: string) => void calls.push(`delete:${agentId}`),
  isUpdatingStatus: false,
  isDeleting: false,
};

function columns(): ColumnDef<Agent, unknown>[] {
  let result: ColumnDef<Agent, unknown>[] | undefined;
  function Probe() {
    result = useAgentColumns(handlers);
    return null;
  }
  renderToStaticMarkup(<Probe />);
  return result!;
}

function renderCell(columnId: string, agent: Agent): string {
  const column = columns().find((candidate) => candidate.id === columnId)!;
  const Cell = column.cell as (context: { row: { original: Agent } }) => ReactNode;
  return renderToStaticMarkup(<>{Cell({ row: { original: agent } })}</>);
}

beforeEach(() => {
  calls.length = 0;
  confirmed.length = 0;
  for (const key of Object.keys(selected)) delete selected[key];
});

describe("useAgentColumns", () => {
  test("has the name, role, flow, provider, apps, status, token, routine and actions columns", () => {
    expect(columns().map((column) => column.id)).toEqual([
      "name",
      "role",
      "flow",
      "provider",
      "apps",
      "status",
      "token",
      "routine",
      "actions",
    ]);
  });

  test("shows the role, flow, provider and status by their labels", () => {
    expect(renderCell("role", routineAgent)).toContain("roles.CYBERSECURITY_ENGINEER");
    expect(renderCell("flow", routineAgent)).toContain("flows.ROUTINE");
    expect(renderCell("provider", apiAgent)).toContain("providers.OPENAI");
    expect(renderCell("status", { ...routineAgent, status: "DISABLED" })).toContain(
      "statuses.DISABLED"
    );
  });

  test("a ROUTINE agent's token shows its prefix and last use", () => {
    const html = renderCell("token", routineAgent);
    expect(html).toContain("oat_Q2xhdWRl");
    expect(html).toContain(
      "token.lastUsed:{&quot;date&quot;:&quot;at(2026-11-24T10:15:30Z)&quot;}"
    );
  });

  test("a ROUTINE agent without token or routine says so", () => {
    const bare = { ...routineAgent, tokenPrefix: null, routineId: null, lastFireStatus: null };
    expect(renderCell("token", bare)).toContain("token.none");
    expect(renderCell("routine", bare)).toContain("routine.none");
  });

  test("a ROUTINE agent's routine shows its id and the last fire result", () => {
    const html = renderCell("routine", routineAgent);
    expect(html).toContain("trig_01AbCdEf");
    expect(html).toContain("fireStatuses.UNAUTHORIZED");
    expect(html).not.toContain("sk-ant-oat01");
  });

  test("a routine never fired says so", () => {
    expect(renderCell("routine", { ...routineAgent, lastFireStatus: null })).toContain(
      "routine.neverFired"
    );
  });

  test("an API agent shows the API flow note and no routine", () => {
    expect(renderCell("token", apiAgent)).toContain("apiFlowNote");
    expect(renderCell("routine", apiAgent)).toBe("");
  });

  test("a ROUTINE agent's menu offers edit, token, routine, disable and delete", () => {
    const html = renderCell("actions", routineAgent);
    for (const entry of [
      "actions.edit",
      "actions.token",
      "actions.routine",
      "actions.disable",
      "actions.delete",
    ]) {
      expect(html).toContain(`item:${entry}`);
    }
    expect(html).not.toContain("actions.enable");
  });

  test("an API agent's menu offers no token and no routine", () => {
    const html = renderCell("actions", apiAgent);
    expect(html).toContain("item:actions.edit");
    expect(html).not.toContain("actions.token");
    expect(html).not.toContain("actions.routine");
  });

  test("the menu entries pass the agent on, and a disabled agent is enabled", () => {
    renderCell("actions", { ...routineAgent, status: "DISABLED" });
    selected["actions.edit"]!();
    selected["actions.token"]!();
    selected["actions.routine"]!();
    selected["actions.enable"]!();

    expect(calls).toEqual([
      "edit:a-routine",
      "token:a-routine",
      "routine:a-routine",
      "status:a-routine:ACTIVE",
    ]);
  });

  test("an active agent is disabled", () => {
    renderCell("actions", routineAgent);
    selected["actions.disable"]!();
    expect(calls).toEqual(["status:a-routine:DISABLED"]);
  });

  test("the delete confirmation names the agent and passes its id on", () => {
    const html = renderCell("actions", routineAgent);
    expect(html).toContain("deleteConfirmDescription:{&quot;name&quot;:&quot;Pentest agent&quot;}");

    confirmed.at(-1)!();

    expect(calls).toEqual(["delete:a-routine"]);
  });
});
