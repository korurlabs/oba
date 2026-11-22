import { apiErrorText } from "@/lib/api-error-text";

/**
 * Refusals of the page endpoints that have a text of their own, as keys of
 * the `pages` messages.
 */
const ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  KLK_SEC_001: "errors.levelTooLow",
  KLK_SEC_002: "errors.attachmentKept",
  KLK_NOT_001: "errors.notFound",
  KLK_STA_001: "errors.slugTaken",
  KLK_STA_002: "errors.versionOutdated",
  KLK_STA_003: "errors.parentDeleted",
  KLK_VAL_002: "errors.subjectNotActive",
  KLK_VAL_003: "errors.guestAboveView",
  KLK_VAL_004: "errors.moveInvalid",
  KLK_VAL_005: "errors.duplicateLocale",
  KLK_VAL_006: "errors.permissionOnChild",
  KLK_VAL_007: "errors.reservedSlug",
  KLK_VAL_008: "errors.notDeleted",
  GEN_VAL_001: "errors.validation",
};

/**
 * The text a failed page request shows: a translated text for the refusals
 * the user can act on, else the server's message, else `fallbackKey`. `t` is
 * the `pages` translator.
 */
export function pageErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string
): string {
  return apiErrorText(error, t, fallbackKey, ERROR_TEXT_KEYS);
}
