import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { NuqsTestingAdapter } from "nuqs/adapters/testing";
import { renderToStaticMarkup } from "react-dom/server";
import type { Agent } from "../schemas";

// The tab reads the agents through a query hook. The hooks, the columns, the
// table and the dialogs are replaced: the table prints its rows or the empty
// state it was given, so the test reads what the search and the filters in
// the URL passed down. The translator echoes its key.
let agents: Agent[];

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualIntl = { ...(await import("next-intl")) };
const actualHooks = { ...(await import("../hooks/use-agents")) };
const actualColumns = { ...(await import("../hooks/use-agent-columns")) };
const actualTable = { ...(await import("@/components/data-table")) };
const actualForm = { ...(await import("./agent-form-dialog")) };
const actualToken = { ...(await import("./agent-token-dialog")) };
const actualRoutine = { ...(await import("./agent-routine-dialog")) };

const mutation = { isPending: false, mutate: () => undefined };

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("../hooks/use-agents", () => ({
  ...actualHooks,
  useAgents: () => ({
    data: { data: agents },
    isLoading: false,
    isError: false,
    refetch: () => undefined,
  }),
  useUpdateAgentStatus: () => mutation,
  useDeleteAgent: () => mutation,
}));
mock.module("../hooks/use-agent-columns", () => ({ ...actualColumns, useAgentColumns: () => [] }));
mock.module("@/components/data-table", () => ({
  ...actualTable,
  DataTable: ({ data, emptyMessage }: { data: Agent[]; emptyMessage: ReactNode }) =>
    data.length === 0 ? (
      <>{emptyMessage}</>
    ) : (
      <p>{`rows:${data.map((agent) => agent.name).join(",")}`}</p>
    ),
}));
mock.module("./agent-form-dialog", () => ({ ...actualForm, AgentFormDialog: () => null }));
mock.module("./agent-token-dialog", () => ({ ...actualToken, AgentTokenDialog: () => null }));
mock.module("./agent-routine-dialog", () => ({
  ...actualRoutine,
  AgentRoutineDialog: () => null,
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("../hooks/use-agents", () => actualHooks);
  mock.module("../hooks/use-agent-columns", () => actualColumns);
  mock.module("@/components/data-table", () => actualTable);
  mock.module("./agent-form-dialog", () => actualForm);
  mock.module("./agent-token-dialog", () => actualToken);
  mock.module("./agent-routine-dialog", () => actualRoutine);
});

const { AgentsTab } = await import("./organization-agents-tab");

function render(searchParams = ""): string {
  return renderToStaticMarkup(
    <NuqsTestingAdapter searchParams={searchParams}>
      <AgentsTab organizationId="org-1" />
    </NuqsTestingAdapter>
  );
}

beforeEach(() => {
  agents = [
    { id: "a-1", name: "Pentest", flow: "ROUTINE", provider: "ANTHROPIC", status: "ACTIVE" },
    { id: "a-2", name: "Sales", flow: "API", provider: "OPENAI", status: "DISABLED" },
  ] as Agent[];
});

describe("AgentsTab", () => {
  test("one toolbar holds the search, the filter and the New agent button", () => {
    const html = render();

    expect(html).toContain('data-slot="list-toolbar"');
    expect(html).toContain("searchLabel");
    expect(html).toContain("trigger");
    expect(html).toContain("newAgent");
    expect(html).toContain("rows:Pentest,Sales");
  });

  test("the search and the filters in the URL narrow the rows", () => {
    expect(render("?search=sal")).toContain("rows:Sales");
    expect(render("?flow=ROUTINE")).toContain("rows:Pentest");
    expect(render("?provider=OPENAI&status=DISABLED")).toContain("rows:Sales");
  });

  test("filters that hide every agent offer to clear them, not to create", () => {
    const html = render("?provider=XAI");

    expect(html).toContain("filteredTitle");
    expect(html).toContain("clearFilters");
    expect(html.match(/newAgent/g)).toHaveLength(1);
  });

  test("an empty list offers the New agent button", () => {
    agents = [];

    const html = render();

    expect(html).toContain("emptyTitle");
    expect(html.match(/newAgent/g)).toHaveLength(2);
  });
});
