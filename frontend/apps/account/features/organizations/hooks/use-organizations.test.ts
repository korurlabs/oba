import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";

// The hook is called as a plain function: every hook it uses is replaced by a
// recorder, and `useMutation` hands back the options it was given, so a test
// runs `onSuccess` the way React Query does after a create that went through.
// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualReactQuery = { ...(await import("@tanstack/react-query")) };
const actualIntl = { ...(await import("next-intl")) };
const actualSonner = { ...(await import("sonner")) };
const actualApps = { ...(await import("@oba/shared/constants/apps")) };
const actualInvalidate = { ...(await import("@oba/shared/hooks/use-invalidate-org-queries")) };
const actualUpdateMe = { ...(await import("@oba/shared/features/users/hooks/use-update-me")) };

const ULAK_ORIGIN = "https://ulak.example.test";

type MutationOptions = {
  onSuccess: (response: { data?: { id?: string } }) => Promise<void>;
};

let updateResult: () => Promise<unknown>;
const events: string[] = [];
const errorToasts: string[] = [];

mock.module("@tanstack/react-query", () => ({
  ...actualReactQuery,
  useQueryClient: () => ({}),
  useMutation: (options: MutationOptions) => options,
}));
mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("sonner", () => ({
  ...actualSonner,
  toast: {
    ...actualSonner.toast,
    success: (message: string) => {
      events.push(`success ${message}`);
    },
    error: (message: string) => {
      errorToasts.push(message);
    },
  },
}));
mock.module("@oba/shared/constants/apps", () => ({
  ...actualApps,
  ULAK_LINKS: {
    get organizations() {
      return ULAK_ORIGIN;
    },
  },
}));
mock.module("@oba/shared/hooks/use-invalidate-org-queries", () => ({
  ...actualInvalidate,
  invalidateAllOrganizationQueries: async () => {
    events.push("invalidated");
  },
}));
mock.module("@oba/shared/features/users/hooks/use-update-me", () => ({
  ...actualUpdateMe,
  useUpdateMe: () => ({
    mutateAsync: async (data: { defaultOrganizationId: string }) => {
      events.push(`saved ${data.defaultOrganizationId}`);
      return updateResult();
    },
  }),
}));

const originalWindow = globalThis.window;
globalThis.window = {
  location: {
    assign: (url: string) => {
      events.push(`opened ${url}`);
    },
  },
} as unknown as Window & typeof globalThis;

afterAll(() => {
  globalThis.window = originalWindow;
  mock.module("@tanstack/react-query", () => actualReactQuery);
  mock.module("next-intl", () => actualIntl);
  mock.module("sonner", () => actualSonner);
  mock.module("@oba/shared/constants/apps", () => actualApps);
  mock.module("@oba/shared/hooks/use-invalidate-org-queries", () => actualInvalidate);
  mock.module("@oba/shared/features/users/hooks/use-update-me", () => actualUpdateMe);
});

const { useCreateOrganization } = await import("./use-organizations");

function createdOrganization(response: { data?: { id?: string } }): Promise<void> {
  // eslint-disable-next-line react-hooks/rules-of-hooks -- reason: every hook it calls is mocked, so it runs as a plain function that returns its mutation options
  const options = useCreateOrganization() as unknown as MutationOptions;
  return options.onSuccess(response);
}

/** Runs `action` with `console.error` silenced, for a path that logs a failure on purpose. */
async function withoutConsoleError(action: () => Promise<void>): Promise<void> {
  const originalConsoleError = console.error;
  console.error = () => undefined;
  try {
    await action();
  } finally {
    console.error = originalConsoleError;
  }
}

beforeEach(() => {
  updateResult = async () => ({ success: true });
  events.length = 0;
  errorToasts.length = 0;
});

describe("useCreateOrganization", () => {
  test("a new organization becomes the default, then Ulak opens on it", async () => {
    await createdOrganization({ data: { id: "org-new" } });

    expect(events).toEqual([
      "invalidated",
      "success createSuccess",
      "saved org-new",
      `opened ${ULAK_ORIGIN}`,
    ]);
    expect(errorToasts).toEqual([]);
  });

  test("a refused default shows the switch error and still opens Ulak", async () => {
    updateResult = async () => {
      throw new Error("PUT /users/me was rejected");
    };

    await withoutConsoleError(() => createdOrganization({ data: { id: "org-new" } }));

    expect(errorToasts).toEqual(["organizationSwitchFailed"]);
    expect(events.at(-1)).toBe(`opened ${ULAK_ORIGIN}`);
  });

  test("an answer without an id shows the switch error, saves nothing and opens Ulak", async () => {
    await withoutConsoleError(() => createdOrganization({}));

    expect(events.some((event) => event.startsWith("saved"))).toBe(false);
    expect(errorToasts).toEqual(["organizationSwitchFailed"]);
    expect(events.at(-1)).toBe(`opened ${ULAK_ORIGIN}`);
  });
});
