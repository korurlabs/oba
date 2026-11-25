import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { ApiError } from "@oba/shared/api/axios";
import { APP_ACCESS_KEYS } from "@/features/organizations/hooks/query-keys";
import { AGENT_KEYS } from "./query-keys";

// Each hook is rendered once with React Query replaced by a recorder:
// `useQuery` and `useMutation` hand their options back, `useQueryClient`
// records every invalidation. The test then runs the options the way React
// Query does after a request, so it reads which caches a write refreshes and
// what the user is told.
type Key = readonly unknown[];
type MutationOptions = {
  mutationFn: (variables: never) => Promise<unknown>;
  onSuccess: (data: unknown, variables: never) => unknown;
  onError: (error: Error) => void;
  gcTime?: number;
};
type QueryOptions = { queryKey: Key; enabled: boolean };

const invalidated: Key[] = [];
const toasts: { kind: string; message: string }[] = [];

const queryClient = {
  invalidateQueries: ({ queryKey }: { queryKey: Key }) => {
    invalidated.push(queryKey);
    return Promise.resolve();
  },
};

const actualQuery = { ...(await import("@tanstack/react-query")) };
const actualIntl = { ...(await import("next-intl")) };
const actualSonner = { ...(await import("sonner")) };

mock.module("@tanstack/react-query", () => ({
  ...actualQuery,
  useQueryClient: () => queryClient,
  useQuery: (options: unknown) => options,
  useMutation: (options: MutationOptions) => options,
}));
mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("sonner", () => ({
  ...actualSonner,
  toast: {
    success: (message: string) => toasts.push({ kind: "success", message }),
    error: (message: string) => toasts.push({ kind: "error", message }),
  },
}));

afterAll(() => {
  mock.module("@tanstack/react-query", () => actualQuery);
  mock.module("next-intl", () => actualIntl);
  mock.module("sonner", () => actualSonner);
});

const hooks = await import("./use-agents");

const ORG = "org-1";

function build<T>(hook: () => T): T {
  let result: T | undefined;
  function Probe() {
    result = hook();
    return null;
  }
  renderToStaticMarkup(<Probe />);
  return result!;
}

function mutation(hook: () => unknown): MutationOptions {
  return build(hook) as MutationOptions;
}

beforeEach(() => {
  invalidated.length = 0;
  toasts.length = 0;
});

describe("agent queries", () => {
  test("the list waits for an organization", () => {
    const list = build(() => hooks.useAgents(ORG)) as unknown as QueryOptions;
    expect(list.queryKey).toEqual(AGENT_KEYS.list(ORG));
    expect(list.enabled).toBe(true);
    expect((build(() => hooks.useAgents(null)) as unknown as QueryOptions).enabled).toBe(false);
  });

  test("the options are read only while asked for", () => {
    const options = build(() => hooks.useAgentOptions(ORG, false)) as unknown as QueryOptions;
    expect(options.queryKey).toEqual(AGENT_KEYS.options(ORG));
    expect(options.enabled).toBe(false);
    expect((build(() => hooks.useAgentOptions(ORG, true)) as unknown as QueryOptions).enabled).toBe(
      true
    );
  });
});

describe("agent mutations: invalidation", () => {
  test("create, update and delete refresh the agents and the grants", async () => {
    for (const hook of [
      () => hooks.useCreateAgent(ORG),
      () => hooks.useUpdateAgent(ORG),
      () => hooks.useDeleteAgent(ORG),
    ]) {
      invalidated.length = 0;
      await mutation(hook).onSuccess(undefined, undefined as never);
      expect(invalidated).toEqual([AGENT_KEYS.list(ORG), APP_ACCESS_KEYS.organization(ORG)]);
    }
  });

  test("status, token and routine changes refresh the agent list only", async () => {
    for (const hook of [
      () => hooks.useUpdateAgentStatus(ORG),
      () => hooks.useIssueAgentToken(ORG),
      () => hooks.useRevokeAgentToken(ORG),
      () => hooks.useSetAgentRoutine(ORG),
      () => hooks.useRemoveAgentRoutine(ORG),
    ]) {
      invalidated.length = 0;
      await mutation(hook).onSuccess(undefined, { status: "ACTIVE" } as never);
      expect(invalidated).toEqual([AGENT_KEYS.list(ORG)]);
    }
  });

  test("the status toast says whether the agent was disabled or enabled", async () => {
    const status = mutation(() => hooks.useUpdateAgentStatus(ORG));
    await status.onSuccess(undefined, { agentId: "a", status: "DISABLED" } as never);
    await status.onSuccess(undefined, { agentId: "a", status: "ACTIVE" } as never);
    expect(toasts.map((toast) => toast.message)).toEqual(["disableSuccess", "enableSuccess"]);
  });

  test("an issued token is dropped from the mutation cache at once", () => {
    expect(mutation(() => hooks.useIssueAgentToken(ORG)).gcTime).toBe(0);
  });
});

describe("agent mutations: errors", () => {
  test("every mutation shows the API's message, or its own without one", () => {
    const cases: Array<[() => unknown, string]> = [
      [() => hooks.useCreateAgent(ORG), "createError"],
      [() => hooks.useUpdateAgent(ORG), "updateError"],
      [() => hooks.useUpdateAgentStatus(ORG), "statusError"],
      [() => hooks.useDeleteAgent(ORG), "deleteError"],
      [() => hooks.useIssueAgentToken(ORG), "token.issueError"],
      [() => hooks.useRevokeAgentToken(ORG), "token.revokeError"],
      [() => hooks.useSetAgentRoutine(ORG), "routine.setError"],
      [() => hooks.useRemoveAgentRoutine(ORG), "routine.removeError"],
    ];
    for (const [hook, fallback] of cases) {
      toasts.length = 0;
      const options = mutation(hook);
      options.onError(new ApiError("Refused by the API", { status: 422, code: "AGT_VAL_003" }));
      options.onError(new Error(""));
      expect(toasts).toEqual([
        { kind: "error", message: "Refused by the API" },
        { kind: "error", message: fallback },
      ]);
    }
  });

  test("a code the form shows on a field gets no toast", () => {
    const create = mutation(() => hooks.useCreateAgent(ORG, { formErrorCodes: ["AGT_STA_001"] }));
    create.onError(new ApiError("Name taken", { status: 409, code: "AGT_STA_001" }));
    const routine = mutation(() =>
      hooks.useSetAgentRoutine(ORG, { formErrorCodes: ["AGT_VAL_002"] })
    );
    routine.onError(new ApiError("Bad id", { status: 422, code: "AGT_VAL_002" }));
    expect(toasts).toEqual([]);

    create.onError(new ApiError("Other", { status: 422, code: "AGT_VAL_003" }));
    expect(toasts).toEqual([{ kind: "error", message: "Other" }]);
  });
});
