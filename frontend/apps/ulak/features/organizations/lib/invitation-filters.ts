import { parseAsString, parseAsStringEnum } from "nuqs/server";
import type { Values } from "nuqs";
import {
  InvitationStatusSchema,
  type Invitation,
} from "@oba/shared/features/organizations/schemas";
import { isSearching, matchesSearch } from "@/lib/list-search";

/** The Invitations list's search and status in the URL, so a narrowed list can be reloaded and shared. */
export const invitationFilterParams = {
  search: parseAsString,
  status: parseAsStringEnum(InvitationStatusSchema.options),
};

export type InvitationFilterParams = Values<typeof invitationFilterParams>;

/** The search and every filter cleared. */
export const CLEARED_INVITATION_FILTERS = { search: null, status: null };

/** The filters of the popover cleared; the search stays. */
export const CLEARED_INVITATION_POPOVER_FILTERS = { status: null };

/** The invitations that match the search on email and the status. */
export function filterInvitations(
  invitations: readonly Invitation[],
  filters: InvitationFilterParams
): Invitation[] {
  return invitations.filter(
    (invitation) =>
      (!filters.status || invitation.status === filters.status) &&
      matchesSearch(filters.search, [invitation.email])
  );
}

/** True when the search or the status narrows the list. */
export function hasActiveInvitationFilter(filters: InvitationFilterParams): boolean {
  return isSearching(filters.search) || !!filters.status;
}
