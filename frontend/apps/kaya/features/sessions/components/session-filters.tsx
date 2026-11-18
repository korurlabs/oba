"use client";

import { useEffect, useState, type ChangeEvent } from "react";
import { Search, X } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { cn } from "@/lib/utils";

import type { SessionFilterParams } from "../hooks/use-session-filter-params";
import type {
  SessionFilterMachine,
  SessionFilterUser,
  SessionFilterSubscription,
  SessionFiltersResponse,
  RepositoryResponse,
} from "../schemas";
import { subscriptionOptionLabel } from "./wall-data";

/** How long the search waits after the last key before it filters. */
const SEARCH_DEBOUNCE_MS = 400;

type FilterKey = Exclude<keyof SessionFilterParams, "page" | "search" | "ended">;

export type SessionFiltersProps = {
  filters: SessionFilterParams;
  /** Applies a change; the wall goes back to its first page. */
  onChange: (change: Partial<SessionFilterParams>) => void;
  options: SessionFiltersResponse | undefined;
  repositories: readonly RepositoryResponse[] | undefined;
};

type Option = { value: string; label: string };

const SELECT_CLASS =
  "border-input bg-background h-9 min-w-0 rounded-md border px-2 text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50";

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
      <select
        className={SELECT_CLASS}
        value={value ?? ""}
        onChange={(event: ChangeEvent<HTMLSelectElement>) => onChange(event.target.value || null)}
      >
        <option value="">All</option>
        {all.map((option: Option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
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
          maxLength={200}
          placeholder="Title, branch or ticket key"
          onChange={(event: ChangeEvent<HTMLInputElement>) => setText(event.target.value)}
          className={cn(SELECT_CLASS, "w-full pl-8")}
        />
      </span>
    </label>
  );
}

/**
 * The wall's filters: a search and one select per value the API offers. Each
 * select lists only what the organization's sessions hold.
 */
export function SessionFilters({ filters, onChange, options, repositories }: SessionFiltersProps) {
  const set = (key: FilterKey) => (value: string | null) => onChange({ [key]: value });
  const active =
    filters.search !== null ||
    filters.ended !== null ||
    (["repositoryId", "userId", "machineId", "model", "accountOrganizationUuid"] as const).some(
      (key: FilterKey) => filters[key] !== null
    );
  // A user narrows the machines to that user's own.
  const machines = (options?.machines ?? []).filter(
    (machine: SessionFilterMachine) => filters.userId === null || machine.userId === filters.userId
  );

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-8">
      <SearchInput value={filters.search} onSearch={(search) => onChange({ search })} />
      <FilterSelect
        label="Repository"
        value={filters.repositoryId}
        onChange={(repositoryId) => onChange({ repositoryId })}
        options={(repositories ?? []).map((repository: RepositoryResponse) => ({
          value: repository.id,
          label: repository.name,
        }))}
      />
      <FilterSelect
        label="User"
        value={filters.userId}
        onChange={(userId) => onChange({ userId, machineId: null })}
        options={(options?.users ?? []).map((user: SessionFilterUser) => ({
          value: user.id,
          label: user.displayName,
        }))}
      />
      <FilterSelect
        label="Machine"
        value={filters.machineId}
        onChange={set("machineId")}
        options={machines.map((machine: SessionFilterMachine) => ({
          value: machine.id,
          label: machine.machineName,
        }))}
      />
      <FilterSelect
        label="Model"
        value={filters.model}
        onChange={set("model")}
        options={(options?.models ?? []).map((model: string) => ({ value: model, label: model }))}
      />
      <FilterSelect
        label="Subscription"
        value={filters.accountOrganizationUuid}
        onChange={set("accountOrganizationUuid")}
        options={(options?.subscriptions ?? []).map((subscription: SessionFilterSubscription) => ({
          value: subscription.uuid,
          label: subscriptionOptionLabel(subscription),
        }))}
      />
      <FilterSelect
        label="Session"
        value={filters.ended === null ? null : String(filters.ended)}
        onChange={(value) => onChange({ ended: value === null ? null : value === "true" })}
        options={[
          { value: "false", label: "Open" },
          { value: "true", label: "Ended" },
        ]}
      />
      {active && (
        <div className="flex items-end">
          <Button
            variant="ghost"
            className="h-9"
            onClick={() =>
              onChange({
                search: null,
                repositoryId: null,
                userId: null,
                machineId: null,
                model: null,
                accountOrganizationUuid: null,
                ended: null,
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
