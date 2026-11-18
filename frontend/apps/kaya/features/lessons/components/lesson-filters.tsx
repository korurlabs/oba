"use client";

import { useEffect, useState, type ChangeEvent } from "react";
import { Search, X } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { CONTROL_CLASS, NativeSelect } from "@oba/ui/sky/field";
import { cn } from "@/lib/utils";

import { ORGANIZATION_WIDE, type LessonFilterParams } from "../hooks/use-lesson-filter-params";
import {
  LESSON_LIMITS,
  type LessonFiltersResponse,
  type LessonReferenceResponse,
} from "../schemas";
import { ORGANIZATION_WIDE_LABEL, hasActiveFilter, referenceName } from "./lesson-data";

/** How long the search waits after the last key before it filters. */
const SEARCH_DEBOUNCE_MS = 400;

type Option = { value: string; label: string };

export type LessonFiltersProps = {
  filters: LessonFilterParams;
  /** Applies a change; the list goes back to its first page. */
  onChange: (change: Partial<LessonFilterParams>) => void;
  options: LessonFiltersResponse | undefined;
};

function FilterSelect({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string | null;
  options: readonly Option[];
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
        <option value="">All</option>
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
  // The URL holds the trimmed search, so a change that only echoes the input leaves it alone:
  // the space after a finished word has to survive for the next word to be typed.
  if (shown !== value) {
    setShown(value);
    if ((value ?? "") !== text.trim()) setText(value ?? "");
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
          maxLength={LESSON_LIMITS.searchMax}
          placeholder="Title or text"
          onChange={(event: ChangeEvent<HTMLInputElement>) => setText(event.target.value)}
          className={cn(CONTROL_CLASS, "h-9 w-full pl-8")}
        />
      </span>
    </label>
  );
}

function referenceOptions(references: readonly LessonReferenceResponse[] | undefined): Option[] {
  return (references ?? []).map((reference: LessonReferenceResponse) => ({
    value: reference.id,
    label: referenceName(reference),
  }));
}

/**
 * The lesson list's filters: a search on title and text, the repositories
 * and users that hold lessons, and "Organization-wide" for the lessons of no
 * repository.
 */
export function LessonFilters({ filters, onChange, options }: LessonFiltersProps) {
  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
      <SearchInput value={filters.search} onSearch={(search) => onChange({ search })} />
      <FilterSelect
        label="Repository"
        value={filters.repositoryId}
        onChange={(repositoryId) => onChange({ repositoryId })}
        options={[
          { value: ORGANIZATION_WIDE, label: ORGANIZATION_WIDE_LABEL },
          ...referenceOptions(options?.repositories),
        ]}
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
            onClick={() => onChange({ search: null, repositoryId: null, userId: null })}
          >
            <X className="size-4" aria-hidden />
            Clear filters
          </Button>
        </div>
      )}
    </div>
  );
}
