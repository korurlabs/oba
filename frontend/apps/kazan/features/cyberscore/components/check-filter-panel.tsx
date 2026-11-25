"use client";

import { useTranslations } from "next-intl";
import { Check } from "lucide-react";
import { ChatChannelFilterPopover } from "@/features/chat/components/chat-channel-filter-popover";
import type { ChatChannelFilterCategory } from "@/features/chat/components/chat-channel-filter-popover";
import { cn } from "@/lib/utils";
import {
  UNCATEGORIZED,
  countCheckFilters,
  useCheckFilterParams,
  type CheckCategoryFilter,
} from "../hooks/use-check-filter-params";
import { CHECK_CATEGORIES, CHECK_STATUSES, type CheckStatus } from "../schemas";

/** The fields of the panel. */
type CheckFilterField = "category" | "status";

const CATEGORY_OPTIONS: readonly CheckCategoryFilter[] = [...CHECK_CATEGORIES, UNCATEGORIZED];

const OPTION_ROW_CLASS =
  "flex w-full items-center gap-2.5 rounded-md px-2 py-1.5 text-left text-sm transition-colors";

/**
 * The Category and Status filters of the checks page, behind a funnel badged
 * with the number of the two that narrow the list. Each takes one value, as
 * the URL keys `category` and `status` hold one; choosing the chosen value
 * again removes it. The state is the URL's, so the overview's category links,
 * a reload and a shared link all open the same list.
 */
export function CheckFilterPanel() {
  const t = useTranslations("cyberscore");
  const [filters, setFilters] = useCheckFilterParams();
  const { category, status } = filters;

  const toggleCategory = (value: CheckCategoryFilter) =>
    void setFilters({ category: category === value ? null : value });
  const toggleStatus = (value: CheckStatus) =>
    void setFilters({ status: status === value ? null : value });
  const clearAll = () => void setFilters({ category: null, status: null });

  const categories: (ChatChannelFilterCategory & { key: CheckFilterField })[] = [
    { key: "category", label: t("checks.filter.category"), count: category ? 1 : 0 },
    { key: "status", label: t("checks.filter.status"), count: status ? 1 : 0 },
  ];

  const renderOptions = (key: string) => (
    <div className="flex-1 overflow-y-auto px-3 py-2">
      {key === "category" ? (
        <FilterOptions
          options={CATEGORY_OPTIONS}
          chosen={category}
          label={(value) => t(`categories.${value}`)}
          onToggle={toggleCategory}
        />
      ) : (
        <FilterOptions
          options={CHECK_STATUSES}
          chosen={status}
          label={(value) => t(`statuses.${value}`)}
          onToggle={toggleStatus}
        />
      )}
    </div>
  );

  return (
    <ChatChannelFilterPopover
      activeCount={countCheckFilters(filters)}
      categories={categories}
      renderOptions={renderOptions}
      onClearAll={clearAll}
    />
  );
}

function FilterOptions<V extends string>({
  options,
  chosen,
  label,
  onToggle,
}: {
  options: readonly V[];
  chosen: V | null;
  label: (value: V) => string;
  onToggle: (value: V) => void;
}) {
  return (
    <div className="space-y-1">
      {options.map((value) => {
        const isChosen = value === chosen;
        return (
          <button
            key={value}
            type="button"
            aria-pressed={isChosen}
            onClick={() => onToggle(value)}
            className={cn(
              OPTION_ROW_CLASS,
              "hover:bg-muted/50 cursor-pointer",
              isChosen && "bg-accent"
            )}
          >
            <span className="min-w-0 flex-1 truncate">{label(value)}</span>
            {isChosen && <Check className="text-primary h-4 w-4 shrink-0" aria-hidden />}
          </button>
        );
      })}
    </div>
  );
}
