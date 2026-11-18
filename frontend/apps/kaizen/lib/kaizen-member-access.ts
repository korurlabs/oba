import { isApiError } from "@oba/shared/api/axios";

/**
 * The 422 refusal of a write that attaches a member who cannot open Kaizen in
 * the organization: a board member, a ticket assignee or reviewer, a private
 * channel member, a chat mention or a pull request reviewer.
 */
export const MEMBER_WITHOUT_KAIZEN_ACCESS_CODE = "APP_VAL_002";

/**
 * True when the member lists of the API mark this member as able to open
 * Kaizen. A response without the flag counts as no access, as a missing app
 * list of the current user does, so a picker never offers a member the
 * server may refuse.
 */
export function hasKaizenAccess(member: { kaizenAccess?: boolean }): boolean {
  return member.kaizenAccess === true;
}

/** True when the server refused a write because a picked member has no Kaizen access. */
export function isMemberWithoutKaizenAccessError(error: unknown): boolean {
  return isApiError(error, MEMBER_WITHOUT_KAIZEN_ACCESS_CODE);
}
