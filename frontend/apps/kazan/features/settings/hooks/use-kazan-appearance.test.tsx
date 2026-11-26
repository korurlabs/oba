import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";
import { USERS_KEYS } from "@oba/shared/features/users/hooks/use-get-me";

// The save goes through a stand-in for `PUT /users/me` and a recorded toast;
// the hooks, the query cache and the messages are real.
let sent: Record<string, unknown>[];
let answer: () => Promise<{ success: boolean; message?: string }>;
let toasts: string[];

const actualUpdateMe = { ...(await import("@oba/shared/features/users/hooks/use-update-me")) };
const actualSonner = { ...(await import("sonner")) };

mock.module("@oba/shared/features/users/hooks/use-update-me", () => ({
  ...actualUpdateMe,
  useUpdateMe: () => ({
    mutateAsync: (data: { userPreferences: Record<string, unknown> }) => {
      sent.push(data.userPreferences);
      return answer();
    },
  }),
}));
mock.module("sonner", () => ({
  ...actualSonner,
  toast: { ...actualSonner.toast, error: (message: string) => toasts.push(message) },
}));
afterAll(() => {
  mock.module("@oba/shared/features/users/hooks/use-update-me", () => actualUpdateMe);
  mock.module("sonner", () => actualSonner);
});

const { useUpdateKazanAppearance, useUpdateTheme } = await import("./use-kazan-appearance");

/** Renders once to obtain the hook's mutation, bound to the given cache. */
function mutationOf<T>(queryClient: QueryClient, useHook: () => T): T {
  let result: T | undefined;
  function Probe() {
    result = useHook();
    return null;
  }
  renderToStaticMarkup(
    <QueryClientProvider client={queryClient}>
      <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
        <Probe />
      </NextIntlClientProvider>
    </QueryClientProvider>
  );
  if (!result) throw new Error("the probe did not render");
  return result;
}

function cacheWith(userPreferences: Record<string, unknown>): QueryClient {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  queryClient.setQueryData(USERS_KEYS.me, { success: true, data: { userPreferences } });
  return queryClient;
}

beforeEach(() => {
  sent = [];
  toasts = [];
  answer = async () => ({ success: true });
});

describe("useUpdateKazanAppearance", () => {
  test("sends the change merged over every other stored preference", async () => {
    const queryClient = cacheWith({ theme: "dark", kazanAppearance: { layout: "classic" } });
    const save = mutationOf(queryClient, useUpdateKazanAppearance);

    await save.mutateAsync({ background: "mist" });

    expect(sent).toEqual([
      { theme: "dark", kazanAppearance: { layout: "classic", background: "mist" } },
    ]);
    const me = queryClient.getQueryData<{ data: { userPreferences: unknown } }>(USERS_KEYS.me);
    expect(me?.data.userPreferences).toEqual(sent[0]);
  });

  test("shows the error toast and keeps the cache when the save is refused", async () => {
    answer = async () => ({ success: false, message: "refused" });
    const queryClient = cacheWith({ kazanAppearance: { layout: "modern" } });
    const save = mutationOf(queryClient, useUpdateKazanAppearance);
    const originalError = console.error;
    console.error = () => {};

    try {
      await expect(save.mutateAsync({ layout: "classic" })).rejects.toThrow("refused");
    } finally {
      console.error = originalError;
    }

    expect(toasts).toEqual([en.appearance.saveError]);
    const me = queryClient.getQueryData<{ data: { userPreferences: unknown } }>(USERS_KEYS.me);
    expect(me?.data.userPreferences).toEqual({ kazanAppearance: { layout: "modern" } });
  });
});

describe("useUpdateTheme", () => {
  test("stores the theme beside the Kazan appearance", async () => {
    const queryClient = cacheWith({ kazanAppearance: { layout: "classic" } });
    const save = mutationOf(queryClient, useUpdateTheme);

    await save.mutateAsync("light");

    expect(sent).toEqual([{ kazanAppearance: { layout: "classic" }, theme: "light" }]);
  });
});
