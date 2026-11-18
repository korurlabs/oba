import { describe, expect, test } from "bun:test";

import {
  NOT_REPORTED,
  NOT_REPORTED_FOR_PROVIDER,
  organizationText,
  tierText,
} from "./account-text";

describe("tierText", () => {
  test("prefers the seat tier, then the billing type", () => {
    expect(tierText({ seatTier: "max_20x", billingType: "stripe_subscription" })).toBe("max_20x");
    expect(tierText({ seatTier: null, billingType: "stripe_subscription" })).toBe(
      "stripe_subscription"
    );
    expect(tierText({ seatTier: "", billingType: undefined })).toBe(NOT_REPORTED);
    expect(NOT_REPORTED).toBe("Not reported");
  });
});

describe("organizationText", () => {
  test("names the organization, else calls the account personal", () => {
    expect(organizationText({ accountOrganizationName: "Korur" })).toBe("Korur");
    expect(organizationText({ accountOrganizationName: null })).toBe("Personal");
    expect(organizationText({})).toBe("Personal");
  });
});

describe("usage text for other providers", () => {
  test("says the usage is not reported for the provider", () => {
    expect(NOT_REPORTED_FOR_PROVIDER).toBe("Not reported for this provider");
  });
});
