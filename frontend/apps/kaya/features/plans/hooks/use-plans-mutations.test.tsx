import { afterAll, beforeAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { KayaApiError } from "@/lib/api/errors";

// The mutation hooks run for real; the query library and the API are replaced,
// so the test sees which keys each hook invalidates. The mocks are set around
// this file's tests only, because other test files replace the same modules.
const calls: string[] = [];
let failWith: Error | null = null;
let invalidated: unknown[][] = [];

const done = async (name: string) => {
  calls.push(name);
  if (failWith !== null) throw failWith;
  return {};
};

const recordingClient = {
  invalidateQueries: async (filters?: { queryKey?: readonly unknown[] }) => {
    invalidated.push([...(filters?.queryKey ?? [])]);
  },
};

type Options = {
  mutationFn: (args: never) => Promise<unknown>;
  onSuccess?: (result: unknown, args: never) => Promise<void> | void;
  onError?: (error: unknown, args: never) => Promise<void> | void;
};

/** Runs a mutation as the query library does: the function, then onSuccess or onError. */
function fakeMutation(options: Options) {
  return {
    mutateAsync: async (args: never) => {
      try {
        const result = await options.mutationFn(args);
        await options.onSuccess?.(result, args);
        return result;
      } catch (error: unknown) {
        await options.onError?.(error, args);
        throw error;
      }
    },
  };
}

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const previous = {
  query: { ...(await import("@tanstack/react-query")) },
  api: { ...(await import("../api")) },
  identity: { ...(await import("@/lib/identity/identity-context")) },
};

beforeAll(() => {
  mock.module("@tanstack/react-query", () => ({
    ...previous.query,
    useMutation: fakeMutation,
    useQueryClient: () => recordingClient,
  }));
  mock.module("../api", () => ({
    ...previous.api,
    giveFeedback: () => done("give"),
    withdrawFeedback: () => done("withdraw"),
  }));
  mock.module("@/lib/identity/identity-context", () => ({
    ...previous.identity,
    useIdentity: () => ({ organization: { organizationId: "org-1" } }),
  }));
});

afterAll(() => {
  mock.module("@tanstack/react-query", () => previous.query);
  mock.module("../api", () => previous.api);
  mock.module("@/lib/identity/identity-context", () => previous.identity);
});

const { useGiveFeedback, useWithdrawFeedback } = await import("./use-plans");

type Mutations = {
  give: ReturnType<typeof useGiveFeedback>;
  withdraw: ReturnType<typeof useWithdrawFeedback>;
};

let mutations: Mutations;

function Probe({ onRender }: { onRender: (found: Mutations) => void }) {
  onRender({ give: useGiveFeedback(), withdraw: useWithdrawFeedback() });
  return null;
}

beforeEach(() => {
  calls.length = 0;
  failWith = null;
  invalidated = [];
  const seen: Mutations[] = [];
  renderToStaticMarkup(<Probe onRender={(found) => seen.push(found)} />);
  mutations = seen[0] as Mutations;
});

const PLAN_KEYS_RELOADED = [
  ["plans", "org-1", "detail", "p-1"],
  ["plans", "org-1", "list"],
];

describe("what each feedback mutation reloads", () => {
  test("feedback and a withdraw reload the plan and every plan list", async () => {
    await mutations.give.mutateAsync({
      planId: "p-1",
      data: { versionNumber: 1, decision: "APPROVE" },
    });
    expect(calls).toEqual(["give"]);
    expect(invalidated).toEqual(PLAN_KEYS_RELOADED);
    invalidated = [];
    await mutations.withdraw.mutateAsync({ planId: "p-1", feedbackId: "f-1" });
    expect(calls).toEqual(["give", "withdraw"]);
    expect(invalidated).toEqual(PLAN_KEYS_RELOADED);
  });

  test("a refusal reaches the caller, is logged and reloads the plan", async () => {
    const warn = console.warn;
    const logged: unknown[][] = [];
    console.warn = (...args: unknown[]) => void logged.push(args);
    try {
      failWith = new KayaApiError("Not the latest version", 409, "KAY_PLN_001");
      await expect(
        mutations.give.mutateAsync({
          planId: "p-1",
          data: { versionNumber: 1, decision: "REQUEST_CHANGES", comment: "Redo" },
        })
      ).rejects.toBe(failWith);
      expect(logged.map((entry) => entry[0])).toEqual(["[plans] Feedback refused"]);
      expect(invalidated).toEqual(PLAN_KEYS_RELOADED);
    } finally {
      console.warn = warn;
    }
  });
});
