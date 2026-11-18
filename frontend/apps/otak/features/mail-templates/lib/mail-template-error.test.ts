import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import { mailTemplateErrorText, mailTemplateFieldErrors } from "./mail-template-error";

const t = (key: string): string => `t:${key}`;

function apiError(code: string | undefined, errorValues: Record<string, unknown> = {}): ApiError {
  return new ApiError("server message", { status: 422, code, errorValues });
}

describe("mailTemplateErrorText", () => {
  test.each([
    ["OTK_NOT_010", "t:errors.notFound"],
    ["OTK_STA_014", "t:errors.nameTaken"],
    ["ORG_SEC_003", "t:errors.forbidden"],
    ["GEN_VAL_001", "t:errors.validation"],
  ])("%s shows its translated text", (code, text) => {
    expect(mailTemplateErrorText(apiError(code), t, "toast.saveError")).toBe(text);
  });

  test("an unmapped code shows the server's message", () => {
    expect(mailTemplateErrorText(apiError("OTK_XXX_999"), t, "toast.saveError")).toBe(
      "server message"
    );
  });
});

describe("mailTemplateFieldErrors", () => {
  test("a taken name is shown on the name field", () => {
    expect(mailTemplateFieldErrors(apiError("OTK_STA_014"), t)).toEqual({
      name: "t:errors.nameTaken",
    });
  });

  test("a 422 puts each error value on its field", () => {
    expect(
      mailTemplateFieldErrors(apiError("GEN_VAL_001", { subject: "Subject is required" }), t)
    ).toEqual({ subject: "Subject is required" });
  });

  test("any other error names no field", () => {
    expect(mailTemplateFieldErrors(apiError("OTK_NOT_010"), t)).toEqual({});
  });
});
