import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { ApiError } from "@oba/shared/api/axios";
import { AZURE_CONNECTION_KEYS } from "./query-keys";

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

const hooks = await import("./use-azure-connections");

const ORG = "org-1";
const ID = "c-1";
const notFound = new ApiError("Not found", { status: 404, code: "AZR_NOT_001" });
const unavailable = new ApiError("Unavailable", { status: 404, code: "AZR_NOT_002" });
const inUse = new ApiError("In use", { status: 409, code: "AZR_STA_002" });
const identityTaken = new ApiError("Identity taken", { status: 409, code: "AZR_STA_003" });
const nameTaken = new ApiError("Name taken", { status: 409, code: "AZR_STA_004" });
const noClientId = new ApiError("No client id", { status: 422, code: "AZR_VAL_001" });
const forbidden = new ApiError("Insufficient role", { status: 403, code: "ORG_SEC_003" });
const FORM_CODES = ["AZR_NOT_002", "AZR_STA_003", "AZR_STA_004", "GEN_VAL_001"];

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

describe("Azure connection queries", () => {
  test("the list is keyed by the organization and waits for it", () => {
    expect(build<QueryOptions>(() => hooks.useAzureConnections(ORG))).toMatchObject({
      queryKey: AZURE_CONNECTION_KEYS.list(ORG),
      enabled: true,
    });
    expect(build<QueryOptions>(() => hooks.useAzureConnections(null)).enabled).toBe(false);
  });

  test("the detail waits for both ids", () => {
    expect(build<QueryOptions>(() => hooks.useAzureConnection(ORG, ID))).toMatchObject({
      queryKey: AZURE_CONNECTION_KEYS.detail(ORG, ID),
      enabled: true,
    });
    expect(build<QueryOptions>(() => hooks.useAzureConnection(ORG, null)).enabled).toBe(false);
  });

  test("the keys of an organization cover its list and details", () => {
    const prefix = AZURE_CONNECTION_KEYS.organization(ORG);
    expect(AZURE_CONNECTION_KEYS.list(ORG).slice(0, prefix.length)).toEqual([...prefix]);
    expect(AZURE_CONNECTION_KEYS.detail(ORG, ID).slice(0, prefix.length)).toEqual([...prefix]);
  });
});

describe("Azure connection mutations: success", () => {
  test("create refreshes the list and says so", async () => {
    await build<MutationOptions>(() => hooks.useCreateAzureConnection(ORG)).onSuccess(
      undefined,
      undefined as never
    );

    expect(invalidated).toEqual([AZURE_CONNECTION_KEYS.list(ORG)]);
    expect(toasts).toEqual([{ kind: "success", message: "azure.createSuccess" }]);
  });

  test("update refreshes the list and every detail of the organization", async () => {
    await build<MutationOptions>(() => hooks.useUpdateAzureConnection(ORG)).onSuccess(undefined, {
      connectionId: ID,
    } as never);

    expect(invalidated).toEqual([AZURE_CONNECTION_KEYS.organization(ORG)]);
    expect(toasts).toEqual([{ kind: "success", message: "azure.updateSuccess" }]);
  });

  test("delete removes the detail, never refetches it, and refreshes the list", async () => {
    await build<MutationOptions>(() => hooks.useDeleteAzureConnection(ORG)).onSuccess(
      undefined,
      ID as never
    );

    expect(removed).toEqual([AZURE_CONNECTION_KEYS.detail(ORG, ID)]);
    expect(invalidated).toEqual([AZURE_CONNECTION_KEYS.list(ORG)]);
    expect(toasts).toEqual([{ kind: "success", message: "azure.deleteSuccess" }]);
  });

  test("a verify that Azure accepted is a success", async () => {
    await build<MutationOptions>(() => hooks.useVerifyAzureConnection(ORG)).onSuccess(
      { success: true, data: { status: "CONNECTED" } },
      ID as never
    );

    expect(invalidated).toEqual([AZURE_CONNECTION_KEYS.organization(ORG)]);
    expect(toasts).toEqual([{ kind: "success", message: "azure.verifySuccess" }]);
  });

  test.each(["PENDING", "FAILING"])(
    "a verify answered with %s is an error toast, and the stored result still refreshes",
    async (status) => {
      await build<MutationOptions>(() => hooks.useVerifyAzureConnection(ORG)).onSuccess(
        { success: true, data: { status } },
        ID as never
      );

      expect(invalidated).toEqual([AZURE_CONNECTION_KEYS.organization(ORG)]);
      expect(toasts).toEqual([{ kind: "error", message: "azure.verifyFailed" }]);
    }
  );
});

describe("Azure connection mutations: errors", () => {
  test.each([
    ["create", () => hooks.useCreateAzureConnection(ORG, { formErrorCodes: FORM_CODES })],
    ["update", () => hooks.useUpdateAzureConnection(ORG, { formErrorCodes: FORM_CODES })],
  ])("%s shows no toast for the codes the form shows on its fields", async (_, hook) => {
    const options = build<MutationOptions>(hook);

    await options.onError(unavailable, { connectionId: ID } as never);
    await options.onError(identityTaken, { connectionId: ID } as never);
    await options.onError(nameTaken, { connectionId: ID } as never);

    expect(toasts).toEqual([]);
  });

  test.each([
    ["create", () => hooks.useCreateAzureConnection(ORG), undefined],
    ["update", () => hooks.useUpdateAzureConnection(ORG), { connectionId: ID }],
    ["delete", () => hooks.useDeleteAzureConnection(ORG), ID],
    ["verify", () => hooks.useVerifyAzureConnection(ORG), ID],
  ])("%s shows the API's refusal of a too-low role as a toast", async (_, hook, variables) => {
    await build<MutationOptions>(hook).onError(forbidden, variables as never);

    expect(toasts).toEqual([{ kind: "error", message: "Insufficient role" }]);
  });

  test("a failure without a message falls back to the translated text", async () => {
    await build<MutationOptions>(() => hooks.useVerifyAzureConnection(ORG)).onError(
      new ApiError("", { status: 500 }),
      ID as never
    );

    expect(toasts).toEqual([{ kind: "error", message: "azure.verifyError" }]);
  });

  test.each([
    ["update", () => hooks.useUpdateAzureConnection(ORG), { connectionId: ID }],
    ["delete", () => hooks.useDeleteAzureConnection(ORG), ID],
    ["verify", () => hooks.useVerifyAzureConnection(ORG), ID],
  ])("%s of a connection that is gone says so and drops it", async (_, hook, variables) => {
    await build<MutationOptions>(hook).onError(notFound, variables as never);

    expect(toasts).toEqual([{ kind: "error", message: "azure.notFound" }]);
    expect(removed).toEqual([AZURE_CONNECTION_KEYS.detail(ORG, ID)]);
    expect(invalidated).toEqual([AZURE_CONNECTION_KEYS.list(ORG)]);
  });

  test("delete of a connection a backup source uses explains why and keeps it", async () => {
    await build<MutationOptions>(() => hooks.useDeleteAzureConnection(ORG)).onError(
      inUse,
      ID as never
    );

    expect(toasts).toEqual([{ kind: "error", message: "azure.inUse" }]);
    expect(removed).toEqual([]);
    expect(invalidated).toEqual([]);
  });

  test("verify without a client id asks for one", async () => {
    await build<MutationOptions>(() => hooks.useVerifyAzureConnection(ORG)).onError(
      noClientId,
      ID as never
    );

    expect(toasts).toEqual([{ kind: "error", message: "azure.clientIdMissing" }]);
  });
});
