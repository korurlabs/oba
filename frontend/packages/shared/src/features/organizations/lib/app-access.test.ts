import { describe, expect, test } from "bun:test";
import type { UserMe, UserOrganization } from "../../users/schema";
import { canOpenApp, hasApp, organizationsWithApp } from "./app-access";

const organization = (
  name: string,
  apps?: string[],
  organizationStatus: UserOrganization["organizationStatus"] = "ACTIVE",
  role: UserOrganization["role"] = "MEMBER"
): UserOrganization => ({
  id: crypto.randomUUID(),
  name,
  role,
  organizationStatus,
  apps,
});

const me = (organizations?: UserOrganization[]): UserMe => ({
  id: "user-1",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  email: "user@example.test",
  userStatus: "ACTIVE",
  roles: ["USER"],
  profile: null,
  defaultOrganizationId: null,
  userPreferences: null,
  organizations,
});

describe("hasApp", () => {
  test("is true for a listed app", () => {
    expect(hasApp(organization("Korur", ["KAZAN", "KAYA"]), "KAYA")).toBe(true);
  });

  test("is false for an unlisted app", () => {
    expect(hasApp(organization("Korur", ["KAZAN"]), "KAYA")).toBe(false);
  });

  test("is false for an empty list and for a missing apps field", () => {
    expect(hasApp(organization("Korur", []), "KAZAN")).toBe(false);
    expect(hasApp(organization("Korur"), "KAZAN")).toBe(false);
  });
});

describe("organizationsWithApp", () => {
  test("keeps only the organizations that list the app", () => {
    const both = organization("Both", ["KAZAN", "KAYA"]);
    const kayaOnly = organization("Kaya", ["KAYA"]);

    expect(organizationsWithApp(me([both, kayaOnly, organization("None")]), "KAZAN")).toEqual([
      both,
    ]);
    expect(organizationsWithApp(me([both, kayaOnly]), "KAYA")).toEqual([both, kayaOnly]);
  });

  test("excludes a DISABLED organization even when it lists the app", () => {
    expect(
      organizationsWithApp(me([organization("Closed", ["KAZAN"], "DISABLED")]), "KAZAN")
    ).toEqual([]);
  });

  test("is empty for an unknown user and for a user without organizations", () => {
    expect(organizationsWithApp(null, "KAZAN")).toEqual([]);
    expect(organizationsWithApp(undefined, "KAZAN")).toEqual([]);
    expect(organizationsWithApp(me(), "KAZAN")).toEqual([]);
  });
});

describe("canOpenApp", () => {
  test("opens account for every user, known or not", () => {
    expect(canOpenApp(null, "account")).toBe(true);
    expect(canOpenApp(me([]), "account")).toBe(true);
  });

  test("opens kazan and kaya only where an ACTIVE organization lists the app", () => {
    const user = me([organization("Korur", ["KAZAN"])]);

    expect(canOpenApp(user, "kazan")).toBe(true);
    expect(canOpenApp(user, "kaya")).toBe(false);
  });

  test("opens kalkan and otak only where an ACTIVE organization lists the app", () => {
    const user = me([
      organization("Korur", ["KALKAN"]),
      organization("Closed", ["OTAK"], "DISABLED"),
    ]);

    expect(canOpenApp(user, "kalkan")).toBe(true);
    expect(canOpenApp(user, "otak")).toBe(false);
    expect(canOpenApp(me([organization("Korur")]), "kalkan")).toBe(false);
    expect(canOpenApp(null, "otak")).toBe(false);
  });

  test("refuses kazan and kaya when no membership has apps", () => {
    const user = me([organization("Korur"), organization("Other")]);

    expect(canOpenApp(user, "kazan")).toBe(false);
    expect(canOpenApp(user, "kaya")).toBe(false);
  });

  test("refuses kazan and kaya when the only grant is in a DISABLED organization", () => {
    const user = me([organization("Closed", ["KAZAN", "KAYA"], "DISABLED")]);

    expect(canOpenApp(user, "kazan")).toBe(false);
    expect(canOpenApp(user, "kaya")).toBe(false);
  });

  test("refuses kazan and kaya while the user is unknown", () => {
    expect(canOpenApp(null, "kazan")).toBe(false);
    expect(canOpenApp(undefined, "kaya")).toBe(false);
  });

  test("opens admin only for a user who manages an ACTIVE organization", () => {
    expect(canOpenApp(me([organization("Korur", [], "ACTIVE", "ADMIN")]), "admin")).toBe(true);
    expect(canOpenApp(me([organization("Korur", ["KAZAN"])]), "admin")).toBe(false);
    expect(canOpenApp(null, "admin")).toBe(false);
  });
});
