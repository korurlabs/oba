import { describe, expect, test } from "bun:test";

import {
  machineCreateRequestSchema,
  machineCreatedResponseSchema,
  machineResponseSchema,
  machineTokenCreatedResponseSchema,
  machineUsageResponseSchema,
  machineUsageWindowSchema,
} from ".";

const MACHINE = {
  id: "machine-1",
  userId: "user-1",
  machineName: "ada-mini",
  status: "ACTIVE",
  sessionCount: 3,
  createdAt: "2026-10-20T08:00:00Z",
};

describe("machineCreateRequestSchema", () => {
  test("trims the machine name and takes 1 to 255 characters", () => {
    expect(machineCreateRequestSchema.parse({ machineName: "  ada-mini " }).machineName).toBe(
      "ada-mini"
    );
    expect(machineCreateRequestSchema.safeParse({ machineName: "   " }).success).toBe(false);
    expect(machineCreateRequestSchema.safeParse({ machineName: "a".repeat(255) }).success).toBe(
      true
    );
    expect(machineCreateRequestSchema.safeParse({ machineName: "a".repeat(256) }).success).toBe(
      false
    );
  });

  test("the user is optional, for an admin registering a machine for someone else", () => {
    expect(machineCreateRequestSchema.safeParse({ machineName: "m", userId: "a" }).success).toBe(
      true
    );
  });
});

describe("machineUsageWindowSchema", () => {
  test("reads a number, a decimal text and the null of a reset window", () => {
    for (const usedPercentage of [23.5, "41.00", null, undefined]) {
      expect(
        machineUsageWindowSchema.safeParse({
          usedPercentage,
          resetsAt: "2026-10-23T14:30:00Z",
          reset: usedPercentage == null,
        }).success
      ).toBe(true);
    }
  });

  test("refuses a window without its reset time or flag", () => {
    expect(machineUsageWindowSchema.safeParse({ usedPercentage: 1, reset: false }).success).toBe(
      false
    );
    expect(machineUsageWindowSchema.safeParse({ usedPercentage: 1, resetsAt: "x" }).success).toBe(
      false
    );
  });
});

describe("machineUsageResponseSchema", () => {
  test("reads the three states of the usage snapshot", () => {
    const known = machineUsageResponseSchema.parse({
      state: "KNOWN",
      capturedAt: "2026-10-23T12:21:00Z",
      reportedByMachineId: "a1",
      reportedByMachineName: "ada-mini",
      sharedWith: [{ machineId: "a2", machineName: "ada-studio" }],
      fiveHour: { usedPercentage: 12, resetsAt: "2026-10-23T14:30:00Z", reset: false },
      sevenDay: { usedPercentage: null, resetsAt: "2026-10-20T00:00:00Z", reset: true },
    });
    expect(known.sharedWith).toHaveLength(1);
    expect(known.sevenDay?.reset).toBe(true);
    expect(
      machineUsageResponseSchema.safeParse({ state: "NO_ACCOUNT", sharedWith: [] }).success
    ).toBe(true);
    expect(
      machineUsageResponseSchema.safeParse({ state: "NO_SNAPSHOT", sharedWith: [], fiveHour: null })
        .success
    ).toBe(true);
  });

  test("refuses an unknown state and a missing shared list", () => {
    expect(machineUsageResponseSchema.safeParse({ state: "STALE", sharedWith: [] }).success).toBe(
      false
    );
    expect(machineUsageResponseSchema.safeParse({ state: "KNOWN" }).success).toBe(false);
  });
});

describe("machineResponseSchema", () => {
  test("reads a machine with only its required fields", () => {
    expect(machineResponseSchema.safeParse(MACHINE).success).toBe(true);
  });

  test("reads a machine with account, token and usage", () => {
    const parsed = machineResponseSchema.parse({
      ...MACHINE,
      userDisplayName: "Ada",
      claudeCodeVersion: "2.1.0",
      lastSeenAt: null,
      account: {
        id: "acc",
        provider: "CLAUDE",
        accountEmail: "ada@example.com",
        accountOrganizationName: "Korur",
        billingType: "stripe_subscription",
        seatTier: "max_20x",
      },
      token: {
        id: "t",
        tokenPrefix: "kyt_abcdefgh",
        createdAt: "2026-10-20T08:00:00Z",
        lastUsedAt: null,
        expiresAt: "2027-10-20T08:00:00Z",
      },
      usage: { state: "NO_SNAPSHOT", sharedWith: [] },
    });
    expect(parsed.token?.tokenPrefix).toBe("kyt_abcdefgh");
    expect(parsed.usage?.state).toBe("NO_SNAPSHOT");
  });

  test("refuses an unknown status and a missing session count", () => {
    expect(machineResponseSchema.safeParse({ ...MACHINE, status: "PAUSED" }).success).toBe(false);
    expect(machineResponseSchema.safeParse({ ...MACHINE, sessionCount: undefined }).success).toBe(
      false
    );
  });
});

describe("token responses", () => {
  test("a created machine carries the plaintext token once", () => {
    expect(
      machineCreatedResponseSchema.safeParse({ machine: MACHINE, token: "kyt_secret" }).success
    ).toBe(true);
    expect(machineCreatedResponseSchema.safeParse({ machine: MACHINE }).success).toBe(false);
  });

  test("a rotated token names its id, prefix and expiry", () => {
    expect(
      machineTokenCreatedResponseSchema.safeParse({
        token: "kyt_secret",
        tokenId: "t",
        tokenPrefix: "kyt_abcdefgh",
        expiresAt: "2027-10-20T08:00:00Z",
      }).success
    ).toBe(true);
    expect(machineTokenCreatedResponseSchema.safeParse({ token: "kyt_secret" }).success).toBe(
      false
    );
  });
});
