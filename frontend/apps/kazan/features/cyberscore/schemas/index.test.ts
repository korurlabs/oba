import { describe, expect, test } from "bun:test";
import {
  CHECK_CATEGORIES,
  CHECK_ERROR_CODES,
  CHECK_NAME_MAX_LENGTH,
  CHECK_TEXT_MAX_LENGTH,
  CheckCategory,
  CheckOutcome,
  CheckStatus,
  CyberscoreLevel,
  checkListParamsSchema,
  checkOutcomeRequestSchema,
  checkRequestSchema,
  checkSchema,
  checkStatusRequestSchema,
  createCheckFormSchema,
  cyberscoreSchema,
  cyberscoreTrendPointSchema,
} from ".";

const t = (key: string) => key;

const validRequest = { name: "MFA on", expected: "Every admin uses MFA", weight: 10 };

describe("enums", () => {
  test("categories keep the server's display order", () => {
    expect([...CHECK_CATEGORIES]).toEqual([
      "IDENTITY",
      "OFFICE",
      "NETWORK_EDGE",
      "CLOUD_PLATFORM",
      "APPLICATIONS",
      "DATA",
      "DEVICES",
      "DEVELOPMENT_SUPPLY_CHAIN",
      "DETECTION_RESPONSE",
      "COMPLIANCE",
    ]);
  });

  test("error codes are the four the server sends", () => {
    expect(CHECK_ERROR_CODES).toEqual({
      duplicateName: "KAZ_STA_020",
      notFound: "KAZ_NOT_020",
      notAllowed: "ORG_SEC_003",
      invalid: "GEN_VAL_001",
    });
  });
});

describe("checkRequestSchema", () => {
  test("accepts the minimal body", () => {
    expect(checkRequestSchema.safeParse(validRequest).success).toBe(true);
  });

  test("accepts a full body with nulls", () => {
    const result = checkRequestSchema.safeParse({
      ...validRequest,
      description: null,
      sortOrder: null,
      category: null,
      outcome: null,
      status: null,
    });
    expect(result.success).toBe(true);
  });

  test.each([
    ["empty name", { name: "" }],
    ["name too long", { name: "a".repeat(CHECK_NAME_MAX_LENGTH + 1) }],
    ["empty expected", { expected: "" }],
    ["expected too long", { expected: "a".repeat(CHECK_TEXT_MAX_LENGTH + 1) }],
    ["weight 0", { weight: 0 }],
    ["weight 101", { weight: 101 }],
    ["fractional weight", { weight: 1.5 }],
    ["sort order -1", { sortOrder: -1 }],
    ["sort order 10000", { sortOrder: 10000 }],
    ["unknown category", { category: "OTHER" }],
    ["unknown outcome", { outcome: "MAYBE" }],
    ["unknown status", { status: "LATER" }],
  ])("rejects %s", (_label, patch) => {
    expect(checkRequestSchema.safeParse({ ...validRequest, ...patch }).success).toBe(false);
  });

  test("accepts the limits themselves", () => {
    expect(
      checkRequestSchema.safeParse({
        name: "a".repeat(CHECK_NAME_MAX_LENGTH),
        expected: "a".repeat(CHECK_TEXT_MAX_LENGTH),
        weight: 100,
        sortOrder: 9999,
      }).success
    ).toBe(true);
  });
});

describe("outcome, status and list params", () => {
  test("outcome request needs a known outcome", () => {
    expect(checkOutcomeRequestSchema.safeParse({ outcome: CheckOutcome.PASS }).success).toBe(true);
    expect(checkOutcomeRequestSchema.safeParse({}).success).toBe(false);
    expect(checkOutcomeRequestSchema.safeParse({ outcome: "DONE" }).success).toBe(false);
  });

  test("status request needs a known status", () => {
    expect(checkStatusRequestSchema.safeParse({ status: CheckStatus.DONE }).success).toBe(true);
    expect(checkStatusRequestSchema.safeParse({ status: "PASS" }).success).toBe(false);
  });

  test("list params allow no status or a known one", () => {
    expect(checkListParamsSchema.safeParse({}).success).toBe(true);
    expect(checkListParamsSchema.safeParse({ status: "IN_PROGRESS" }).success).toBe(true);
    expect(checkListParamsSchema.safeParse({ status: "PASS" }).success).toBe(false);
  });
});

