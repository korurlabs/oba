"use client";

import { useTranslations } from "next-intl";
import type { FilterPopoverLabels } from "@oba/ui/sky/filter-popover";
import type { ListEmptyStateLabels } from "@oba/ui/sky/page-states";

/** The texts of the sky filter popover, the same on every Ulak list. */
export function useFilterPopoverLabels(): FilterPopoverLabels {
  const t = useTranslations("filterPopover");
  return {
    trigger: t("trigger"),
    chooseCategory: t("chooseCategory"),
    searchOptions: t("searchOptions"),
    noOptions: t("noOptions"),
    loading: t("loading"),
    clear: t("clear"),
  };
}

/** The accessible name of the clear button in every list search. */
export function useClearSearchLabel(): string {
  return useTranslations("list")("clearSearch");
}

/**
 * The texts of a list's empty state: the list's own title and description
 * when it holds nothing, its own title when the search or filters hide every
 * row, and the shared hint and clear button for that case.
 */
export function useListEmptyLabels({
  emptyTitle,
  emptyDescription,
  filteredTitle,
}: {
  emptyTitle: string;
  emptyDescription: string;
  filteredTitle: string;
}): ListEmptyStateLabels {
  const t = useTranslations("list");
  return {
    emptyTitle,
    emptyDescription,
    filteredTitle,
    filteredDescription: t("filteredDescription"),
    clearFilters: t("clearFilters"),
  };
}
