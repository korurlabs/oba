"use client";

import * as React from "react";
import { ListFilter, Loader2, Search, X } from "lucide-react";

import { Button } from "./button";
import { Input } from "./field";
import { Popover, PopoverContent, PopoverTrigger } from "./popover";
import { cn } from "../src/lib/utils";

/** How long typing pauses before a category's server search runs, so each key press costs no request. */
export const SERVER_SEARCH_DELAY_MS = 300;

export type FilterOption = {
  value: string;
  label: string;
  /** A dot, avatar or icon before the label. */
  icon?: React.ReactNode;
  /** Muted text after the label, such as a prefix or an e-mail address. */
  hint?: string;
};

type FilterCategoryBase = {
  /** Unique within the popover; also names the radio group of a single select. */
  key: string;
  label: string;
  /**
   * The options shown. With `onSearchChange` these are the server's answer to
   * the current search and are shown as given, so they must keep the selected
   * value among them for its row to stay ticked.
   */
  options: FilterOption[];
  /** Shows a search box that narrows the options in memory, for long static lists. */
  searchable?: boolean;
  /**
   * Makes the category search on the server: called with the typed text after
   * a short pause, and with "" when the member leaves the category or closes
   * the popover. The search box is then always shown.
   */
  onSearchChange?: (search: string) => void;
  /** True while the server search of `onSearchChange` runs. */
  isLoading?: boolean;
  /** A translated message shown above the options when loading them failed. */
  error?: string | null;
  /** Overrides `labels.searchOptions` for this category. */
  searchPlaceholder?: string;
  /** Overrides `labels.noOptions` for this category. */
  emptyLabel?: string;
};

/**
 * A category that holds at most one value; ticking the chosen option again
 * clears it. With `defaultValue` it always holds one: ticking the chosen
 * option again returns it to the default, as the app's Clear should.
 */
export type SingleFilterCategory = FilterCategoryBase & {
  mode: "single";
  value: string | null;
  onChange: (value: string | null) => void;
  /**
   * The option the list shows when nothing narrows it, such as "upcoming"
   * events. It is not counted as a filter, so the trigger's badge and the
   * Clear footer appear only once another option is chosen.
   */
  defaultValue?: string;
};

/** A category that holds any number of values. */
export type MultiFilterCategory = FilterCategoryBase & {
  mode: "multi";
  value: string[];
  onChange: (value: string[]) => void;
};

export type FilterCategory = SingleFilterCategory | MultiFilterCategory;

/** Every text of the popover, translated by the app. */
export type FilterPopoverLabels = {
  /** The trigger's accessible name, such as "Filter". */
  trigger: string;
  /** Shown on the right until a category is chosen, such as "Select a field". */
  chooseCategory: string;
  /** The placeholder of an option search. */
  searchOptions: string;
  /** Shown when a category has no option to show. */
  noOptions: string;
  /** The accessible text of the loading spinner. */
  loading: string;
  /** The footer button that clears every filter, such as "Clear all". */
  clear: string;
};

/** The number of values a category narrows the list by. */
export function categoryActiveCount(category: FilterCategory): number {
  if (category.mode === "multi") return category.value.length;
  return category.value === null || category.value === category.defaultValue ? 0 : 1;
}

/** The number on the trigger's badge: every chosen value of every category. */
export function totalActiveCount(categories: readonly FilterCategory[]): number {
  let total = 0;
  for (const category of categories) total += categoryActiveCount(category);
  return total;
}

/** The values of a multi select after its option `value` is ticked or unticked. */
export function toggleMultiValue(values: readonly string[], value: string): string[] {
  if (values.includes(value)) return values.filter((chosen) => chosen !== value);
  return [...values, value];
}

/**
 * The value of a single select after its option `value` is clicked: the
 * chosen one clears, to `defaultValue` when the category has one.
 */
export function nextSingleValue(
  current: string | null,
  value: string,
  defaultValue?: string
): string | null {
  if (current !== value) return value;
  return defaultValue ?? null;
}

/** Whether the category shows a search box over its options. */
export function hasOptionSearch(category: FilterCategory): boolean {
  return category.onSearchChange !== undefined || category.searchable === true;
}

/**
 * The options shown for `search`. A server search already narrowed them; a
 * static list is narrowed here on the label and the hint, ignoring case.
 */
export function visibleOptions(category: FilterCategory, search: string): FilterOption[] {
  if (category.onSearchChange !== undefined || !category.searchable) return category.options;
  const needle = search.trim().toLowerCase();
  if (needle === "") return category.options;
  return category.options.filter(
    (option) =>
      option.label.toLowerCase().includes(needle) ||
      (option.hint?.toLowerCase().includes(needle) ?? false)
  );
}

