import { isApiError } from "@oba/shared/api/axios";
import { apiErrorText, validationFieldErrors } from "@/lib/api-error-text";

/** Refusals of the outreach endpoints that have a text of their own. */
const ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  OTK_NOT_001: "errors.relationNotFound",
  OTK_NOT_002: "errors.contactNotFound",
  OTK_NOT_005: "errors.notFound",
  OTK_NOT_006: "errors.senderNotFound",
  OTK_NOT_010: "errors.templateNotFound",
  OTK_NOT_012: "errors.campaignNotFound",
  OTK_STA_011: "errors.notEditable",
  OTK_VAL_004: "errors.sharedMailboxNotFound",
  OTK_VAL_007: "errors.contactWithoutAddress",
  OTK_VAL_008: "errors.attachmentsTooLarge",
  OTK_VAL_014: "errors.actionNotForType",
  OTK_VAL_015: "errors.contactWithoutPhone",
  STG_VAL_005: "errors.attachmentDuplicate",
  NTF_UPS_001: "errors.sendFailed",
  AZR_UPS_002: "errors.storageFailed",
  ORG_SEC_003: "errors.forbidden",
  GEN_VAL_001: "errors.validation",
};

/**
 * The server names a nested mail field by its path; the forms show the
 * subject, body and language at the top level.
 */
const FIELD_ALIASES: Partial<Record<string, string>> = {
  "mail.subject": "subject",
  "mail.body": "body",
  "mail.language": "language",
};

/**
 * The text a failed outreach request shows: a translated text for the
 * refusals the user can act on, else the server's message, else
 * `fallbackKey`. `t` is the `outreaches` translator.
 */
export function outreachErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string
): string {
  return apiErrorText(error, t, fallbackKey, ERROR_TEXT_KEYS);
}

/**
 * The form fields a refused save names, with the message for each: the
 * campaign for `OTK_NOT_012`, else the `errorValues` of a 422. `t` is the
 * `outreaches` translator.
 */
export function outreachFieldErrors(
  error: unknown,
  t: (key: string) => string
): Record<string, string> {
  if (isApiError(error, "OTK_NOT_012")) {
    return { campaignId: t("errors.campaignNotFound") };
  }
  return validationFieldErrors(error, FIELD_ALIASES);
}
