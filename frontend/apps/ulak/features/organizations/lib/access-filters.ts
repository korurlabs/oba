import { parseAsString, parseAsStringLiteral } from "nuqs/server";
import type { Values } from "nuqs";
import type { AppGrant, MemberAppAccess } from "@oba/shared/features/organizations/schemas";
import { isSearching, matchesSearch } from "@/lib/list-search";

/** The Access page's tabs, in the order they are shown: the grants, and what each member ends up with. */
export const ACCESS_TABS = ["grants", "members"] as const;
export type AccessTabValue = (typeof ACCESS_TABS)[number];

export function isAccessTab(value: string): value is AccessTabValue {
  return (ACCESS_TABS as readonly string[]).includes(value);
}

/**
 * The Access page's tab and search in the URL, so a tab and a narrowed list
 * can be reloaded and shared. One search serves both tabs: on Grants it
 * matches the grant's member or group, on Members the member's name.
 */
export const accessFilterParams = {
  tab: parseAsStringLiteral(ACCESS_TABS).withDefault("grants"),
  search: parseAsString,
};

export type AccessFilterParams = Values<typeof accessFilterParams>;

/** The grants whose member or group matches the search. */
export function filterGrants(grants: readonly AppGrant[], search: string | null): AppGrant[] {
  return grants.filter((grant) => matchesSearch(search, [grant.userFullName, grant.groupName]));
}

/** The members whose name matches the search. */
export function filterMemberAccess(
  members: readonly MemberAppAccess[],
  search: string | null
): MemberAppAccess[] {
  return members.filter((member) => matchesSearch(search, [member.fullName]));
}

/** True when the search narrows the open tab's list. */
export function hasActiveAccessFilter(filters: AccessFilterParams): boolean {
  return isSearching(filters.search);
}
