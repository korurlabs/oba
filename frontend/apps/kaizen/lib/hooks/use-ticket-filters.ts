"use client";

import { useMemo, useCallback, useEffect } from "react";
import { useQueryStates } from "nuqs";
import {
  ticketFilterParams,
  useTicketFilterParams,
  type TicketFilterParams,
} from "@/features/tickets/hooks/use-ticket-filter-params";

/**
 * Number of filters set in `filters`, as the filter badge shows it. Sort
 * order is not a filter, and a due date range counts once.
 */
export function countActiveTicketFilters(filters: TicketFilterParams): number {
  let count = 0;
  if ((filters.statuses ?? []).length > 0) count++;
  if ((filters.types ?? []).length > 0) count++;
  if ((filters.priorities ?? []).length > 0) count++;
  if ((filters.assigneeIds ?? []).length > 0) count++;
  if ((filters.reviewerIds ?? []).length > 0) count++;
  if ((filters.createdByIds ?? []).length > 0) count++;
  if ((filters.labelIds ?? []).length > 0) count++;
  if ((filters.boardIds ?? []).length > 0) count++;
  if (filters.search) count++;
  if (filters.projectId) count++;
  if (filters.dueDateStart || filters.dueDateEnd) count++;
  return count;
}

/**
 * Convenience wrapper around `useTicketFilterParams` (nuqs-based URL state).
 *
 * Provides:
 * - `filters` / `setFilters` – the raw nuqs state
 * - `activeFilterCount` – number of non-default filters active
 * - `clearFilters` – reset all filter params to null
 * - `updateFilter` – update a single filter key
 */
export function useTicketFilters() {
  const [filters, setFilters] = useTicketFilterParams();

  const activeFilterCount = useMemo(() => countActiveTicketFilters(filters), [filters]);

  const clearFilters = useCallback(() => {
    setFilters({
      search: null,
      statuses: null,
      types: null,
      priorities: null,
      assigneeIds: null,
      reviewerIds: null,
      createdByIds: null,
      labelIds: null,
      boardIds: null,
      projectId: null,
      dueDateStart: null,
      dueDateEnd: null,
    });
  }, [setFilters]);

  const updateFilter = useCallback(
    <K extends keyof TicketFilterParams>(key: K, value: TicketFilterParams[K]) => {
      setFilters({ [key]: value } as Partial<TicketFilterParams>);
    },
    [setFilters],
  );

  return {
    filters,
    setFilters,
    updateFilter,
    clearFilters,
    activeFilterCount,
  };
}

// ── Filters relative to a saved baseline ─────────────────────────────

/** The filter fields a user can save. Sort order and `includeAllBoards` are not saved. */
const BASELINE_FILTER_KEYS = [
  "search",
  "statuses",
  "types",
  "priorities",
  "assigneeIds",
  "reviewerIds",
  "createdByIds",
  "labelIds",
  "boardIds",
  "projectId",
  "dueDateStart",
  "dueDateEnd",
] as const satisfies readonly (keyof TicketFilterParams)[];

type BaselineFilterKey = (typeof BASELINE_FILTER_KEYS)[number];

const ARRAY_FILTER_KEYS: ReadonlySet<BaselineFilterKey> = new Set<BaselineFilterKey>([
  "statuses",
  "types",
  "priorities",
  "assigneeIds",
  "reviewerIds",
  "createdByIds",
  "labelIds",
  "boardIds",
]);

/**
 * The filters a table shows when its URL carries no filter parameter: the
 * user's saved filters, or the page's own default when nothing is saved.
 * Saved filters come back from JSON as plain strings, so values are checked
 * against the URL parsers before use.
 */
export type TicketFilterBaseline = {
  [K in BaselineFilterKey]?: string | readonly string[] | null;
};

/** A filter value with "cleared" always written as null. */
type FilterValue = string | string[] | null;

/**
 * The URL parsers seen through one loose signature, so the fields can be
 * handled in a loop. Each parser still checks its own values: an unknown
 * status is dropped by the enum parser.
 */
