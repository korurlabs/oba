import { isApiError } from "@oba/shared/api/axios";
import { apiErrorText, validationFieldErrors } from "@/lib/api-error-text";

/** Refusals of the document template endpoints that have a text of their own. */
const ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  DOC_NOT_001: "errors.notFound",
  DOC_STA_001: "errors.nameTaken",
  DOC_VAL_001: "errors.fileNotHtmlOrPdf",
  LGE_NOT_001: "errors.legalEntityNotFound",
  AZR_UPS_001: "errors.uploadFailed",
  ORG_SEC_003: "errors.forbidden",
  GEN_VAL_001: "errors.validation",
};

/**
 * The server's names of the request properties and file parts, mapped to
 * the form fields that show them: the multipart parts are `html` and `pdf`.
 */
const FIELD_ALIASES: Partial<Record<string, string>> = {
  html: "htmlFile",
  pdf: "pdfFile",
};

/**
 * The text a failed template request shows: a translated text for the
 * refusals the user can act on, else the server's message, else
 * `fallbackKey`. `t` is the `documentTemplates` translator.
 */
export function documentTemplateErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string
): string {
  return apiErrorText(error, t, fallbackKey, ERROR_TEXT_KEYS);
}

/**
 * The form fields a refused save or upload names, with the message for
 * each: the name for a name the organization already has in this language, the
 * legal entity for one that no longer exists,
 * the file part the server did not take as HTML or PDF, else the
 * `errorValues` of a 422. `t` is the `documentTemplates` translator.
 */
export function documentTemplateFieldErrors(
  error: unknown,
  t: (key: string) => string
): Record<string, string> {
  if (isApiError(error, "DOC_STA_001")) {
    return { name: t("errors.nameTaken") };
  }
  if (isApiError(error, "LGE_NOT_001")) {
    return { legalEntityId: t("errors.legalEntityNotFound") };
  }
  if (isApiError(error, "DOC_VAL_001")) {
    const text = documentTemplateErrorText(error, t, "toast.saveError");
    return Object.fromEntries(
      Object.keys(error.errorValues).map((field) => [FIELD_ALIASES[field] ?? field, text])
    );
  }
  return validationFieldErrors(error, FIELD_ALIASES);
}
