import { parseAsString } from "nuqs/server";
import type { Values } from "nuqs";
import type { OrganizationGroup } from "@oba/shared/features/organizations/schemas";
import { isSearching, matchesSearch } from "@/lib/list-search";

/** The Groups list's search in the URL, so a narrowed list can be reloaded and shared. */
export const groupFilterParams = {
  search: parseAsString,
};

export type GroupFilterParams = Values<typeof groupFilterParams>;

/** The search cleared. */
export const CLEARED_GROUP_FILTERS = { search: null };

/** The groups that match the search on name or description. */
export function filterGroups(
  groups: readonly OrganizationGroup[],
  filters: GroupFilterParams
): OrganizationGroup[] {
  return groups.filter((group) => matchesSearch(filters.search, [group.name, group.description]));
}

/** True when the search narrows the list. */
export function hasActiveGroupFilter(filters: GroupFilterParams): boolean {
  return isSearching(filters.search);
}