const LOOSE_FILTER_PARSERS = ticketFilterParams as unknown as Record<
  keyof TicketFilterParams,
  { parse: (query: string) => unknown; serialize: (value: never) => string }
>;

/**
 * nuqs parsers of the baseline-relative filters. A discrete change (a status
 * chip, an assignee, the sort) pushes a history entry so Back undoes it; the
 * search replaces, so typing does not add an entry per keystroke.
 */
const baselineTicketFilterParams = {
  ...ticketFilterParams,
  search: ticketFilterParams.search.withOptions({ history: "replace" }),
};

function toFilterValue(value: unknown): FilterValue {
  if (typeof value === "string") return value === "" ? null : value;
  if (Array.isArray(value)) return value.length === 0 ? null : value.map(String);
  return null;
}

function sameFilterValue(a: FilterValue, b: FilterValue): boolean {
  if (a === null || b === null) return a === b;
  if (typeof a === "string" || typeof b === "string") return a === b;
  if (a.length !== b.length) return false;
  // The order of chosen values does not change which tickets match.
  const sortedB = [...b].sort();
  return [...a].sort().every((value, index) => value === sortedB[index]);
}

/** The baseline with every value run through its URL parser. */
function parseBaseline(baseline: TicketFilterBaseline): Record<BaselineFilterKey, FilterValue> {
  const parsed = {} as Record<BaselineFilterKey, FilterValue>;
  for (const key of BASELINE_FILTER_KEYS) {
    const value = toFilterValue(baseline[key]);
    if (value === null) {
      parsed[key] = null;
      continue;
    }
    const parser = LOOSE_FILTER_PARSERS[key];
    parsed[key] = toFilterValue(parser.parse(parser.serialize(value as never)));
  }
  return parsed;
}

/** The URL query of a value, to tell whether a write changes the URL. */
function queryOf(key: keyof TicketFilterParams, value: unknown): string | null {
  if (value === null || value === undefined) return null;
  return LOOSE_FILTER_PARSERS[key].serialize(value as never);
}

/**
 * Filters read from the URL relative to a saved baseline, so the URL carries
 * only what differs from the saved set and is clean when nothing differs.
 *
 * Per filter field: no parameter means the baseline value; a parameter with a
 * value overrides it; an empty parameter (`?statuses=`, `?search=`) means the
 * field is cleared although the baseline sets it. An array whose items are all
 * invalid parses to an empty array and counts as cleared too.
 *
 * Writing removes a field's parameter when the new value equals the baseline,
 * writes an empty parameter when a field set by the baseline is cleared, and
 * writes the value otherwise. Once the baseline is known, any parameter equal
 * to it is removed without a history entry: this cleans the URL after a save
 * refreshes the baseline, after a reset, and on an incoming link that repeats
 * saved values.
 *
 * `baseline` is null while it is not known yet (the user is still loading);
 * pass `{}` when nothing is saved. Pass a stable object (memoized), since a
 * new identity re-runs the cleanup.
 */
