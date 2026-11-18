import { isApiError } from "@oba/shared/api/axios";
import { apiErrorText, validationFieldErrors } from "@/lib/api-error-text";

/** Refusals of the event endpoints that have a text of their own. */
const ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  OTK_NOT_009: "errors.notFound",
  OTK_STA_013: "errors.nameTaken",
  OTK_VAL_010: "errors.dateRange",
  OTK_VAL_011: "errors.periodWithoutToday",
  ORG_SEC_003: "errors.forbidden",
  GEN_VAL_001: "errors.validation",
};

/**
 * The text a failed event request shows: a translated text for the refusals
 * the user can act on, else the server's message, else `fallbackKey`. `t` is
 * the `events` translator.
 */
export function eventErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string
): string {
  return apiErrorText(error, t, fallbackKey, ERROR_TEXT_KEYS);
}

/**
 * The form fields a refused save names, with the message for each: the name
 * for a name another event has, the end date for an end before the start,
 * else the `errorValues` of a 422. `t` is the `events` translator.
 */
export function eventFieldErrors(
  error: unknown,
  t: (key: string) => string
): Record<string, string> {
  if (isApiError(error, "OTK_STA_013")) {
    return { name: t("errors.nameTaken") };
  }
  if (isApiError(error, "OTK_VAL_010")) {
    return { endDate: t("errors.dateRange") };
  }
  return validationFieldErrors(error);
}
