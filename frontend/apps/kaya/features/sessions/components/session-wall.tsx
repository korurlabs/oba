"use client";

import { useCallback, useMemo, useState } from "react";
import { AlertTriangle } from "lucide-react";

import { FilterPopover } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { ListEmptyState, LoadError } from "@oba/ui/sky/page-states";
import { SearchInput } from "@oba/ui/sky/search-input";
import {
  CLEAR_FILTERS_LABEL,
  CLEAR_SEARCH_LABEL,
  FILTER_LABELS,
  ListPagination,
} from "@/components/common/list-parts";

import {
  useSessionFilterParams,
  type SessionFilterParams,
} from "../hooks/use-session-filter-params";
import {
  useSessionFilters,
  useSessions,
  useRepositories,
  WALL_PAGE_SIZE,
} from "../hooks/use-sessions";
import type { SessionListResponse, SessionQueryRequest } from "../schemas";
import { NO_SESSION_FILTERS, sessionFilterCategories, sessionsFiltered } from "./session-filters";
import { SessionTile } from "./session-tile";

const GRID = "grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3";

/** The API's query for the filters in the URL; a filter that is not set is left out. */
export function toQuery(filters: SessionFilterParams): SessionQueryRequest {
  const query: SessionQueryRequest = { page: Math.max(1, filters.page), amount: WALL_PAGE_SIZE };
  if (filters.search !== null) query.search = filters.search;
  if (filters.repositoryId !== null) query.repositoryId = filters.repositoryId;
  if (filters.userId !== null) query.userId = filters.userId;
  if (filters.machineId !== null) query.machineId = filters.machineId;
  if (filters.model !== null) query.model = filters.model;
  if (filters.accountOrganizationUuid !== null)
    query.accountOrganizationUuid = filters.accountOrganizationUuid;
  if (filters.ended !== null) query.ended = filters.ended;
  return query;
}

/**
 * The wall: one tile per session of the organization, newest activity first, 60
 * a page. The filters live in the URL, so a filtered wall survives a reload
 * and can be shared.
 */
export function SessionWall() {
  const [filters, setFilters] = useSessionFilterParams();
  const query = useMemo(() => toQuery(filters), [filters]);
  const sessions = useSessions(query);
  const options = useSessionFilters();
  const repositories = useRepositories();
  // Relative times are computed against the moment the answer arrived.
  const [now] = useState(() => Date.now());
  const loadedAt = sessions.dataUpdatedAt || now;

  const onChange = useCallback(
    (change: Partial<SessionFilterParams>) => void setFilters({ ...change, page: 1 }),
    [setFilters]
  );

  const tiles: SessionListResponse[] = sessions.data?.data ?? [];
  const pagination = sessions.data?.pagination ?? null;
  const optionErrors = [
    options.error === null
      ? null
      : `The user, machine, model and subscription lists could not be loaded: ${options.error.message}`,
    repositories.error === null
      ? null
      : `The repository list could not be loaded: ${repositories.error.message}`,
  ].filter((text): text is string => text !== null);

  const filtered = sessionsFiltered(filters);

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title="Sessions"
        description="Every Claude Code session recorded in your organization, newest activity first."
      />

      <ListToolbar
        search={
          <SearchInput
            value={filters.search}
            onSearchChange={(search: string | undefined) => onChange({ search: search ?? null })}
            label="Search sessions"
            placeholder="Title, branch or ticket key"
            clearLabel={CLEAR_SEARCH_LABEL}
          />
        }
        filters={
          <FilterPopover
            categories={sessionFilterCategories(
              filters,
              onChange,
              options.data?.data ?? undefined,
              repositories.data?.data ?? undefined
            )}
            onClear={() => onChange({ ...NO_SESSION_FILTERS, search: filters.search })}
            labels={FILTER_LABELS}
          />
        }
      />

      {optionErrors.length > 0 && (
        <ul className="text-warning-text flex flex-col gap-1 text-sm" role="status">
          {optionErrors.map((text: string) => (
            <li key={text} className="flex items-start gap-2">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
              <span>{text}</span>
            </li>
          ))}
        </ul>
      )}

      {sessions.error !== null && sessions.data === undefined ? (
        <LoadError
          title="Could not load the sessions"
          message={sessions.error.message}
          onRetry={() => void sessions.refetch()}
        />
      ) : sessions.data === undefined ? (
        <WallLoading />
      ) : tiles.length === 0 ? (
        <ListEmptyState
          filtered={filtered || filters.page > 1}
          onClearFilters={() => onChange(NO_SESSION_FILTERS)}
          labels={{
            emptyTitle: "No sessions yet",
            emptyDescription:
              "Sessions appear here once a machine of your organization records one.",
            filteredTitle: "No session matches",
            filteredDescription: "No session of your organization matches the search and filters.",
            clearFilters: CLEAR_FILTERS_LABEL,
          }}
        />
      ) : (
        <>
          {sessions.error !== null && (
            <p className="text-warning-text flex items-start gap-2 text-sm" role="status">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
              The latest reload failed, showing the previous sessions: {sessions.error.message}
            </p>
          )}
          <div className={GRID} aria-busy={sessions.isFetching}>
            {tiles.map((session: SessionListResponse) => (
              <SessionTile key={session.id} session={session} now={loadedAt} />
            ))}
          </div>
          <ListPagination
            pagination={pagination}
            noun="sessions"
            onPageChange={(page: number) => void setFilters({ page })}
          />
        </>
      )}
    </div>
  );
}

/** The wall's outline while the first page loads. */
export function WallLoading() {
  return (
    <div className={GRID} aria-busy="true" aria-label="Loading sessions">
      {Array.from({ length: 6 }, (_, index: number) => (
        <div key={index} className="bg-muted h-64 animate-pulse rounded-xl border" />
      ))}
    </div>
  );
}
