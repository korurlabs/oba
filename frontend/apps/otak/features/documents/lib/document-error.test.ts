import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import { documentErrorText, documentFieldErrors } from "./document-error";

const t = (key: string): string => `t:${key}`;

function apiError(code: string | undefined, errorValues: Record<string, unknown> = {}): ApiError {
  return new ApiError("server message", { status: 422, code, errorValues });
}

describe("documentErrorText", () => {
  test.each([
    ["DOC_NOT_002", "t:errors.notFound"],
    ["DOC_NOT_001", "t:errors.templateNotFound"],
    ["DOC_VAL_001", "t:errors.fileNotHtmlOrPdf"],
    ["DOC_VAL_002", "t:errors.templateOfOtherLegalEntity"],
    ["LGE_NOT_001", "t:errors.legalEntityNotFound"],
    ["OTK_NOT_001", "t:errors.relationNotFound"],
    ["AZR_UPS_001", "t:errors.uploadFailed"],
    ["APP_SEC_001", "t:errors.noOtakAccess"],
    ["APP_SEC_002", "t:errors.forbidden"],
    ["ORG_SEC_003", "t:errors.forbidden"],
    ["GEN_VAL_001", "t:errors.validation"],
  ])("%s shows its translated text", (code, text) => {
    expect(documentErrorText(apiError(code), t, "toast.saveError")).toBe(text);
  });

  test("an unmapped code shows the server's message", () => {
    expect(documentErrorText(apiError("DOC_XXX_999"), t, "toast.saveError")).toBe("server message");
  });

  test("an error without a message falls back to the fallback key", () => {
    expect(documentErrorText(new Error(""), t, "toast.saveError")).toBe("t:toast.saveError");
  });
});

describe("documentFieldErrors", () => {
  test("a legal entity that is gone is shown on the legal entity field", () => {
    expect(documentFieldErrors(apiError("LGE_NOT_001"), t)).toEqual({
      legalEntityId: "t:errors.legalEntityNotFound",
    });
  });

  test("a template of another legal entity is shown on the template field", () => {
    expect(documentFieldErrors(apiError("DOC_VAL_002"), t)).toEqual({
      templateId: "t:errors.templateOfOtherLegalEntity",
    });
  });

  test("a template that is gone is shown on the template field", () => {
    expect(documentFieldErrors(apiError("DOC_NOT_001"), t)).toEqual({
      templateId: "t:errors.templateNotFound",
    });
  });

  test("a relation that is gone is shown on the relation field", () => {
    expect(documentFieldErrors(apiError("OTK_NOT_001"), t)).toEqual({
      relationId: "t:errors.relationNotFound",
    });
  });

  test("a refused file part is shown on its file field", () => {
    expect(documentFieldErrors(apiError("DOC_VAL_001", { pdf: "not a PDF" }), t)).toEqual({
      pdfFile: "t:errors.fileNotHtmlOrPdf",
    });
  });

  test("a 422 names its fields, the owner as the relation", () => {
    expect(
      documentFieldErrors(
        apiError("GEN_VAL_001", { title: "must not be blank", entityId: "must not be null" }),
        t
      )
    ).toEqual({ title: "must not be blank", relationId: "must not be null" });
  });

  test("another refusal names no field", () => {
    expect(documentFieldErrors(apiError("DOC_NOT_002"), t)).toEqual({});
  });
});
