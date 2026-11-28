"use client";

import type { FilterOption, FilterPopoverLabels } from "@oba/ui/sky/filter-popover";
import { TablePagination } from "@oba/ui/sky/table-pagination";

/** The texts of every Filter popover in Kaya, which is English only. */
export const FILTER_LABELS: FilterPopoverLabels = {
  trigger: "Filter",
  chooseCategory: "Select a field",
  searchOptions: "Search options",
  noOptions: "No options",
  loading: "Loading",
  clear: "Clear all",
};

/** The name of the clear button of every list search. */
export const CLEAR_SEARCH_LABEL = "Clear search";

/** The button of a filtered empty list that clears the search and every filter. */
export const CLEAR_FILTERS_LABEL = "Clear filters";

/**
 * The options of a filter category, with the chosen value kept among them
 * when the list no longer offers it, as after a shared link to a machine
 * that has since gone: its row stays ticked, so it can be cleared.
 */
export function withChosen(options: FilterOption[], value: string | null): FilterOption[] {
  if (value === null) return options;
  for (const option of options) {
    if (option.value === value) return options;
  }
  return [...options, { value, label: value }];
}

/** The page fields of a paged Kaya answer. */
export type ListPage = {
  currentPage: number;
  totalPages: number;
  totalElements: number;
};

/**
 * The paging row below a paged list, shown only when there is more than one
 * page. Kaya's lists run newest first, so the way back is "Newer".
 */
export function ListPagination({
  pagination,
  noun,
  onPageChange,
}: {
  pagination: ListPage | null | undefined;
  /** The plural of what the list holds, such as "sessions". */
  noun: string;
  onPageChange: (page: number) => void;
}) {
  if (!pagination || pagination.totalPages <= 1) return null;
  return (
    <TablePagination
      page={pagination.currentPage}
      totalPages={pagination.totalPages}
      onPageChange={onPageChange}
      label="Pages"
      previousLabel="Newer"
      nextLabel="Older"
      summary={`Page ${pagination.currentPage} of ${pagination.totalPages} · ${pagination.totalElements} ${noun}`}
    />
  );
}
