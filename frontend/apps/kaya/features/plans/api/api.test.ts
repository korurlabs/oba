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
    delete: async (url: string, config?: unknown) => {
      calls.push({ method: "DELETE", url, config });
      return answer();
    },
  },
}));

afterAll(() => {
  mock.module("@/lib/api/client", () => actualClient);
});

const plans = await import(".");

beforeEach(() => {
  calls.length = 0;
});

describe("plan reads", () => {
  test("the list sends its filters as query params; filters, detail, version and diff by path", async () => {
    await plans.listPlans({ page: 2, amount: 25, reviewState: "OPEN", kind: "SPEC" });
    await plans.getPlanFilters();
    await plans.getPlan("p-1");
    await plans.getPlanVersion("p-1", 3);
    await plans.getPlanDiff("p-1", 3);
    expect(calls).toEqual([
      {
        method: "GET",
        url: "/kaya/plans",
        config: { params: { page: 2, amount: 25, reviewState: "OPEN", kind: "SPEC" } },
      },
      { method: "GET", url: "/kaya/plans/filters", config: undefined },
      { method: "GET", url: "/kaya/plans/p-1", config: undefined },
      { method: "GET", url: "/kaya/plans/p-1/versions/3", config: undefined },
      { method: "GET", url: "/kaya/plans/p-1/versions/3/diff", config: undefined },
    ]);
  });
});

describe("feedback writes", () => {
  test("feedback posts its body; a withdraw deletes the item", async () => {
    await plans.giveFeedback("p-1", {
      versionNumber: 2,
      decision: "REQUEST_CHANGES",
      comment: "Split step 3",
      lineStart: 40,
      lineEnd: 44,
      quote: "3. Add the plan entities",
    });
    await plans.withdrawFeedback("p-1", "f-1");
    expect(calls).toEqual([
      {
        method: "POST",
        url: "/kaya/plans/p-1/feedback",
        body: {
          versionNumber: 2,
          decision: "REQUEST_CHANGES",
          comment: "Split step 3",
          lineStart: 40,
          lineEnd: 44,
          quote: "3. Add the plan entities",
        },
      },
      { method: "DELETE", url: "/kaya/plans/p-1/feedback/f-1", config: undefined },
    ]);
  });
});