describe("createCheckFormSchema", () => {
  const form = createCheckFormSchema(t);
  const valid = {
    name: "  MFA on  ",
    description: "",
    expected: "Every admin uses MFA",
    weight: "10",
    sortOrder: "0",
    category: null,
    outcome: CheckOutcome.TODO,
    status: CheckStatus.NOT_STARTED,
  };

  test("trims text and coerces number inputs", () => {
    const result = form.safeParse(valid);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.name).toBe("MFA on");
      expect(result.data.weight).toBe(10);
      expect(result.data.sortOrder).toBe(0);
    }
  });

  test.each([
    ["name", { name: "   " }, "errors.nameRequired"],
    ["name length", { name: "a".repeat(CHECK_NAME_MAX_LENGTH + 1) }, "errors.nameTooLong"],
    ["expected", { expected: " " }, "errors.expectedRequired"],
    [
      "description length",
      { description: "a".repeat(CHECK_TEXT_MAX_LENGTH + 1) },
      "errors.descriptionTooLong",
    ],
    ["weight high", { weight: "101" }, "errors.weightRange"],
    ["weight low", { weight: "0" }, "errors.weightRange"],
    ["weight fraction", { weight: "2.5" }, "errors.weightRange"],
    ["sort order high", { sortOrder: "10000" }, "errors.sortOrderRange"],
    ["sort order low", { sortOrder: "-1" }, "errors.sortOrderRange"],
  ])("reports %s with its translated message", (_label, patch, message) => {
    const result = form.safeParse({ ...valid, ...patch });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.map((issue) => issue.message)).toContain(message);
    }
  });

  test("an uncategorized check keeps a null category", () => {
    const result = form.safeParse({ ...valid, category: CheckCategory.DATA });
    expect(result.success && result.data.category).toBe("DATA");
  });
});

describe("response schemas", () => {
  test("parses the not-yet-scored score", () => {
    const result = cyberscoreSchema.safeParse({
      scorePercentage: 0,
      scoreLevel: "UNKNOWN",
      breakdownByCategory: {},
      scorableCheckCount: 0,
      lastCalculatedAt: null,
    });
    expect(result.success).toBe(true);
  });

  test("parses a scored response with a partial breakdown", () => {
    const result = cyberscoreSchema.safeParse({
      scorePercentage: 72.5,
      scoreLevel: CyberscoreLevel.LOW,
      breakdownByCategory: { IDENTITY: 80, DATA: 50 },
      scorableCheckCount: 4,
      lastCalculatedAt: "2026-11-25T08:30:00Z",
    });
    expect(result.success).toBe(true);
  });

  test("rejects a breakdown keyed by an unknown category", () => {
    const result = cyberscoreSchema.safeParse({
      scorePercentage: 10,
      scoreLevel: "CRITICAL",
      breakdownByCategory: { WEATHER: 10 },
      scorableCheckCount: 1,
      lastCalculatedAt: null,
    });
    expect(result.success).toBe(false);
  });

  test("parses a trend point and rejects a timestamp as its date", () => {
    expect(
      cyberscoreTrendPointSchema.safeParse({
        date: "2026-11-25",
        scorePercentage: 50,
        scoreLevel: "MEDIUM",
      }).success
    ).toBe(true);
    expect(
      cyberscoreTrendPointSchema.safeParse({
        date: "2026-11-25T00:00:00Z",
        scorePercentage: 50,
        scoreLevel: "MEDIUM",
      }).success
    ).toBe(false);
  });

  test("parses a check", () => {
    const result = checkSchema.safeParse({
      id: "c1",
      name: "MFA",
      description: null,
      expected: "MFA everywhere",
      weight: 10,
      sortOrder: 0,
      category: null,
      outcome: "TODO",
      status: "NOT_STARTED",
      updatedAt: "2026-11-25T08:30:00Z",
    });
    expect(result.success).toBe(true);
  });
});
