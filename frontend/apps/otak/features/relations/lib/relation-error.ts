import { isApiError } from "@oba/shared/api/axios";
import { apiErrorText, validationFieldErrors } from "@/lib/api-error-text";

/**
 * The refusal of a new owner who is an active member without Otak access:
 * only a member with Otak may own a relation.
 */
export const OWNER_WITHOUT_OTAK_ACCESS_CODE = "APP_VAL_002";

/** Refusals of the relation endpoints that have a text of their own. */
const ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  OTK_NOT_001: "errors.notFound",
  OTK_STA_001: "errors.ownerNotMember",
  [OWNER_WITHOUT_OTAK_ACCESS_CODE]: "errors.ownerWithoutOtakAccess",
  OTK_STA_002: "errors.notLead",
  OTK_STA_003: "errors.typeUnchanged",
  OTK_STA_006: "errors.stageNotForType",
  OTK_NOT_004: "errors.icpNotFound",
  OTK_NOT_009: "errors.eventNotFound",
  OTK_NOT_012: "errors.campaignNotFound",
  ORG_SEC_003: "errors.forbidden",
  GEN_VAL_001: "errors.validation",
};

/**
 * The text a failed relation request shows: a translated text for the
 * refusals the user can act on, else the server's message, else `fallbackKey`.
 * `t` is the `relations` translator.
 */
export function relationErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string
): string {
  return apiErrorText(error, t, fallbackKey, ERROR_TEXT_KEYS);
}

/**
 * The form fields a refused save names, with the server's message for each:
 * the `errorValues` of a 422, the owner for `OTK_STA_001` and for an owner
 * without Otak access, the ICP for `OTK_NOT_004`, the event for
 * `OTK_NOT_009`, or the campaign for `OTK_NOT_012`. Empty for any other refusal. `t` is the `relations`
 * translator.
 */
export function relationFieldErrors(
  error: unknown,
  t: (key: string) => string
): Record<string, string> {
  if (isApiError(error, "OTK_STA_001")) {
    return { ownerUserId: t("errors.ownerNotMember") };
  }
  if (isApiError(error, OWNER_WITHOUT_OTAK_ACCESS_CODE)) {
    return { ownerUserId: t("errors.ownerWithoutOtakAccess") };
  }
  if (isApiError(error, "OTK_NOT_004")) {
    return { icpId: t("errors.icpNotFound") };
  }
  if (isApiError(error, "OTK_NOT_009")) {
    return { eventId: t("errors.eventNotFound") };
  }
  if (isApiError(error, "OTK_NOT_012")) {
    return { campaignId: t("errors.campaignNotFound") };
  }
  return validationFieldErrors(error);
}
