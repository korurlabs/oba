"use client";

import { useEffect, useState, type ChangeEvent } from "react";
import { Search, X } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { CONTROL_CLASS, NativeSelect } from "@oba/ui/sky/field";
import { cn } from "@/lib/utils";

import { ALL_REVIEW_STATES, type PlanFilterParams } from "../hooks/use-plan-filter-params";
import {
  PLAN_LIMITS,
  PlanKindSchema,
  type PlanFiltersResponse,
  type PlanKind,
  type PlanReferenceResponse,
  type PlanReviewState,
} from "../schemas";
import {
  KIND_LABELS,
  REVIEW_BADGES,
  REVIEW_FILTERS,
  hasActiveFilter,
  referenceName,
} from "./plan-data";

/** How long the search waits after the last key before it filters. */
const SEARCH_DEBOUNCE_MS = 400;

type Option = { value: string; label: string };

export type PlanFiltersProps = {
  filters: PlanFilterParams;
  /** Applies a change; the list goes back to its first page. */
  onChange: (change: Partial<PlanFilterParams>) => void;
  options: PlanFiltersResponse | undefined;
};

function FilterSelect({
  label,
  value,
  options,
  allLabel = "All",
  onChange,
}: {
  label: string;
  value: string | null;
  options: readonly Option[];
  allLabel?: string;
  onChange: (value: string | null) => void;
}) {
  // A value from a shared link that the organization no longer holds stays selectable, so it can be cleared.
  const all =
    value !== null && !options.some((option: Option) => option.value === value)
      ? [...options, { value, label: value }]
      : options;
  return (
    <label className="flex min-w-0 flex-col gap-1 text-xs font-medium">
      <span className="text-muted-foreground">{label}</span>
      <NativeSelect
        value={value ?? ""}
        onChange={(event: ChangeEvent<HTMLSelectElement>) => onChange(event.target.value || null)}
      >
        <option value="">{allLabel}</option>
        {all.map((option: Option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </NativeSelect>
    </label>
  );
}

function SearchInput({
  value,
  onSearch,
}: {
  value: string | null;
  onSearch: (value: string | null) => void;
}) {
  const [text, setText] = useState(value ?? "");
  const [shown, setShown] = useState(value);

  // The URL wins when it changes from outside, as on "Clear filters" or back navigation.
  if (shown !== value) {
    setShown(value);
    setText(value ?? "");
  }

  useEffect(() => {
    const next = text.trim() === "" ? null : text.trim();
    if (next === value) return;
    const timer = setTimeout(() => onSearch(next), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [text, value, onSearch]);

  return (
    <label className="flex min-w-0 flex-col gap-1 text-xs font-medium sm:col-span-2">
      <span className="text-muted-foreground">Search</span>
      <span className="relative">
        <Search
          className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2"
          aria-hidden
        />
        <input
          type="search"
          value={text}
          maxLength={PLAN_LIMITS.searchMax}
          placeholder="Title or path"
          onChange={(event: ChangeEvent<HTMLInputElement>) => setText(event.target.value)}
          className={cn(CONTROL_CLASS, "h-9 w-full pl-8")}
        />
      </span>
    </label>
  );
}

function referenceOptions(references: readonly PlanReferenceResponse[] | undefined): Option[] {
  return (references ?? []).map((reference: PlanReferenceResponse) => ({
    value: reference.id,
    label: referenceName(reference),
  }));
}

/**
 * The plan list's filters: a search on title and path, and one select per
 * value the API offers. The review select starts on "Open" and offers "All".
 */
export function PlanFilters({ filters, onChange, options }: PlanFiltersProps) {
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7">
      <SearchInput value={filters.search} onSearch={(search) => onChange({ search })} />
      <label className="flex min-w-0 flex-col gap-1 text-xs font-medium">
        <span className="text-muted-foreground">Review</span>
        <NativeSelect
          value={filters.reviewState}
          onChange={(event: ChangeEvent<HTMLSelectElement>) =>
            onChange({
              reviewState: event.target.value as PlanReviewState | typeof ALL_REVIEW_STATES,
            })
          }
        >
          {REVIEW_FILTERS.map((state: PlanReviewState) => (
            <option key={state} value={state}>
              {REVIEW_BADGES[state].label}
            </option>
          ))}
          <option value={ALL_REVIEW_STATES}>All</option>
        </NativeSelect>
      </label>
      <FilterSelect
        label="Kind"
        value={filters.kind}
        onChange={(kind) => onChange({ kind: kind as PlanKind | null })}
        options={PlanKindSchema.options.map((kind: PlanKind) => ({
          value: kind,
          label: KIND_LABELS[kind],
        }))}
      />
      <FilterSelect
        label="Repository"
        value={filters.repositoryId}
        onChange={(repositoryId) => onChange({ repositoryId })}
        options={referenceOptions(options?.repositories)}
      />
      <FilterSelect
        label="Pack"
        value={filters.packId}
        onChange={(packId) => onChange({ packId })}
        options={referenceOptions(options?.packs)}
      />
      <FilterSelect
        label="User"
        value={filters.userId}
        onChange={(userId) => onChange({ userId })}
        options={referenceOptions(options?.users)}
      />
      {hasActiveFilter(filters) && (
        <div className="flex items-end">
          <Button
            variant="ghost"
            className="h-9"
            onClick={() =>
              onChange({
                search: null,
                reviewState: "OPEN",
                kind: null,
                repositoryId: null,
                packId: null,
                userId: null,
              })
            }
          >
            <X className="size-4" aria-hidden />
            Clear filters
          </Button>
        </div>
      )}
    </div>
  );
}
