import { useTranslations } from "next-intl";
import type { FilterPopoverLabels } from "@oba/ui/sky/filter-popover";

/** The texts of every Otak list's filter popover, the same on each list. */
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

/** The name of the clear button in every Otak search field, the same on each list. */
export function useSearchClearLabel(): string {
  const t = useTranslations("searchInput");
  return t("clear");
}
