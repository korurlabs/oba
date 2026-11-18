import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import { eventErrorText, eventFieldErrors } from "./event-error";

const t = (key: string): string => `t:${key}`;

function apiError(code: string | undefined, errorValues: Record<string, unknown> = {}): ApiError {
  return new ApiError("server message", { status: 422, code, errorValues });
}

describe("eventErrorText", () => {
  test.each([
    ["OTK_NOT_009", "t:errors.notFound"],
    ["OTK_STA_013", "t:errors.nameTaken"],
    ["OTK_VAL_010", "t:errors.dateRange"],
    ["OTK_VAL_011", "t:errors.periodWithoutToday"],
    ["ORG_SEC_003", "t:errors.forbidden"],
    ["GEN_VAL_001", "t:errors.validation"],
  ])("%s shows its translated text", (code, text) => {
    expect(eventErrorText(apiError(code), t, "toast.saveError")).toBe(text);
  });

  test("an unmapped code shows the server's message", () => {
    expect(eventErrorText(apiError("OTK_XXX_999"), t, "toast.saveError")).toBe("server message");
  });
});

describe("eventFieldErrors", () => {
  test("a taken name is shown on the name field", () => {
    expect(eventFieldErrors(apiError("OTK_STA_013"), t)).toEqual({ name: "t:errors.nameTaken" });
  });

  test("an end before the start is shown on the end date", () => {
    expect(eventFieldErrors(apiError("OTK_VAL_010"), t)).toEqual({
      endDate: "t:errors.dateRange",
    });
  });

  test("a 422 puts each error value on its field", () => {
    expect(eventFieldErrors(apiError("GEN_VAL_001", { city: "too long" }), t)).toEqual({
      city: "too long",
    });
  });

  test("any other error names no field", () => {
    expect(eventFieldErrors(apiError("OTK_NOT_009"), t)).toEqual({});
  });
});