/** The category whose options show: the chosen one, or the only one when there is just one. */
export function shownCategory(
  categories: readonly FilterCategory[],
  selectedKey: string | null
): FilterCategory | null {
  if (selectedKey !== null) {
    const selected = categories.find((category) => category.key === selectedKey);
    if (selected) return selected;
  }
  return categories.length === 1 ? (categories[0] ?? null) : null;
}

function isChecked(category: FilterCategory, value: string): boolean {
  return category.mode === "multi" ? category.value.includes(value) : category.value === value;
}

function choose(category: FilterCategory, value: string): void {
  if (category.mode === "multi") {
    category.onChange(toggleMultiValue(category.value, value));
    return;
  }
  const next = nextSingleValue(category.value, value, category.defaultValue);
  // Clicking the chosen default changes nothing, so the app gets no call.
  if (next !== category.value) category.onChange(next);
}

const COUNT_CLASS =
  "bg-korur-blue/10 text-korur-blue-hover inline-flex h-5 min-w-5 items-center justify-center rounded-md px-1 text-xs font-medium tabular-nums";

/** Option rows and category tabs light up like sky's menu links (`sky-glass-item` in styles.css). */
const OPTION_ROW_CLASS =
  "sky-glass-item flex cursor-pointer items-center gap-2.5 px-2 py-1.5 text-sm";

export type FilterPopoverPanelProps = {
  categories: readonly FilterCategory[];
  /** The key of the category whose options show; null shows the prompt. */
  selectedKey: string | null;
  onSelectCategory: (key: string) => void;
  /** The text of the option search of the shown category. */
  search: string;
  onSearchChange: (search: string) => void;
  onClear: () => void;
  labels: FilterPopoverLabels;
  className?: string;
};

/**
 * The popover's content: the categories on the left with their counts, the
 * shown category's options on the right, and a Clear footer while any filter
 * is set. Fully controlled, so it renders the same outside the popover.
 */
export function FilterPopoverPanel({
  categories,
  selectedKey,
  onSelectCategory,
  search,
  onSearchChange,
  onClear,
  labels,
  className,
}: FilterPopoverPanelProps) {
  const groupId = React.useId();
  const shown = shownCategory(categories, selectedKey);
  const activeCount = totalActiveCount(categories);

  return (
    <div
      data-slot="filter-popover-panel"
      className={cn("flex min-h-0 flex-col overflow-hidden", className)}
    >
      <div className="flex max-h-[320px] min-h-0 flex-1">
        <div
          className="sky-glass-divider w-[40%] space-y-0.5 overflow-y-auto border-r p-1.5"
          role="tablist"
          aria-orientation="vertical"
        >
          {categories.map((category) => {
            const isShown = shown?.key === category.key;
            const count = categoryActiveCount(category);
            return (
              <button
                key={category.key}
                type="button"
                role="tab"
                aria-selected={isShown}
                data-category={category.key}
                onClick={() => onSelectCategory(category.key)}
                className="sky-glass-item flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm"
              >
                <span className="truncate">{category.label}</span>
                {count > 0 && <span className={COUNT_CLASS}>{count}</span>}
              </button>
            );
          })}
        </div>

        <div className="flex min-h-0 w-[60%] flex-col" role="tabpanel">
          {shown === null ? (
            <div className="text-muted-foreground flex flex-1 items-center justify-center px-4 py-8 text-center text-sm">
              {labels.chooseCategory}
            </div>
          ) : (
            <>
              {hasOptionSearch(shown) && (
                <div className="sky-glass-divider border-b px-3 py-2">
                  <div className="relative">
                    <Search
                      aria-hidden
                      className="text-muted-foreground pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2"
                    />
                    <Input
                      type="search"
                      value={search}
                      onChange={(event) => onSearchChange(event.target.value)}
                      placeholder={shown.searchPlaceholder ?? labels.searchOptions}
                      aria-label={shown.searchPlaceholder ?? labels.searchOptions}
                      className="h-8 bg-white/80 pl-8"
                    />
                  </div>
                </div>
              )}
              <div className="flex-1 overflow-y-auto px-3 py-2">
                <CategoryOptions
                  category={shown}
                  options={visibleOptions(shown, search)}
                  groupName={`${groupId}-${shown.key}`}
                  labels={labels}
                />
              </div>
            </>
          )}
        </div>
      </div>

      {activeCount > 0 && (
        <div className="sky-glass-divider shrink-0 border-t px-3 py-2">
          <Button type="button" variant="ghost" size="sm" className="w-full" onClick={onClear}>
            <X aria-hidden />
            {labels.clear}
          </Button>
        </div>
      )}
    </div>
  );
}

