import { describe, expect, test } from "bun:test";
import {
  CheckOutcome,
  CheckStatus,
  type Check,
  type CheckFormValues,
  CHECK_OUTCOMES,
} from "../schemas";
import {
  CHECK_DEFAULT_WEIGHT,
  checkFormDefaults,
  checkFormToRequest,
  deriveCheckStatus,
} from "./check-form";

describe("deriveCheckStatus", () => {
  test.each([
    ["TODO", "NOT_STARTED"],
    ["NOT_APPLICABLE", "NOT_APPLICABLE"],
    ["PASS", "DONE"],
    ["PARTIAL", "DONE"],
    ["FAIL", "DONE"],
    ["NO_LICENSE", "DONE"],
  ])("%s gives %s", (outcome, status) => {
    expect(deriveCheckStatus(outcome as CheckOutcome)).toBe(status as CheckStatus);
  });

  test("covers every outcome", () => {
    for (const outcome of CHECK_OUTCOMES) {
      expect(deriveCheckStatus(outcome)).toBeDefined();
    }
  });
});

describe("checkFormDefaults", () => {
  test("a new check starts as TODO and not started", () => {
    const defaults = checkFormDefaults();
    expect(defaults.weight).toBe(CHECK_DEFAULT_WEIGHT);
    expect(defaults.outcome).toBe(CheckOutcome.TODO);
    expect(defaults.status).toBe(CheckStatus.NOT_STARTED);
    expect(defaults.category).toBeNull();
    expect(defaults.name).toBe("");
  });

  test("an edit prefills from the check and turns a null description into empty", () => {
    const check: Check = {
      id: "c1",
      name: "MFA",
      description: null,
      expected: "MFA everywhere",
      weight: 30,
      sortOrder: 4,
      category: "IDENTITY",
      outcome: "FAIL",
      status: "IN_PROGRESS",
      updatedAt: new Date("2026-11-25T08:30:00Z"),
    } as unknown as Check;
    const defaults = checkFormDefaults(check);
    expect(defaults).toEqual({
      name: "MFA",
      description: "",
      expected: "MFA everywhere",
      weight: 30,
      sortOrder: 4,
      category: "IDENTITY",
      outcome: "FAIL",
      status: "IN_PROGRESS",
    });
  });
});

describe("checkFormToRequest", () => {
  const values: CheckFormValues = {
    name: "MFA",
    description: "",
    expected: "MFA everywhere",
    weight: 10,
    sortOrder: 0,
    category: null,
    outcome: "PASS",
    status: "DONE",
  };

  test("sends an empty description as null and always sends outcome and status", () => {
    const request = checkFormToRequest(values);
    expect(request.description).toBeNull();
    expect(request.outcome).toBe("PASS");
    expect(request.status).toBe("DONE");
  });

  test("keeps a filled description", () => {
    expect(checkFormToRequest({ ...values, description: "why" }).description).toBe("why");
  });
});
