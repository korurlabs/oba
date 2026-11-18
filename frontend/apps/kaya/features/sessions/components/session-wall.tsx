"use client";

import { useCallback, useMemo, useState } from "react";
import { AlertTriangle, ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@oba/ui/sky/card";

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
import { SessionFilters } from "./session-filters";
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

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1>Sessions</h1>
        <p className="text-muted-foreground">
          Every Claude Code session recorded in your organization, newest activity first.
        </p>
      </div>

      <SessionFilters
        filters={filters}
        onChange={onChange}
        options={options.data?.data ?? undefined}
        repositories={repositories.data?.data ?? undefined}
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
        <WallError message={sessions.error.message} onRetry={() => void sessions.refetch()} />
      ) : sessions.data === undefined ? (
        <WallLoading />
      ) : tiles.length === 0 ? (
        <WallEmpty filtered={filters.page > 1 || Object.keys(query).length > 2} />
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
          {pagination !== null && pagination.totalPages > 1 && (
            <nav aria-label="Pages" className="flex items-center justify-between gap-4 text-sm">
              <Button
                variant="outline"
                disabled={pagination.first}
                onClick={() => void setFilters({ page: pagination.currentPage - 1 })}
              >
                <ChevronLeft className="size-4" aria-hidden />
                Newer
              </Button>
              <span className="text-muted-foreground">
                Page {pagination.currentPage} of {pagination.totalPages} ·{" "}
                {pagination.totalElements} sessions
              </span>
              <Button
                variant="outline"
                disabled={pagination.last}
                onClick={() => void setFilters({ page: pagination.currentPage + 1 })}
              >
                Older
                <ChevronRight className="size-4" aria-hidden />
              </Button>
            </nav>
          )}
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

function WallEmpty({ filtered }: { filtered: boolean }) {
  return (
    <Card className="items-center py-12 text-center">
      <CardHeader className="w-full justify-items-center">
        <CardTitle className="text-lg">
          {filtered ? "No session matches" : "No sessions yet"}
        </CardTitle>
        <CardDescription>
          {filtered
            ? "No session of your organization matches these filters."
            : "Sessions appear here once a machine of your organization records one."}
        </CardDescription>
      </CardHeader>
    </Card>
  );
}

function WallError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <Card role="alert" className="border-error/40 bg-error-light gap-4">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <AlertTriangle className="text-error size-5" aria-hidden />
          Could not load the sessions
        </CardTitle>
        <CardDescription className="text-foreground">{message}</CardDescription>
      </CardHeader>
      <CardContent>
        <Button variant="outline" onClick={onRetry}>
          Try again
        </Button>
      </CardContent>
    </Card>
  );
}
