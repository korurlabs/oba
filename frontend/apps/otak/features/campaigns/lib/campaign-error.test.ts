import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import { campaignErrorText, campaignFieldErrors } from "./campaign-error";

const t = (key: string): string => `t:${key}`;

function apiError(code: string | undefined, errorValues: Record<string, unknown> = {}): ApiError {
  return new ApiError("server message", { status: 422, code, errorValues });
}

describe("campaignErrorText", () => {
  test.each([
    ["OTK_NOT_012", "t:errors.notFound"],
    ["OTK_NOT_013", "t:errors.contentNotFound"],
    ["LGE_NOT_001", "t:errors.legalEntityNotFound"],
    ["OTK_NOT_009", "t:errors.eventNotFound"],
    ["OTK_NOT_004", "t:errors.icpNotFound"],
    ["OTK_STA_015", "t:errors.nameTaken"],
    ["OTK_STA_001", "t:errors.ownerNotMember"],
    ["APP_VAL_002", "t:errors.ownerWithoutOtakAccess"],
    ["OTK_VAL_016", "t:errors.statusDates"],
    ["OTK_VAL_017", "t:errors.dateRange"],
    ["STG_VAL_005", "t:errors.imageDuplicate"],
    ["ORG_SEC_003", "t:errors.forbidden"],
    ["GEN_VAL_001", "t:errors.validation"],
  ])("%s shows its translated text", (code, text) => {
    expect(campaignErrorText(apiError(code), t, "toast.saveError")).toBe(text);
  });

  test("another code shows the server's message", () => {
    expect(campaignErrorText(apiError("OTK_STA_007"), t, "toast.saveError")).toBe("server message");
  });

  test("an error without a message falls back to the fallback key", () => {
    expect(campaignErrorText(new Error(""), t, "toast.saveError")).toBe("t:toast.saveError");
  });
});

describe("campaignFieldErrors", () => {
  test.each([
    ["OTK_STA_015", "name", "t:errors.nameTaken"],
    ["OTK_STA_001", "ownerUserId", "t:errors.ownerNotMember"],
    ["APP_VAL_002", "ownerUserId", "t:errors.ownerWithoutOtakAccess"],
    ["OTK_NOT_009", "eventId", "t:errors.eventNotFound"],
    ["OTK_NOT_004", "icpId", "t:errors.icpNotFound"],
    ["OTK_VAL_016", "status", "t:errors.statusDates"],
    ["OTK_VAL_017", "endDate", "t:errors.dateRange"],
    ["LGE_NOT_001", "legalEntityId", "t:errors.legalEntityNotFound"],
  ])("%s is shown on the %s field", (code, field, text) => {
    expect(campaignFieldErrors(apiError(code), t)).toEqual({ [field]: text });
  });

  test("a 422 puts its errorValues on the fields", () => {
    expect(campaignFieldErrors(apiError("GEN_VAL_001", { name: "Name is required" }), t)).toEqual({
      name: "Name is required",
    });
  });

  test("any other error names no field", () => {
    expect(campaignFieldErrors(apiError("OTK_NOT_012"), t)).toEqual({});
    expect(campaignFieldErrors(new Error("x"), t)).toEqual({});
  });
});
