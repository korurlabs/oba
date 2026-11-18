import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";

type Call = { method: string; url: string; body?: unknown; config?: unknown };

const calls: Call[] = [];
// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualClient = { ...(await import("@/lib/api/client")) };

const answer = async () => ({ data: { success: true, data: null } });

mock.module("@/lib/api/client", () => ({
  api: {
    get: async (url: string, config?: unknown) => {
      calls.push({ method: "GET", url, config });
      return answer();
    },
    post: async (url: string, body?: unknown) => {
      calls.push({ method: "POST", url, body });
      return answer();
    },
    put: async (url: string, body?: unknown) => {
      calls.push({ method: "PUT", url, body });
      return answer();
    },
    delete: async (url: string, config?: unknown) => {
      calls.push({ method: "DELETE", url, config });
      return answer();
    },
  },
}));

afterAll(() => {
  mock.module("@/lib/api/client", () => actualClient);
});

const changes = await import(".");

beforeEach(() => {
  calls.length = 0;
});

describe("change calls", () => {
  test("the list sends its filters as query params; a detail, a submit and a review by version", async () => {
    await changes.listChanges({ page: 2, amount: 25, status: "IN_REVIEW", packId: "p-1" });
    await changes.getChange("v-1");
    await changes.submitChange("v-1", { reason: "Allow the kaizen CLI" });
    await changes.reviewChange("v-1", { decision: "REJECT", comment: "too broad" });
    expect(calls).toEqual([
      {
        method: "GET",
        url: "/kaya/changes",
        config: { params: { page: 2, amount: 25, status: "IN_REVIEW", packId: "p-1" } },
      },
      { method: "GET", url: "/kaya/changes/v-1", config: undefined },
      { method: "POST", url: "/kaya/changes/v-1/submit", body: { reason: "Allow the kaizen CLI" } },
      {
        method: "POST",
        url: "/kaya/changes/v-1/reviews",
        body: { decision: "REJECT", comment: "too broad" },
      },
    ]);
  });
});

describe("file history api", () => {
  test("a file's history by version and path", async () => {
    await changes.getFileHistory({ versionId: "v-1", path: "AGENTS.md", page: 1, amount: 20 });
    expect(calls).toEqual([
      {
        method: "GET",
        url: "/kaya/changes/history",
        config: { params: { versionId: "v-1", path: "AGENTS.md", page: 1, amount: 20 } },
      },
    ]);
  });
});
