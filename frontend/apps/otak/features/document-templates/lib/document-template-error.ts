import { isApiError } from "@oba/shared/api/axios";
import { apiErrorText, validationFieldErrors } from "@/lib/api-error-text";

/** Refusals of the document template endpoints that have a text of their own. */
const ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  OTK_NOT_007: "errors.notFound",
  OTK_STA_009: "errors.nameTaken",
  OTK_VAL_009: "errors.fileNotHtmlOrPdf",
  AZR_UPS_001: "errors.uploadFailed",
  ORG_SEC_003: "errors.forbidden",
  GEN_VAL_001: "errors.validation",
};

/** Refusals of a template copied onto a mail or a relation as an attachment. */
const ATTACHMENT_ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  OTK_NOT_007: "errors.notFound",
  OTK_NOT_011: "attach.errors.mailNotFound",
  OTK_NOT_001: "attach.errors.relationNotFound",
  OTK_VAL_012: "attach.errors.notUsable",
  OTK_STA_011: "attach.errors.mailSent",
  STG_VAL_005: "attach.errors.duplicate",
  APP_SEC_002: "errors.forbidden",
  ORG_SEC_003: "errors.forbidden",
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
 * The text of a template that could not be attached to a mail or a
 * relation. `t` is the `documentTemplates` translator.
 */
export function documentTemplateAttachmentErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string
): string {
  return apiErrorText(error, t, fallbackKey, ATTACHMENT_ERROR_TEXT_KEYS);
}

/**
 * The form fields a refused save or upload names, with the message for
 * each: the name for a name the organization already has in this language,
 * the file part the server did not take as HTML or PDF, else the
 * `errorValues` of a 422. `t` is the `documentTemplates` translator.
 */
export function documentTemplateFieldErrors(
  error: unknown,
  t: (key: string) => string
): Record<string, string> {
  if (isApiError(error, "OTK_STA_009")) {
    return { name: t("errors.nameTaken") };
  }
  if (isApiError(error, "OTK_VAL_009")) {
    const text = documentTemplateErrorText(error, t, "toast.saveError");
    return Object.fromEntries(
      Object.keys(error.errorValues).map((field) => [FIELD_ALIASES[field] ?? field, text])
    );
  }
  return validationFieldErrors(error, FIELD_ALIASES);
}
