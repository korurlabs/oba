import { parseAsString, parseAsStringEnum } from "nuqs/server";
import type { Values } from "nuqs";
import {
  OrganizationMemberStatusSchema,
  OrganizationRoleSchema,
  type OrganizationMember,
} from "@oba/shared/features/organizations/schemas";
import { isSearching, matchesSearch } from "@/lib/list-search";

/** The Humans list's search, role and status in the URL, so a narrowed list can be reloaded and shared. */
export const memberFilterParams = {
  search: parseAsString,
  role: parseAsStringEnum(OrganizationRoleSchema.options),
  status: parseAsStringEnum(OrganizationMemberStatusSchema.options),
};

export type MemberFilterParams = Values<typeof memberFilterParams>;

/** The search and every filter cleared. */
export const CLEARED_MEMBER_FILTERS = { search: null, role: null, status: null };

/** The filters of the popover cleared; the search stays. */
export const CLEARED_MEMBER_POPOVER_FILTERS = { role: null, status: null };

/** The members that match the search on name or email, the role and the status. */
export function filterMembers(
  members: readonly OrganizationMember[],
  filters: MemberFilterParams
): OrganizationMember[] {
  return members.filter(
    (member) =>
      (!filters.role || member.role === filters.role) &&
      (!filters.status || member.status === filters.status) &&
      matchesSearch(filters.search, [`${member.firstName} ${member.lastName}`, member.email])
  );
}

/** True when the search or a filter narrows the list. */
export function hasActiveMemberFilter(filters: MemberFilterParams): boolean {
  return isSearching(filters.search) || !!filters.role || !!filters.status;
}
