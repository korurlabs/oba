import { beforeEach, describe, expect, it, mock } from "bun:test";

type ChangeListener = (state: string) => void;

const platform: { OS: string } = { OS: "ios" };
const listeners: Set<ChangeListener> = new Set();
const emit = (state: string): void => {
  for (const listener of [...listeners]) listener(state);
};

mock.module("react-native", () => ({
  Platform: platform,
  AppState: {
    addEventListener: (_event: string, listener: ChangeListener) => {
      listeners.add(listener);
      return { remove: () => listeners.delete(listener) };
    },
  },
}));

mock.module("@/lib/env", () => ({
  ENV: {
    apiBaseUrl: "https://api.test",
    keycloakIssuer: "https://auth.test/realms/sensey",
    keycloakClientId: "kazan-mobile",
    accountUrl: "https://account.test",
    adminUrl: "https://admin.test",
    kazanWebUrl: "https://kazan.test",
  },
}));

const opened: string[] = [];
// What `openBrowserAsync` does before it resolves; by default nothing.
let onOpen: () => void = () => undefined;
let openResult: { type: string } | Error = { type: "dismiss" };
mock.module("expo-web-browser", () => ({
  openBrowserAsync: async (url: string) => {
    opened.push(url);
    onOpen();
    if (openResult instanceof Error) throw openResult;
    return openResult;
  },
  WebBrowserPresentationStyle: { PAGE_SHEET: "pageSheet" },
  WebBrowserResultType: { OPENED: "opened", DISMISS: "dismiss" },
}));

const {
  kazanWebUrl,
  keycloakAccountUrl,
  openInAppBrowser,
  openPlatformPage,
  platformPageUrl,
  watchReturnToApp,
} = await import("@/lib/browser");

/** Whether `promise` has settled after pending callbacks have run. */
async function settled(promise: Promise<unknown>): Promise<boolean> {
  let done = false;
  promise.then(
    () => (done = true),
    () => (done = true)
  );
  await new Promise((resolve) => setTimeout(resolve, 0));
  return done;
}

beforeEach(() => {
  opened.length = 0;
  listeners.clear();
  platform.OS = "ios";
  onOpen = () => undefined;
  openResult = { type: "dismiss" };
});

describe("browser URLs", () => {
  it("builds Account, Admin, Keycloak account and Kazan web URLs", () => {
    expect(platformPageUrl("onboarding")).toBe("https://account.test/onboarding");
    expect(platformPageUrl("invitations")).toBe("https://account.test/invitations");
    expect(platformPageUrl("newOrganization")).toBe("https://account.test/organizations/new");
    expect(platformPageUrl("profile")).toBe("https://account.test/");
    expect(platformPageUrl("organizations")).toBe("https://admin.test/");
    expect(keycloakAccountUrl("deviceActivity")).toBe(
      "https://auth.test/realms/sensey/account/account-security/device-activity"
    );
    expect(kazanWebUrl("boards/kzn/settings")).toBe("https://kazan.test/boards/kzn/settings");
    expect(kazanWebUrl("/boards")).toBe("https://kazan.test/boards");
  });

  it("opens an Oba web page in the in-app browser", async () => {
    await openPlatformPage("invitations");
    await openPlatformPage("organizations");
    expect(opened).toEqual(["https://account.test/invitations", "https://admin.test/"]);
    expect(listeners.size).toBe(0);
  });
});

describe("watchReturnToApp", () => {
  it("resolves on the first active state after the app left, then stops listening", async () => {
    const watch = watchReturnToApp();
    emit("active");
    expect(await settled(watch.returned)).toBe(false);
    emit("background");
    emit("active");
    expect(await settled(watch.returned)).toBe(true);
    expect(listeners.size).toBe(0);
  });

  it("stops listening when cancelled", () => {
    const watch = watchReturnToApp();
    watch.cancel();
    watch.cancel();
    expect(listeners.size).toBe(0);
  });
});

describe("openInAppBrowser on Android", () => {
  it("waits for the return even when the app left before the tab reported open", async () => {
    platform.OS = "android";
    openResult = { type: "opened" };
    onOpen = () => emit("background");
    const open = openInAppBrowser("https://kazan.test/boards");
    expect(await settled(open)).toBe(false);
    emit("active");
    expect(await settled(open)).toBe(true);
    expect(listeners.size).toBe(0);
  });

  it("does not wait when the tab did not open, and stops listening", async () => {
    platform.OS = "android";
    openResult = { type: "dismiss" };
    await openInAppBrowser("https://kazan.test/boards");
    expect(listeners.size).toBe(0);
  });

  it("re-throws a failure to open and stops listening", async () => {
    platform.OS = "android";
    openResult = new Error("no browser");
    const warn = console.warn;
    console.warn = () => undefined;
    try {
      await expect(openInAppBrowser("https://kazan.test/boards")).rejects.toThrow("no browser");
    } finally {
      console.warn = warn;
    }
    expect(listeners.size).toBe(0);
  });
});
