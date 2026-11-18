import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import type { UserMe, UserOrganization } from "@oba/shared/features/users/schema";

import {
  organizationAccessLoss,
  organizationAccessOf,
  watchOrganizationAccess,
  type OrganizationAccess,
  type OrganizationAccessLoss,
  type OrganizationAccessWatch,
} from "@/lib/organizationAccess";

const access = (memberIds: string[], kaizenIds: string[] = memberIds): OrganizationAccess => ({
  memberIds,
  kaizenIds,
});

describe("organizationAccessLoss", () => {
  it("is membership when the active organization is no longer in the list", () => {
    expect(organizationAccessLoss("org-a", "org-a", access(["org-b"]))).toBe("membership");
    expect(organizationAccessLoss("org-a", "org-a", access([]))).toBe("membership");
  });

  it("is kaizen when the user still belongs to the active organization but lost Kaizen in it", () => {
    expect(organizationAccessLoss("org-a", "org-a", access(["org-a", "org-b"], ["org-b"]))).toBe(
      "kaizen"
    );
    expect(organizationAccessLoss("org-a", "org-a", access(["org-a"], []))).toBe("kaizen");
  });

  it("is null for a missing permission inside an organization the user can still use", () => {
    expect(organizationAccessLoss("org-a", "org-a", access(["org-a", "org-b"]))).toBeNull();
  });

  it("is null for a 403 of an organization that is not the active one", () => {
    expect(organizationAccessLoss("org-b", "org-a", access(["org-a"]))).toBeNull();
    expect(
      organizationAccessLoss("org-b", "org-a", access(["org-a", "org-b"], ["org-a"]))
    ).toBeNull();
    expect(organizationAccessLoss("org-a", null, access([]))).toBeNull();
  });
});

describe("organizationAccessOf", () => {
  const organization = (id: string, apps: string[] | undefined): UserOrganization => ({
    id,
    name: id,
    role: "MEMBER",
    organizationStatus: "ACTIVE",
    apps,
  });
  const me = (organizations: UserOrganization[] | null): UserMe => ({
    id: "user-1",
    createdAt: "2026-11-01T09:00:00Z",
    updatedAt: "2026-11-01T09:00:00Z",
    email: "ada@example.com",
    userStatus: "ACTIVE",
    profile: null,
    defaultOrganizationId: null,
    userPreferences: null,
    organizations,
    pendingInvitations: null,
  });

  it("lists every membership and the ones that grant Kaizen", () => {
    expect(
      organizationAccessOf(
        me([
          organization("org-a", ["KAIZEN"]),
          organization("org-b", ["KAYA"]),
          organization("org-c", undefined),
        ])
      )
    ).toEqual({ memberIds: ["org-a", "org-b", "org-c"], kaizenIds: ["org-a"] });
  });

  it("is empty when the user has no organizations", () => {
    expect(organizationAccessOf(me(null))).toEqual({ memberIds: [], kaizenIds: [] });
  });
});

describe("watchOrganizationAccess", () => {
  let emit: (organizationId: string) => void;
  let loads: number;
  let current: OrganizationAccess;
  let release: () => void;
  let lost: [string, OrganizationAccessLoss][];
  let failures: unknown[];
  let active: string | null;
  const originalWarn = console.warn;

  function watch(
    load: () => Promise<OrganizationAccess> = async () => current
  ): OrganizationAccessWatch {
    return {
      subscribe: (listener) => {
        emit = listener;
        return () => undefined;
      },
      loadOrganizationAccess: async () => {
        loads += 1;
        return load();
      },
      activeOrganizationId: () => active,
      onAccessLost: (organizationId, loss) => {
        lost.push([organizationId, loss]);
      },
      onCheckFailed: (error) => {
        failures.push(error);
      },
    };
  }

  async function settle(): Promise<void> {
    for (let i = 0; i < 5; i += 1) await Promise.resolve();
  }

  beforeEach(() => {
    console.warn = () => undefined;
    loads = 0;
    current = access([]);
    lost = [];
    failures = [];
    active = "org-a";
    release = () => undefined;
  });

  afterEach(() => {
    console.warn = originalWarn;
  });

  it("reports the loss of the active organization", async () => {
    current = access(["org-b"]);
    watchOrganizationAccess(watch());
    emit("org-a");
    await settle();
    expect(lost).toEqual([["org-a", "membership"]]);
  });

  it("reports the loss of Kaizen in the active organization", async () => {
    current = access(["org-a", "org-b"], ["org-b"]);
    watchOrganizationAccess(watch());
    emit("org-a");
    await settle();
    expect(lost).toEqual([["org-a", "kaizen"]]);
  });

  it("keeps the organization on a 403 for a missing permission", async () => {
    current = access(["org-a"]);
    watchOrganizationAccess(watch());
    emit("org-a");
    await settle();
    expect(loads).toBe(1);
    expect(lost).toEqual([]);
  });

  it("reads the membership list once for 403s that arrive while it loads", async () => {
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    watchOrganizationAccess(
      watch(async () => {
        await gate;
        return access(["org-b"]);
      })
    );
    emit("org-a");
    emit("org-a");
    emit("org-a");
    release();
    await settle();
    expect(loads).toBe(1);
    expect(lost).toEqual([["org-a", "membership"]]);

    // A later 403 starts a new check.
    emit("org-a");
    await settle();
    expect(loads).toBe(2);
  });

  it("reports a failed check and checks again on the next 403", async () => {
    const failure = new Error("offline");
    watchOrganizationAccess(
      watch(async () => {
        throw failure;
      })
    );
    emit("org-a");
    await settle();
    expect(failures).toEqual([failure]);
    expect(lost).toEqual([]);

    emit("org-a");
    await settle();
    expect(loads).toBe(2);
  });
});
