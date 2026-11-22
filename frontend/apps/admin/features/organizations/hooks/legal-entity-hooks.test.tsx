import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { ApiError } from "@oba/shared/api/axios";
import { LEGAL_ENTITY_KEYS } from "./query-keys";

// Each hook is rendered once with React Query replaced by a recorder:
// `useQuery` and `useMutation` hand their options back, `useQueryClient`
// records every invalidation and removal. The test then runs the options the
// way React Query does after a request, so it reads which caches a write
// refreshes and which toast the user sees.
type Key = readonly unknown[];
type MutationOptions = {
  onSuccess: (data: unknown, variables: never) => unknown;
  onError: (error: Error, variables: never) => unknown;
};
type QueryOptions = { queryKey: Key; enabled: boolean };

const invalidated: Key[] = [];
const removed: Key[] = [];
const toasts: { kind: string; message: string }[] = [];

const queryClient = {
  invalidateQueries: ({ queryKey }: { queryKey: Key }) => {
    invalidated.push(queryKey);
    return Promise.resolve();
  },
  removeQueries: ({ queryKey }: { queryKey: Key }) => {
    removed.push(queryKey);
  },
};

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
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

const hooks = await import("./use-legal-entities");

const ORG = "org-1";
const ID = "le-1";
const notFound = new ApiError("Not found", { status: 404, code: "LGE_NOT_001" });
const taken = new ApiError("Taken", { status: 409, code: "LGE_STA_001" });
const invalid = new ApiError("Invalid", {
  status: 422,
  code: "GEN_VAL_001",
  errorValues: { shortName: "must not be blank" },
});
const forbidden = new ApiError("Insufficient role", { status: 403, code: "ORG_SEC_003" });

function build<T>(hook: () => unknown): T {
  let result: unknown;
  function Probe() {
    result = hook();
    return null;
  }
  renderToStaticMarkup(<Probe />);
  return result as T;
}

beforeEach(() => {
  invalidated.length = 0;
  removed.length = 0;
  toasts.length = 0;
});

describe("legal entity queries", () => {
  test("the list is keyed by the organization and waits for it", () => {
    expect(build<QueryOptions>(() => hooks.useLegalEntities(ORG))).toMatchObject({
      queryKey: LEGAL_ENTITY_KEYS.list(ORG),
      enabled: true,
    });
    expect(build<QueryOptions>(() => hooks.useLegalEntities(null)).enabled).toBe(false);
  });

  test("the detail waits for both ids", () => {
    expect(build<QueryOptions>(() => hooks.useLegalEntity(ORG, ID))).toMatchObject({
      queryKey: LEGAL_ENTITY_KEYS.detail(ORG, ID),
      enabled: true,
    });
    expect(build<QueryOptions>(() => hooks.useLegalEntity(ORG, null)).enabled).toBe(false);
  });
});

describe("legal entity mutations: success", () => {
  test("create refreshes the list and says so", async () => {
    await build<MutationOptions>(() => hooks.useCreateLegalEntity(ORG)).onSuccess(
      undefined,
      undefined as never
    );

    expect(invalidated).toEqual([LEGAL_ENTITY_KEYS.list(ORG)]);
    expect(toasts).toEqual([{ kind: "success", message: "legalEntities.createSuccess" }]);
  });

  test("update refreshes the list and every detail of the organization", async () => {
    await build<MutationOptions>(() => hooks.useUpdateLegalEntity(ORG)).onSuccess(undefined, {
      legalEntityId: ID,
    } as never);

    expect(invalidated).toEqual([LEGAL_ENTITY_KEYS.organization(ORG)]);
  });

  test("delete removes the detail, never refetches it, and refreshes the list", async () => {
    await build<MutationOptions>(() => hooks.useDeleteLegalEntity(ORG)).onSuccess(
      undefined,
      ID as never
    );

    expect(removed).toEqual([LEGAL_ENTITY_KEYS.detail(ORG, ID)]);
    expect(invalidated).toEqual([LEGAL_ENTITY_KEYS.list(ORG)]);
    expect(toasts).toEqual([{ kind: "success", message: "legalEntities.deleteSuccess" }]);
  });
});

describe("legal entity mutations: errors", () => {
  test.each([
    [
      "create",
      () => hooks.useCreateLegalEntity(ORG, { formErrorCodes: ["LGE_STA_001", "GEN_VAL_001"] }),
    ],
    [
      "update",
      () => hooks.useUpdateLegalEntity(ORG, { formErrorCodes: ["LGE_STA_001", "GEN_VAL_001"] }),
    ],
  ])("%s shows no toast for the codes the form shows on its fields", async (_, hook) => {
    const options = build<MutationOptions>(hook);

    await options.onError(taken, { legalEntityId: ID } as never);
    await options.onError(invalid, { legalEntityId: ID } as never);

    expect(toasts).toEqual([]);
  });

  test("without form codes a duplicate short name is a toast", async () => {
    await build<MutationOptions>(() => hooks.useCreateLegalEntity(ORG)).onError(
      taken,
      undefined as never
    );

    expect(toasts).toEqual([{ kind: "error", message: "Taken" }]);
  });

  test.each([
    ["create", () => hooks.useCreateLegalEntity(ORG), undefined],
    ["update", () => hooks.useUpdateLegalEntity(ORG), { legalEntityId: ID }],
    ["delete", () => hooks.useDeleteLegalEntity(ORG), ID],
  ])("%s shows the API's refusal of a too-low role as a toast", async (_, hook, variables) => {
    await build<MutationOptions>(hook).onError(forbidden, variables as never);

    expect(toasts).toEqual([{ kind: "error", message: "Insufficient role" }]);
  });

  test("a failure without a message falls back to the translated text", async () => {
    await build<MutationOptions>(() => hooks.useDeleteLegalEntity(ORG)).onError(
      new ApiError("", { status: 500 }),
      ID as never
    );

    expect(toasts).toEqual([{ kind: "error", message: "legalEntities.deleteError" }]);
  });

  test.each([
    ["update", () => hooks.useUpdateLegalEntity(ORG), { legalEntityId: ID }],
    ["delete", () => hooks.useDeleteLegalEntity(ORG), ID],
  ])("%s of a legal entity that is gone says so and drops it", async (_, hook, variables) => {
    await build<MutationOptions>(hook).onError(notFound, variables as never);

    expect(toasts).toEqual([{ kind: "error", message: "legalEntities.notFound" }]);
    expect(removed).toEqual([LEGAL_ENTITY_KEYS.detail(ORG, ID)]);
    expect(invalidated).toEqual([LEGAL_ENTITY_KEYS.list(ORG)]);
  });
});
