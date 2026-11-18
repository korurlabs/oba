import { isApiError } from "@oba/shared/api/axios";
import { apiErrorText, validationFieldErrors } from "@/lib/api-error-text";

/** Refusals of the mail template endpoints that have a text of their own. */
const ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  OTK_NOT_010: "errors.notFound",
  OTK_STA_014: "errors.nameTaken",
  ORG_SEC_003: "errors.forbidden",
  GEN_VAL_001: "errors.validation",
};

/**
 * The text a failed mail template request shows: a translated text for the
 * refusals the user can act on, else the server's message, else
 * `fallbackKey`. `t` is the `mailTemplates` translator.
 */
export function mailTemplateErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string
): string {
  return apiErrorText(error, t, fallbackKey, ERROR_TEXT_KEYS);
}

/**
 * The form fields a refused save names, with the message for each: the name
 * for a name the organization already has in this language, else the
 * `errorValues` of a 422. `t` is the `mailTemplates` translator.
 */
export function mailTemplateFieldErrors(
  error: unknown,
  t: (key: string) => string
): Record<string, string> {
  if (isApiError(error, "OTK_STA_014")) {
    return { name: t("errors.nameTaken") };
  }
  return validationFieldErrors(error);
}
