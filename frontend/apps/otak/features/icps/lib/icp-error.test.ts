import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import { icpErrorText, icpFieldErrors } from "./icp-error";

const t = (key: string): string => `t:${key}`;

function apiError(code: string | undefined, errorValues: Record<string, unknown> = {}): ApiError {
  return new ApiError("server message", { status: 422, code, errorValues });
}

describe("icpErrorText", () => {
  test.each([
    ["OTK_NOT_004", "t:errors.notFound"],
    ["OTK_STA_005", "t:errors.nameTaken"],
    ["OTK_VAL_001", "t:errors.sizeRange"],
    ["ORG_SEC_003", "t:errors.forbidden"],
    ["GEN_VAL_001", "t:errors.validation"],
  ])("%s shows its translated text", (code, text) => {
    expect(icpErrorText(apiError(code), t, "toast.saveFailed")).toBe(text);
  });

  test("an unmapped code shows the server's message", () => {
    expect(icpErrorText(apiError("OTK_XXX_999"), t, "toast.saveFailed")).toBe("server message");
  });

  test("an error without a message falls back to the fallback key", () => {
    expect(icpErrorText(new Error(""), t, "toast.saveFailed")).toBe("t:toast.saveFailed");
    expect(icpErrorText("boom", t, "toast.saveFailed")).toBe("t:toast.saveFailed");
  });
});

describe("icpFieldErrors", () => {
  test("a taken name is shown on the name field", () => {
    expect(icpFieldErrors(apiError("OTK_STA_005"), t)).toEqual({ name: "t:errors.nameTaken" });
  });

  test("an inverted size range is shown on the maximum size field", () => {
    expect(icpFieldErrors(apiError("OTK_VAL_001"), t)).toEqual({
      companySizeMax: "t:errors.sizeRange",
    });
  });

  test("a 422 puts each error value on its field and drops non-text values", () => {
    expect(
      icpFieldErrors(apiError("GEN_VAL_001", { name: "must not be blank", count: 3 }), t)
    ).toEqual({ name: "must not be blank" });
  });

  test("any other error names no field", () => {
    expect(icpFieldErrors(apiError("OTK_NOT_004"), t)).toEqual({});
    expect(icpFieldErrors(new Error("x"), t)).toEqual({});
  });
});
