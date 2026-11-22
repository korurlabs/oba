import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import { documentTemplateErrorText, documentTemplateFieldErrors } from "./document-template-error";

const t = (key: string): string => `t:${key}`;

function apiError(code: string | undefined, errorValues: Record<string, unknown> = {}): ApiError {
  return new ApiError("server message", { status: 422, code, errorValues });
}

describe("documentTemplateErrorText", () => {
  test.each([
    ["DOC_NOT_001", "t:errors.notFound"],
    ["DOC_STA_001", "t:errors.nameTaken"],
    ["DOC_VAL_001", "t:errors.fileNotHtmlOrPdf"],
    ["LGE_NOT_001", "t:errors.legalEntityNotFound"],
    ["AZR_UPS_001", "t:errors.uploadFailed"],
    ["ORG_SEC_003", "t:errors.forbidden"],
    ["GEN_VAL_001", "t:errors.validation"],
  ])("%s shows its translated text", (code, text) => {
    expect(documentTemplateErrorText(apiError(code), t, "toast.saveError")).toBe(text);
  });

  test("an unmapped code shows the server's message", () => {
    expect(documentTemplateErrorText(apiError("OTK_XXX_999"), t, "toast.saveError")).toBe(
      "server message"
    );
  });

  test("an error without a message falls back to the fallback key", () => {
    expect(documentTemplateErrorText(new Error(""), t, "toast.saveError")).toBe(
      "t:toast.saveError"
    );
  });
});

describe("documentTemplateFieldErrors", () => {
  test("a taken name is shown on the name field", () => {
    expect(documentTemplateFieldErrors(apiError("DOC_STA_001"), t)).toEqual({
      name: "t:errors.nameTaken",
    });
  });

  test("a legal entity that no longer exists is shown on the legal entity field", () => {
    expect(documentTemplateFieldErrors(apiError("LGE_NOT_001"), t)).toEqual({
      legalEntityId: "t:errors.legalEntityNotFound",
    });
  });

  test("the retired Otak template codes have no text of their own", () => {
    expect(documentTemplateErrorText(apiError("OTK_STA_009"), t, "toast.saveError")).toBe(
      "server message"
    );
  });

  test("a refused file part is shown on its file field", () => {
    expect(documentTemplateFieldErrors(apiError("DOC_VAL_001", { pdf: "not a PDF" }), t)).toEqual({
      pdfFile: "t:errors.fileNotHtmlOrPdf",
    });
    expect(documentTemplateFieldErrors(apiError("DOC_VAL_001", { html: "not HTML" }), t)).toEqual({
      htmlFile: "t:errors.fileNotHtmlOrPdf",
    });
  });

  test("the retired content-type code has no text of its own", () => {
    expect(documentTemplateErrorText(apiError("OTK_VAL_006"), t, "toast.saveError")).toBe(
      "server message"
    );
  });

  test("a 422 puts each error value on its field", () => {
    expect(
      documentTemplateFieldErrors(apiError("GEN_VAL_001", { name: "Name is required" }), t)
    ).toEqual({ name: "Name is required" });
  });

  test("any other error names no field", () => {
    expect(documentTemplateFieldErrors(apiError("AZR_UPS_001"), t)).toEqual({});
    expect(documentTemplateFieldErrors(new Error("x"), t)).toEqual({});
  });
});