export function useBaselineTicketFilters(baseline: TicketFilterBaseline | null) {
  const [rawFilters, setRawFilters] = useQueryStates(baselineTicketFilterParams, {
    history: "push",
  });

  const parsedBaseline = useMemo(
    () => (baseline === null ? null : parseBaseline(baseline)),
    [baseline],
  );

  const toEffective = useCallback(
    (raw: TicketFilterParams): TicketFilterParams => {
      const effective: Record<string, unknown> = { ...raw };
      for (const key of BASELINE_FILTER_KEYS) {
        effective[key] =
          raw[key] === null ? (parsedBaseline?.[key] ?? null) : toFilterValue(raw[key]);
      }
      // Every field holds a value its parser produced or accepted.
      return effective as TicketFilterParams;
    },
    [parsedBaseline],
  );

  const filters = useMemo(() => toEffective(rawFilters), [toEffective, rawFilters]);

  const activeFilterCount = useMemo(() => countActiveTicketFilters(filters), [filters]);

  /** The URL values of a patch of effective values, without keys the URL already holds. */
  const toUrlPatch = useCallback(
    (patch: Partial<TicketFilterParams>, previousRaw: TicketFilterParams) => {
      const urlPatch: Record<string, unknown> = {};
      for (const [name, value] of Object.entries(patch)) {
        if (value === undefined) continue;
        const key = name as keyof TicketFilterParams;
        let next: unknown = value;
        if ((BASELINE_FILTER_KEYS as readonly string[]).includes(key)) {
          const baselineKey = key as BaselineFilterKey;
          const wanted = toFilterValue(value);
          const saved = parsedBaseline?.[baselineKey] ?? null;
          if (sameFilterValue(wanted, saved)) next = null;
          else if (wanted === null) next = ARRAY_FILTER_KEYS.has(baselineKey) ? [] : "";
          else next = wanted;
        }
        // A key whose URL does not change is left out, so it neither pushes a
        // history entry nor turns a search edit into one.
        if (queryOf(key, next) === queryOf(key, previousRaw[key])) continue;
        urlPatch[key] = next;
      }
      return urlPatch as Partial<TicketFilterParams>;
    },
    [parsedBaseline],
  );

  const setFilters = useCallback(
    (
      update:
        | Partial<TicketFilterParams>
        | ((previous: TicketFilterParams) => Partial<TicketFilterParams>),
    ) =>
      setRawFilters((previousRaw) => {
        const patch = typeof update === "function" ? update(toEffective(previousRaw)) : update;
        return toUrlPatch(patch, previousRaw);
      }),
    [setRawFilters, toEffective, toUrlPatch],
  );

  const updateFilter = useCallback(
    <K extends keyof TicketFilterParams>(key: K, value: TicketFilterParams[K]) =>
      setFilters({ [key]: value } as Partial<TicketFilterParams>),
    [setFilters],
  );

  const clearFilters = useCallback(
    () =>
      setFilters(
        Object.fromEntries(BASELINE_FILTER_KEYS.map((key) => [key, null])) as Partial<
          TicketFilterParams
        >,
      ),
    [setFilters],
  );

  /**
   * Removes every filter parameter, so the table shows whichever baseline
   * applies once it loads: Reset deletes the saved set, and the page default
   * then takes its place. Clearing relative to the old baseline would leave
   * empty parameters that differ from the new one. Only parameters present in
   * the URL are removed, and a URL without any writes nothing: every write
   * pushes a history entry, and an entry identical to the current one would
   * make the next Back press do nothing.
   */
  const resetToBaseline = useCallback(() => {
    const present = BASELINE_FILTER_KEYS.filter((key) => rawFilters[key] !== null);
    if (present.length === 0) return;
    setRawFilters(
      Object.fromEntries(present.map((key) => [key, null])) as Partial<TicketFilterParams>,
      { history: "push" },
    );
  }, [rawFilters, setRawFilters]);

  // Whether the view differs from the baseline: what Save would store is new.
  // A parameter equal to the baseline does not count, since the cleanup below
  // removes it.
  const hasChanges = useMemo(() => {
    if (parsedBaseline === null) return false;
    return BASELINE_FILTER_KEYS.some(
      (key) =>
        rawFilters[key] !== null &&
        !sameFilterValue(toFilterValue(rawFilters[key]), parsedBaseline[key]),
    );
  }, [parsedBaseline, rawFilters]);

  // Removes parameters that repeat the baseline. It replaces the history
  // entry: the view does not change, only its address.
  useEffect(() => {
    if (parsedBaseline === null) return;
    const redundant: Partial<Record<BaselineFilterKey, null>> = {};
    for (const key of BASELINE_FILTER_KEYS) {
      if (rawFilters[key] === null) continue;
      if (sameFilterValue(toFilterValue(rawFilters[key]), parsedBaseline[key])) {
        redundant[key] = null;
      }
    }
    if (Object.keys(redundant).length === 0) return;
    setRawFilters(redundant, { history: "replace" });
  }, [parsedBaseline, rawFilters, setRawFilters]);

  return {
    filters,
    setFilters,
    updateFilter,
    clearFilters,
    resetToBaseline,
    hasChanges,
    activeFilterCount,
    isReady: parsedBaseline !== null,
  };
}

export type { TicketFilterParams };
