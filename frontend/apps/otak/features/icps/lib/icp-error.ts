import { isApiError } from "@oba/shared/api/axios";
import { apiErrorText, validationFieldErrors } from "@/lib/api-error-text";

/** Refusals of the ICP endpoints that have a text of their own. */
const ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  OTK_NOT_004: "errors.notFound",
  OTK_STA_005: "errors.nameTaken",
  OTK_VAL_001: "errors.sizeRange",
  ORG_SEC_003: "errors.forbidden",
  GEN_VAL_001: "errors.validation",
};

/**
 * The text a failed ICP request shows: a translated text for the refusals
 * the user can act on, else the server's message, else `fallbackKey`. `t` is
 * the `icps` translator.
 */
export function icpErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string
): string {
  return apiErrorText(error, t, fallbackKey, ERROR_TEXT_KEYS);
}

/**
 * The form fields a refused save names, with the message for each: the name
 * for a name another ICP has, the maximum size for an inverted size range
 * (where the form shows its own range check), else the `errorValues` of a 422.
 * `t` is the `icps` translator.
 */
export function icpFieldErrors(error: unknown, t: (key: string) => string): Record<string, string> {
  if (isApiError(error, "OTK_STA_005")) {
    return { name: t("errors.nameTaken") };
  }
  if (isApiError(error, "OTK_VAL_001")) {
    return { companySizeMax: t("errors.sizeRange") };
  }
  return validationFieldErrors(error);
}
