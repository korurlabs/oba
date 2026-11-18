import { isApiError } from "@oba/shared/api/axios";
import { apiErrorText, validationFieldErrors } from "@/lib/api-error-text";

/** Refusals of the mail sender endpoints that have a text of their own. */
const ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  OTK_NOT_006: "errors.notFound",
  OTK_STA_008: "errors.mailAddressTaken",
  OTK_VAL_002: "errors.accountNotInTenant",
  OTK_VAL_003: "errors.accountWithoutMailbox",
  OTK_VAL_004: "errors.sharedMailboxNotFound",
  NTF_UPS_001: "errors.microsoftUnreachable",
  ORG_SEC_003: "errors.forbidden",
  GEN_VAL_001: "errors.validation",
};

/** Refusals that are about the address itself, so the form shows them on its field. */
const MAIL_ADDRESS_ERROR_CODES = ["OTK_STA_008", "OTK_VAL_002", "OTK_VAL_003", "OTK_VAL_004"] as const;

/**
 * The text a failed mail sender request shows: a translated text for the
 * refusals the user can act on, else the server's message, else
 * `fallbackKey`. `t` is the `mailSenders` translator.
 */
export function mailSenderErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string
): string {
  return apiErrorText(error, t, fallbackKey, ERROR_TEXT_KEYS);
}

/**
 * The form fields a refused add names, with the message for each: the mail
 * address for an address another sender has, that names no account in the
 * tenant, whose account has no mailbox or that names no shared mailbox, else
 * the `errorValues` of a 422.
 * `t` is the `mailSenders` translator.
 */
export function mailSenderFieldErrors(
  error: unknown,
  t: (key: string) => string
): Record<string, string> {
  for (const code of MAIL_ADDRESS_ERROR_CODES) {
    if (isApiError(error, code)) {
      return { mailAddress: mailSenderErrorText(error, t, "toast.saveError") };
    }
  }
  return validationFieldErrors(error);
}
