import { apiErrorText, validationFieldErrors } from "@/lib/api-error-text";

/** Refusals of the contact endpoints that have a text of their own. */
const ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  OTK_NOT_001: "errors.relationNotFound",
  OTK_NOT_002: "errors.notFound",
  OTK_VAL_005: "errors.emailTypeWithoutEmail",
  OTK_VAL_013: "errors.mainFlagNotForKind",
  ORG_SEC_003: "errors.forbidden",
  GEN_VAL_001: "errors.validation",
};

/** The text a failed contact request shows. `t` is the `contacts` translator. */
export function contactErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string
): string {
  return apiErrorText(error, t, fallbackKey, ERROR_TEXT_KEYS);
}

/** The form fields a refused save names (the `errorValues` of a 422). */
export function contactFieldErrors(error: unknown): Record<string, string> {
  return validationFieldErrors(error);
}
