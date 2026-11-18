import type { TranslationKey } from "@/i18n";
import { AUTH_REFRESH_TRANSIENT, type ApiError, type ApiErrorEvent } from "@/lib/api";
import { MEMBER_WITHOUT_KAZAN_ACCESS_CODE } from "@/lib/kazanAccess";
import { BOARD_PREFIX_TAKEN_CODE } from "@/lib/schemas/boards";
import {
  INCIDENT_CLOSE_REQUIRES_RCA_CODE,
  TICKET_BACKLOG_STATUS_CODE,
} from "@/lib/schemas/tickets";

type Translate = (key: TranslationKey) => string;

/**
 * Refusals that a form shows under one of its fields, as it shows a
 * validation error: a board key in use belongs under New board's Key field.
 */
const FIELD_ERROR_CODES: ReadonlySet<string> = new Set([BOARD_PREFIX_TAKEN_CODE]);

/**
 * Rules the app words itself, so the refusal reads in the user's language
 * instead of the server's English: two ticket rules, and a write that attaches
 * a member without Kazan (a board member add, a ticket assignee, a pull
 * request reviewer, a mention in an edited chat message).
 */
const RULE_MESSAGE_KEYS: Readonly<Record<string, TranslationKey>> = {
  [TICKET_BACKLOG_STATUS_CODE]: "ticketBacklogStatusNotAllowed",
  [INCIDENT_CLOSE_REQUIRES_RCA_CODE]: "ticketIncidentCloseRequiresRca",
  [MEMBER_WITHOUT_KAZAN_ACCESS_CODE]: "memberAccessRefused",
};

/**
 * True for a refusal by a validation rule (HTTP 422): the same request sent
 * again is refused the same way, so its toast offers no Retry.
 */
export function isRuleRefusal(error: ApiError): boolean {
  return error.status === 422;
}

/** Methods that only read. Their failures belong to the screen that loads the data. */
const READ_METHODS: readonly string[] = ["get", "head", "options"];

/**
 * The toast text for a failed request, or null when the toast layer stays
 * quiet because another surface owns the failure:
 *
 * - a validation error (422 `GEN_VAL_001`) and a refusal in
 *   `FIELD_ERROR_CODES` are shown under the form fields;
 * - a failed read is the loading screen's error state ("Couldn't load ...",
 *   "Try again"), so a toast on top would say the same thing twice.
 *
 * Every other failure is a write that did not happen, and the user hears about
 * it here. A rule in `RULE_MESSAGE_KEYS` is shown in the app's words.
 * Any other backend error that carries a code also carries a message written
 * for people (a business rule such as "You already posted today"), so that
 * message is shown; without one, the text follows the kind of failure.
 */
export function apiErrorToastMessage(event: ApiErrorEvent, t: Translate): string | null {
  const { error } = event;
  if (error.kind === "validation") return null;
  if (error.code !== undefined && FIELD_ERROR_CODES.has(error.code)) return null;
  const method: string = (event.method ?? "get").toLowerCase();
  if (READ_METHODS.includes(method)) return null;

  if (error.code === AUTH_REFRESH_TRANSIENT) return t("apiErrorSessionRefresh");

  switch (error.kind) {
    case "network":
      return t("apiErrorNetwork");
    case "server":
      return t("apiErrorServer");
    case "rate-limited":
      return t("apiErrorRateLimited");
    default:
      break;
  }

  const ruleKey: TranslationKey | undefined =
    error.code !== undefined ? RULE_MESSAGE_KEYS[error.code] : undefined;
  if (ruleKey) return t(ruleKey);
  if (error.code !== undefined && error.message.trim() !== "") return error.message;
  if (error.kind === "forbidden") return t("apiErrorForbidden");
  if (error.kind === "not-found") return t("apiErrorNotFound");
  return t("apiErrorGeneric");
}
