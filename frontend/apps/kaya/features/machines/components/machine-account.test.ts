import { describe, expect, test } from "bun:test";

import type { MachineAccount } from "../schemas";
import {
  accountPlanText,
  BILLING_TYPE_LABELS,
  billingTypeLabel,
  humanize,
} from "./machine-account";

function account(overrides: Partial<MachineAccount> = {}): MachineAccount {
  return {
    id: "a",
    provider: "CLAUDE",
    accountEmail: "ada@example.com",
    accountOrganizationName: "Acme",
    billingType: "stripe_subscription",
    seatTier: null,
    ...overrides,
  };
}

describe("billingTypeLabel", () => {
  test("names every billing type Claude Code reports", () => {
    expect(billingTypeLabel("stripe_subscription")).toBe("Subscription");
    expect(billingTypeLabel("stripe_subscription_contracted")).toBe("Contracted subscription");
    expect(billingTypeLabel("stripe_subscription_enterprise_self_serve")).toBe(
      "Enterprise self-serve subscription"
    );
    expect(billingTypeLabel("apple_subscription")).toBe("Apple subscription");
    expect(billingTypeLabel("google_play_subscription")).toBe("Google Play subscription");
    expect(billingTypeLabel("aws_marketplace")).toBe("AWS Marketplace");
    expect(billingTypeLabel("c4e_consumption_trial")).toBe("Consumption trial");
    expect(billingTypeLabel("usage_based")).toBe("Usage-based");
    for (const [value, label] of BILLING_TYPE_LABELS) {
      expect(label).not.toContain("_");
      expect(label).not.toBe(value);
    }
  });

  test("humanizes a billing type it does not know, and never shows it raw", () => {
    expect(billingTypeLabel("prepaid_credits")).toBe("Prepaid credits");
    expect(billingTypeLabel("toString")).toBe("ToString");
  });

  test("is null when no billing type was reported", () => {
    expect(billingTypeLabel(null)).toBeNull();
    expect(billingTypeLabel(undefined)).toBeNull();
    expect(billingTypeLabel("  ")).toBeNull();
  });
});

describe("humanize", () => {
  test("turns separators into spaces and capitalizes the first letter", () => {
    expect(humanize("max_5x")).toBe("Max 5x");
    expect(humanize("team_premium")).toBe("Team premium");
    expect(humanize("some--odd__value")).toBe("Some odd value");
  });
});

describe("accountPlanText", () => {
  test("joins the organization, the billing label and the seat tier", () => {
    expect(accountPlanText(account())).toBe("Acme · Subscription");
    expect(accountPlanText(account({ seatTier: "max_5x" }))).toBe("Acme · Subscription · Max 5x");
  });

  test("leaves out the parts the account did not report", () => {
    expect(accountPlanText(account({ accountOrganizationName: null }))).toBe("Subscription");
    expect(accountPlanText(account({ billingType: "", seatTier: "premium" }))).toBe(
      "Acme · Premium"
    );
    expect(
      accountPlanText(account({ accountOrganizationName: "", billingType: null, seatTier: null }))
    ).toBeNull();
    expect(accountPlanText(null)).toBeNull();
  });
});
