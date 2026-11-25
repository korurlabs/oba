import { describe, expect, test } from "bun:test";

import {
  TOKEN_COMMAND,
  TOKEN_LINE_START,
  accountSubscriptionText,
  machinePermissions,
} from "./machine-text";

const TOKEN = { id: "t", tokenPrefix: "kyt_abcdefgh", createdAt: "x", expiresAt: "y" };

describe("machinePermissions", () => {
  test("only the machine's own user rotates", () => {
    const machine = { userId: "me", status: "ACTIVE" as const, token: TOKEN };
    expect(machinePermissions(machine, "me", false)).toEqual({
      rotate: true,
      revoke: true,
      retire: true,
    });
    expect(machinePermissions(machine, "other", true)).toEqual({
      rotate: false,
      revoke: true,
      retire: true,
    });
    expect(machinePermissions(machine, "other", false)).toEqual({
      rotate: false,
      revoke: false,
      retire: false,
    });
    expect(machinePermissions(machine, null, true).rotate).toBe(false);
  });

  test("a retired machine takes no action, and revoke needs a live token", () => {
    expect(
      machinePermissions({ userId: "me", status: "RETIRED", token: null }, "me", true)
    ).toEqual({
      rotate: false,
      revoke: false,
      retire: false,
    });
    expect(
      machinePermissions({ userId: "me", status: "ACTIVE", token: null }, "me", false).revoke
    ).toBe(false);
  });
});

describe("machine texts", () => {
  test("joins the subscription parts the machine reported", () => {
    expect(
      accountSubscriptionText({
        id: "a",
        provider: "CLAUDE",
        accountOrganizationName: "Korur",
        billingType: "stripe",
        seatTier: null,
      })
    ).toBe("Korur · stripe");
    expect(accountSubscriptionText(null)).toBeNull();
  });

  test("names the Kagi command that reads the token from the terminal", () => {
    expect(TOKEN_COMMAND).toBe(
      "kagi secrets set --personal --path /kaya/recorder --from-file /dev/stdin"
    );
    expect(TOKEN_COMMAND).not.toContain(TOKEN_LINE_START);
    expect(TOKEN_LINE_START).toBe("KAYA_RECORDER_TOKEN=");
  });
});
