import {
  createLoader,
  parseAsInteger,
  parseAsString,
  parseAsStringEnum,
} from "nuqs/server";
import type { Values } from "nuqs";

export const filtersSearchParams = {
  search: parseAsString,
  page: parseAsInteger.withOptions({ scroll: true }).withDefault(1),
  amount: parseAsInteger.withDefault(10),
  sortBy: parseAsString.withDefault("createdAt"),
  sortDirection: parseAsStringEnum(["asc", "desc"]).withDefault("desc"),
};

export type FiltersSearchParams = Values<typeof filtersSearchParams>;
export const loadSearchParams = createLoader(filtersSearchParams);
