import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";

type Call = { url: string; config: unknown };

const calls: Call[] = [];
// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualClient = { ...(await import("@/lib/api/client")) };

mock.module("@/lib/api/client", () => ({
  api: {
    get: async (url: string, config?: unknown) => {
      calls.push({ url, config });
      return { data: { success: true, data: [] } };
    },
  },
}));

afterAll(() => {
  mock.module("@/lib/api/client", () => actualClient);
});

const rules = await import(".");
const conventions = await import("@/features/conventions/api");

beforeEach(() => {
  calls.length = 0;
});

describe("kind lists of the Rules and Conventions pages", () => {
  test("each kind reads its own endpoint with a repeated versionId", async () => {
    const params = { versionId: ["v-1", "v-2"] };
    await rules.listRules(params);
    await rules.listAgentsFiles(params);
    await rules.listRoles(params);
    await rules.listSubagents(params);
    await rules.listWorkflows(params);
    await rules.listTemplates(params);
    await rules.listHooks(params);
    await conventions.listConventions(params);
    const config = { params, paramsSerializer: { indexes: null } };
    expect(calls).toEqual(
      [
        "/kaya/rules",
        "/kaya/agents-files",
        "/kaya/roles",
        "/kaya/subagents",
        "/kaya/workflows",
        "/kaya/templates",
        "/kaya/hooks",
        "/kaya/conventions",
      ].map((url: string) => ({ url, config }))
    );
  });

  test("returns the envelope the server sent", async () => {
    expect(await rules.listRules({ versionId: ["v-1"] })).toEqual({ success: true, data: [] });
  });
});
