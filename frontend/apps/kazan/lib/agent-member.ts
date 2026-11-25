import { isApiError } from "@oba/shared/api/axios";
import type { UserKind } from "@oba/shared/features/users/schema";

/**
 * The 422 refusal of a write that targets an agent where only a person fits:
 * a board role other than MEMBER, or an agent as a ticket reviewer.
 */
export const AGENT_TARGET_REFUSED_CODE = "AGT_VAL_001";

/**
 * The 403 refusal of an action an agent may not take as the caller, such as
 * closing or deleting a ticket. A person in the web app does not meet it,
 * but an agent session signed in to Kazan does.
 */
export const AGENT_ACTION_REFUSED_CODE = "AGT_SEC_002";

/** The refused action the server names in the error values of both refusals. */
const ACTION_ERROR_VALUE = "action";

/** Refused actions that have their own words; any other gets the generic text of its code. */
const ACTION_MESSAGE_KEYS: ReadonlyMap<string, string> = new Map([
  ["BOARD_ROLE", "agents.refusals.boardRole"],
  ["TICKET_REVIEWER", "agents.refusals.ticketReviewer"],
  ["TICKET_DONE", "agents.refusals.ticketDone"],
  ["TICKET_DELETE", "agents.refusals.ticketDelete"],
]);

/** True for a member, candidate or assignee of kind AGENT. */
export function isAgent(member: { kind?: UserKind | null }): boolean {
  return member.kind === "AGENT";
}

/**
 * The `kazan` translation key that explains an agent refusal, or null when
 * the error is not one. The caller shows the server's message for null.
 */
export function agentRefusalMessageKey(error: unknown): string | null {
  if (!isApiError(error)) return null;
  const isTargetRefusal = error.code === AGENT_TARGET_REFUSED_CODE;
  if (!isTargetRefusal && error.code !== AGENT_ACTION_REFUSED_CODE) return null;

  const action = error.errorValues[ACTION_ERROR_VALUE];
  const actionKey = typeof action === "string" ? ACTION_MESSAGE_KEYS.get(action) : undefined;
  if (actionKey) return actionKey;
  return isTargetRefusal ? "agents.refusals.targetRefused" : "agents.refusals.actionRefused";
}
