import { parseAsString } from "nuqs/server";
import type { Values } from "nuqs";
import type { LegalEntityListItem } from "../schemas";
import { isSearching, matchesSearch } from "@/lib/list-search";

/** The Legal entities list's search in the URL, so a narrowed list can be reloaded and shared. */
export const legalEntityFilterParams = {
  search: parseAsString,
};

export type LegalEntityFilterParams = Values<typeof legalEntityFilterParams>;

/** The search cleared. */
export const CLEARED_LEGAL_ENTITY_FILTERS = { search: null };

/** The legal entities that match the search on short name, legal name or registration number. */
export function filterLegalEntities(
  entities: readonly LegalEntityListItem[],
  filters: LegalEntityFilterParams
): LegalEntityListItem[] {
  return entities.filter((entity) =>
    matchesSearch(filters.search, [entity.shortName, entity.legalName, entity.registrationNumber])
  );
}

/** True when the search narrows the list. */
export function hasActiveLegalEntityFilter(filters: LegalEntityFilterParams): boolean {
  return isSearching(filters.search);
}