function CategoryOptions({
  category,
  options,
  groupName,
  labels,
}: {
  category: FilterCategory;
  options: FilterOption[];
  groupName: string;
  labels: FilterPopoverLabels;
}) {
  return (
    <div className="space-y-1">
      {category.error && (
        <p role="alert" className="text-error px-2 py-1 text-xs">
          {category.error}
        </p>
      )}
      {options.map((option) => {
        const checked = isChecked(category, option.value);
        return (
          <label key={option.value} className={OPTION_ROW_CLASS}>
            {category.mode === "multi" ? (
              <input
                type="checkbox"
                className="accent-korur-blue size-4 shrink-0"
                checked={checked}
                onChange={() => choose(category, option.value)}
              />
            ) : (
              <input
                type="radio"
                name={groupName}
                className="accent-korur-blue size-4 shrink-0"
                checked={checked}
                onChange={() => choose(category, option.value)}
                // A radio fires no change when it is already chosen, so the
                // click itself clears it: a single select can go back to "any".
                onClick={() => {
                  if (checked) choose(category, option.value);
                }}
              />
            )}
            {option.icon}
            <span className="truncate" title={option.label}>
              {option.label}
            </span>
            {option.hint && (
              <span className="text-muted-foreground ml-auto max-w-[45%] shrink-0 truncate text-xs">
                {option.hint}
              </span>
            )}
          </label>
        );
      })}
      {category.isLoading ? (
        <div role="status" className="flex justify-center py-2">
          <Loader2 className="text-muted-foreground size-4 animate-spin" aria-hidden />
          <span className="sr-only">{labels.loading}</span>
        </div>
      ) : (
        options.length === 0 && (
          <p className="text-muted-foreground px-2 py-8 text-center text-sm">
            {category.emptyLabel ?? labels.noOptions}
          </p>
        )
      )}
    </div>
  );
}

export type FilterPopoverProps = {
  categories: readonly FilterCategory[];
  /** Clears every filter; the app also resets its paging here. */
  onClear: () => void;
  labels: FilterPopoverLabels;
  /** Classes for the trigger button. */
  className?: string;
};

/**
 * The Filter button of a list toolbar, badged with the number of chosen
 * values, and its popover of categories and options. The app keeps every
 * value (usually in the URL) and passes it in; the popover keeps only which
 * category shows and the text of its option search.
 */
export function FilterPopover({ categories, onClear, labels, className }: FilterPopoverProps) {
  const [selectedKey, setSelectedKey] = React.useState<string | null>(null);
  const [search, setSearch] = React.useState("");
  const activeCount = totalActiveCount(categories);
  const shown = shownCategory(categories, selectedKey);

  // The newest callback without restarting the pause on every render of the app.
  const serverSearch = React.useRef<((search: string) => void) | undefined>(undefined);
  serverSearch.current = shown?.onSearchChange;
  const sent = React.useRef("");

  React.useEffect(() => {
    if (sent.current === search) return;
    const timer = setTimeout(() => {
      sent.current = search;
      serverSearch.current?.(search);
    }, SERVER_SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [search]);

  /** Empties the option search, and the server search of the shown category with it. */
  const resetSearch = () => {
    if (sent.current !== "") shown?.onSearchChange?.("");
    sent.current = "";
    setSearch("");
  };

  return (
    <Popover
      onOpenChange={(open) => {
        if (!open) resetSearch();
      }}
    >
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          title={labels.trigger}
          data-active={activeCount > 0 || undefined}
          className={cn(
            "data-[active=true]:border-korur-blue/40 data-[state=open]:border-korur-blue/40 data-[state=open]:bg-accent shrink-0 gap-2 data-[state=open]:shadow-[0_0_14px_-4px_rgb(47_143_233/0.45)]",
            className
          )}
        >
          <ListFilter aria-hidden />
          {/* Text, not an aria-label, so the badge's count is read with the name. */}
          <span className="sr-only">{labels.trigger}</span>
          {activeCount > 0 && <span className={COUNT_CLASS}>{activeCount}</span>}
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        collisionPadding={16}
        className="sky-glass-panel flex max-h-[var(--radix-popover-content-available-height)] w-[min(520px,calc(100vw-2rem))] flex-col overflow-hidden p-0"
      >
        <FilterPopoverPanel
          categories={categories}
          selectedKey={selectedKey}
          onSelectCategory={(key) => {
            if (key === shown?.key) return;
            resetSearch();
            setSelectedKey(key);
          }}
          search={search}
          onSearchChange={setSearch}
          onClear={onClear}
          labels={labels}
          className="min-h-0 flex-1"
        />
      </PopoverContent>
    </Popover>
  );
}
