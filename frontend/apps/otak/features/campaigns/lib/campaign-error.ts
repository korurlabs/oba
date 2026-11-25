import { isApiError } from "@oba/shared/api/axios";
import { apiErrorText, validationFieldErrors } from "@/lib/api-error-text";

/** The refusal of a new owner who is an active member without Otak access. */
const OWNER_WITHOUT_OTAK_ACCESS_CODE = "APP_VAL_002";

/** Refusals of the campaign endpoints that have a text of their own. */
const ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  OTK_NOT_012: "errors.notFound",
  OTK_NOT_013: "errors.contentNotFound",
  OTK_NOT_009: "errors.eventNotFound",
  OTK_NOT_004: "errors.icpNotFound",
  OTK_STA_015: "errors.nameTaken",
  OTK_STA_001: "errors.ownerNotMember",
  [OWNER_WITHOUT_OTAK_ACCESS_CODE]: "errors.ownerWithoutOtakAccess",
  OTK_VAL_016: "errors.statusDates",
  OTK_VAL_017: "errors.dateRange",
  LGE_NOT_001: "errors.legalEntityNotFound",
  STG_VAL_005: "errors.imageDuplicate",
  ORG_SEC_003: "errors.forbidden",
  GEN_VAL_001: "errors.validation",
};

/** The fields each refusal names on the form. */
const ERROR_FIELDS: Partial<Record<string, string>> = {
  OTK_STA_015: "name",
  OTK_STA_001: "ownerUserId",
  [OWNER_WITHOUT_OTAK_ACCESS_CODE]: "ownerUserId",
  OTK_NOT_009: "eventId",
  OTK_NOT_004: "icpId",
  OTK_VAL_016: "status",
  OTK_VAL_017: "endDate",
  LGE_NOT_001: "legalEntityId",
};

/**
 * The text a failed campaign request shows: a translated text for the
 * refusals the user can act on, else the server's message, else
 * `fallbackKey`. `t` is the `campaigns` translator.
 */
export function campaignErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string
): string {
  return apiErrorText(error, t, fallbackKey, ERROR_TEXT_KEYS);
}

/**
 * The form fields a refused save names, with the message for each: the name
 * for a name another campaign has, the owner, event or ICP the server
 * refused, the end day before the start day, the legal entity of a content,
 * the status for moments that break its rules, else the `errorValues` of a
 * 422. A form shows only the fields it has. `t` is the `campaigns` translator.
 */
export function campaignFieldErrors(
  error: unknown,
  t: (key: string) => string
): Record<string, string> {
  if (isApiError(error) && error.code !== undefined) {
    const field = ERROR_FIELDS[error.code];
    const textKey = ERROR_TEXT_KEYS[error.code];
    if (field !== undefined && textKey !== undefined) {
      return { [field]: t(textKey) };
    }
  }
  return validationFieldErrors(error);
}
