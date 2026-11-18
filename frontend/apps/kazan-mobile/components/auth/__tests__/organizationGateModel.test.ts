import { describe, expect, it, mock } from "bun:test";
import type { UserOrganization } from "@oba/shared/features/users/schema";

// Bun cannot parse react-native's Flow source; the organization store reads only Platform.OS.
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

const { decideOrganizationGate } = await import("@/components/auth/organizationGateModel");

/** A membership that grants Kazan. */
const open = (id: string): UserOrganization => ({
  id,
  name: `Name of ${id}`,
  role: "MEMBER",
  organizationStatus: "ACTIVE",
  apps: ["KAZAN"],
});

/** A membership that does not grant Kazan. */
const closed = (id: string): UserOrganization => ({ ...open(id), apps: ["KAYA"] });

const member = (
  organizations: UserOrganization[],
  defaultOrganizationId: string | null = null
) => ({
  status: "success" as const,
  organizations,
  defaultOrganizationId,
});

describe("decideOrganizationGate", () => {
  it("opens the app at once with a stored organization while the list loads or fails", () => {
    expect(decideOrganizationGate("org-a", { status: "pending" })).toEqual({
      view: "app",
      target: undefined,
      kazanLostIn: null,
    });
    expect(decideOrganizationGate("org-a", { status: "error" })).toEqual({
      view: "app",
      target: undefined,
      kazanLostIn: null,
    });
  });

  it("waits for the list without a stored organization, and shows its failure", () => {
    expect(decideOrganizationGate(null, { status: "pending" }).view).toBe("loading");
    expect(decideOrganizationGate(null, { status: "error" }).view).toBe("error");
  });

  it("keeps the stored organization while the user is still a member with Kazan", () => {
    expect(
      decideOrganizationGate("org-b", member([open("org-a"), open("org-b")], "org-a"))
    ).toEqual({ view: "app", target: "org-b", kazanLostIn: null });
  });

  it("picks the default organization after sign-in and holds the app until it is active", () => {
    expect(decideOrganizationGate(null, member([open("org-a"), open("org-b")], "org-b"))).toEqual({
      view: "loading",
      target: "org-b",
      kazanLostIn: null,
    });
  });

  it("moves to another organization when the stored one is no longer the user's", () => {
    expect(decideOrganizationGate("org-gone", member([open("org-a")], null))).toEqual({
      view: "app",
      target: "org-a",
      kazanLostIn: null,
    });
  });

  it("moves a stored organization without Kazan to one with it and names the closed one", () => {
    expect(
      decideOrganizationGate("org-a", member([closed("org-a"), open("org-b")], "org-a"))
    ).toEqual({ view: "app", target: "org-b", kazanLostIn: "Name of org-a" });
  });

  it("passes over a default and a first organization without Kazan", () => {
    expect(
      decideOrganizationGate(
        null,
        member([closed("org-a"), closed("org-b"), open("org-c")], "org-b")
      )
    ).toEqual({ view: "loading", target: "org-c", kazanLostIn: null });
  });

  it("does not count a disabled organization or one without an apps list", () => {
    const disabled: UserOrganization = { ...open("org-a"), organizationStatus: "DISABLED" };
    const oldApi: UserOrganization = { ...open("org-b"), apps: undefined };
    expect(decideOrganizationGate(null, member([disabled, oldApi]))).toEqual({
      view: "no-kazan",
      target: null,
      kazanLostIn: null,
    });
  });

  it("shows the no-access state when no membership grants Kazan, also over a stored one", () => {
    expect(decideOrganizationGate(null, member([closed("org-a")], "org-a"))).toEqual({
      view: "no-kazan",
      target: null,
      kazanLostIn: null,
    });
    expect(decideOrganizationGate("org-a", member([closed("org-a"), closed("org-b")]))).toEqual({
      view: "no-kazan",
      target: null,
      kazanLostIn: "Name of org-a",
    });
  });

  it("shows onboarding to a user without an organization, also over a stale stored one", () => {
    expect(decideOrganizationGate(null, member([]))).toEqual({
      view: "no-organization",
      target: null,
      kazanLostIn: null,
    });
    expect(decideOrganizationGate("org-gone", member([], "org-gone"))).toEqual({
      view: "no-organization",
      target: null,
      kazanLostIn: null,
    });
  });
});
