import { describe, expect, it, mock } from "bun:test";

mock.module("react-native", () => ({
  Platform: { OS: "ios" },
  AppState: { addEventListener: () => ({ remove: () => undefined }) },
}));

const secureStore = new Map<string, string>();
mock.module("expo-secure-store", () => ({
  getItemAsync: async (key: string) => secureStore.get(key) ?? null,
  setItemAsync: async (key: string, value: string) => {
    secureStore.set(key, value);
  },
  deleteItemAsync: async (key: string) => {
    secureStore.delete(key);
  },
}));

const {
  ACTIVE_ORGANIZATION_KEY,
  clearActiveOrganizationId,
  getActiveOrganizationId,
  pickActiveOrganizationId,
  setActiveOrganizationId,
  subscribeActiveOrganization,
} = await import("@/lib/organizationStore");

describe("pickActiveOrganizationId", () => {
  it("keeps the stored organization while the user is a member", () => {
    expect(
      pickActiveOrganizationId({
        memberOrganizationIds: ["a", "b"],
        storedOrganizationId: "b",
        defaultOrganizationId: "a",
      })
    ).toBe("b");
  });

  it("falls back to the default, then the first membership, then null", () => {
    expect(
      pickActiveOrganizationId({
        memberOrganizationIds: ["a", "b"],
        storedOrganizationId: "gone",
        defaultOrganizationId: "b",
      })
    ).toBe("b");
    expect(
      pickActiveOrganizationId({
        memberOrganizationIds: ["a", "b"],
        storedOrganizationId: null,
        defaultOrganizationId: "gone",
      })
    ).toBe("a");
    expect(
      pickActiveOrganizationId({
        memberOrganizationIds: [],
        storedOrganizationId: "a",
        defaultOrganizationId: "a",
      })
    ).toBeNull();
  });
});

describe("active organization store", () => {
  it("persists, notifies and clears", async () => {
    let notified = 0;
    const off = subscribeActiveOrganization(() => {
      notified += 1;
    });
    await setActiveOrganizationId("org-1");
    expect(getActiveOrganizationId()).toBe("org-1");
    expect(secureStore.get(ACTIVE_ORGANIZATION_KEY)).toBe("org-1");
    await clearActiveOrganizationId();
    expect(getActiveOrganizationId()).toBeNull();
    expect(secureStore.has(ACTIVE_ORGANIZATION_KEY)).toBe(false);
    expect(notified).toBe(2);
    off();
  });
});
