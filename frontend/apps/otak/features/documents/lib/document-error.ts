import { isApiError } from "@oba/shared/api/axios";
import { apiErrorText, validationFieldErrors } from "@/lib/api-error-text";

/** Refusals of the document and legal entity endpoints that have a text of their own. */
const ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  DOC_NOT_002: "errors.notFound",
  DOC_NOT_001: "errors.templateNotFound",
  DOC_VAL_001: "errors.fileNotHtmlOrPdf",
  DOC_VAL_002: "errors.templateOfOtherLegalEntity",
  LGE_NOT_001: "errors.legalEntityNotFound",
  OTK_NOT_001: "errors.relationNotFound",
  AZR_UPS_001: "errors.uploadFailed",
  APP_SEC_001: "errors.noOtakAccess",
  APP_SEC_002: "errors.forbidden",
  ORG_SEC_003: "errors.forbidden",
  GEN_VAL_001: "errors.validation",
};

/**
 * The server's names of the request properties and file parts, mapped to
 * the form fields that show them: the owner is the picked relation, and the
 * multipart parts are `html` and `pdf`.
 */
const FIELD_ALIASES: Partial<Record<string, string>> = {
  entityId: "relationId",
  html: "htmlFile",
  pdf: "pdfFile",
};

/**
 * The text a failed document request shows: a translated text for the
 * refusals the user can act on, else the server's message, else
 * `fallbackKey`. `t` is the `documents` translator.
 */
export function documentErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string
): string {
  return apiErrorText(error, t, fallbackKey, ERROR_TEXT_KEYS);
}

/**
 * The form fields a refused save or upload names, with the message for
 * each: the legal entity, the template or the relation that no longer
 * exists or does not fit, the file part the server did not take as HTML or
 * PDF, else the `errorValues` of a 422. `t` is the `documents` translator.
 */
export function documentFieldErrors(
  error: unknown,
  t: (key: string) => string
): Record<string, string> {
  if (isApiError(error, "LGE_NOT_001")) {
    return { legalEntityId: t("errors.legalEntityNotFound") };
  }
  if (isApiError(error, "DOC_VAL_002")) {
    return { templateId: t("errors.templateOfOtherLegalEntity") };
  }
  if (isApiError(error, "DOC_NOT_001")) {
    return { templateId: t("errors.templateNotFound") };
  }
  if (isApiError(error, "OTK_NOT_001")) {
    return { relationId: t("errors.relationNotFound") };
  }
  if (isApiError(error, "DOC_VAL_001")) {
    const text = documentErrorText(error, t, "toast.uploadError");
    return Object.fromEntries(
      Object.keys(error.errorValues).map((field) => [FIELD_ALIASES[field] ?? field, text])
    );
  }
  return validationFieldErrors(error, FIELD_ALIASES);
}
