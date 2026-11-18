"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { useQueryClient, type InfiniteData } from "@tanstack/react-query";
import { AlertCircle, GitPullRequest, Loader2 } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { SenseyTable, type RowSelectionState } from "@oba/shared/components/sensey-table";
import { Button } from "@/components/ui/button";
import { useChatOrganizationId } from "@/features/chat/hooks/use-chat-messages";
import {
  PULL_REQUEST_KEYS,
  useGithubPullRequests,
  useGithubRepositories,
} from "../hooks/use-github-pull-requests";
import { usePullRequestPickerColumns } from "../hooks/use-pull-request-picker-columns";
import { usePullRequestPickerFilters } from "../hooks/use-pull-request-picker-filters";
import {
  PULL_REQUEST_SEARCH_MAX,
  type GithubPullRequestPageResponse,
  type GithubPullRequestSummaryResponse,
  type GithubRepositoryResponse,
} from "../schemas";
import { isGithubNotConnected, pullRequestErrorKey } from "./pull-request-errors";
import { PullRequestFilterPanel } from "./pull-request-filter-panel";
import { RepositoryCombobox } from "./repository-combobox";

/** The repository last shared from, preselected next time in this browser. */
const LAST_REPOSITORY_STORAGE_KEY = "kaizen.pull-requests.last-repository";

/** The pull request chosen in the picker, with the repository it belongs to. */
export interface PickedPullRequest {
  githubRepositoryId: number;
  repositoryFullName: string;
  summary: GithubPullRequestSummaryResponse;
}

function readLastRepositoryId(): number | null {
  try {
    const stored = Number(window.localStorage.getItem(LAST_REPOSITORY_STORAGE_KEY));
    return Number.isSafeInteger(stored) && stored > 0 ? stored : null;
    // eslint-disable-next-line no-empty -- reason: the preselected repository is a per-browser convenience; unreadable storage only means the first repository is preselected
  } catch {}
  return null;
}

/** Called when the reader chooses a repository, so a share is not needed to remember it. */
function writeLastRepositoryId(githubRepositoryId: number): void {
  try {
    window.localStorage.setItem(LAST_REPOSITORY_STORAGE_KEY, String(githubRepositoryId));
    // eslint-disable-next-line no-empty -- reason: the preselected repository is a per-browser convenience; when storage refuses the write, the choice still applies on this screen
  } catch {}
}

interface PullRequestPickerProps {
  /** The repository whose pull requests are listed; undefined until one is chosen. */
  repositoryId: number | undefined;
  onRepositoryChange: (githubRepositoryId: number) => void;
  /** The chosen pull request, which may belong to another repository than the listed one. */
  selected: PickedPullRequest | null;
  /** A row clicked or ticked, or null when the chosen row was clicked again. */
  onSelectedChange: (picked: PickedPullRequest | null) => void;
}

/**
 * The pull requests of one repository of the GitHub App installation, last
 * updated first, 30 at a time with "Load more", narrowed by state and author.
 * One row at a time is chosen; clicking it again clears the choice, and a
 * filter that hides the chosen row leaves it chosen. The repository last chosen
 * in this browser is preselected, else the first.
 */
export function PullRequestPicker({
  repositoryId,
  onRepositoryChange,
  selected,
  onSelectedChange,
}: PullRequestPickerProps) {
  const t = useTranslations("pullRequests");
  const repositoriesQuery = useGithubRepositories();
  const repositories = useMemo(() => repositoriesQuery.data?.data ?? [], [repositoriesQuery.data]);
  const [search, setSearch] = useState("");

  // Storage is read after mount, so the server and the hydrating client
  // render the same markup; no list is fetched before the choice is known.
  const [storedId, setStoredId] = useState<number | null | undefined>(undefined);
  useEffect(() => setStoredId(readLastRepositoryId()), []);

  useEffect(() => {
    if (repositoryId !== undefined || storedId === undefined) return;
    const preselected =
      repositories.find((repository) => repository.githubRepositoryId === storedId) ?? repositories[0];
    if (preselected) onRepositoryChange(preselected.githubRepositoryId);
  }, [repositoryId, storedId, repositories, onRepositoryChange]);

  const repository = repositories.find((candidate) => candidate.githubRepositoryId === repositoryId);
  const githubRepositoryId = repository?.githubRepositoryId;

  const picker = usePullRequestPickerFilters();
  const listQuery = useGithubPullRequests(githubRepositoryId, search, picker.requestFilters);

  // A row updated while paging can come back on the next page as well.
  const rows = useMemo(() => {
    const seen = new Set<number>();
    return (listQuery.data?.pages ?? [])
      .flatMap((page) => page.items)
      .filter((row) => {
        if (seen.has(row.number)) return false;
        seen.add(row.number);
        return true;
      });
  }, [listQuery.data]);

  // Every list seen for this repository, not only the current one: choosing an
  // author narrows the rows, and a login that no contributor entry holds would
  // otherwise leave the Author options with it.
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const loadedAuthors = useMemo(() => {
    if (!orgId || githubRepositoryId === undefined) return [];
    const lists = queryClient.getQueriesData<InfiniteData<GithubPullRequestPageResponse>>({
      queryKey: PULL_REQUEST_KEYS.repositoryLists(orgId, githubRepositoryId),
    });
    const cachedRows = lists.flatMap(([, list]) => (list?.pages ?? []).flatMap((page) => page.items));
    return [...cachedRows, ...rows].map((row) => row.authorLogin);
  }, [queryClient, orgId, githubRepositoryId, rows]);

  const handleRepositoryChange = useCallback(
    (githubRepositoryId: number) => {
      writeLastRepositoryId(githubRepositoryId);
      onRepositoryChange(githubRepositoryId);
    },
    [onRepositoryChange],
  );

  // GitHub refuses a longer search, so the text is cut at the server's limit.
  const handleSearchChange = useCallback((value: string | undefined) => {
    setSearch((value ?? "").slice(0, PULL_REQUEST_SEARCH_MAX));
  }, []);

  return (
    <section
      aria-labelledby="pull-request-picker-title"
      className="border-border bg-card flex min-w-0 flex-col rounded-lg border"
    >
      <div className="border-border border-b px-4 py-3">
        <h2 id="pull-request-picker-title" className="text-foreground text-sm font-semibold">
          {t("picker.title")}
        </h2>
        <p className="text-muted-foreground text-xs">{t("picker.description")}</p>
      </div>

      {repositoriesQuery.isError ? (
        <RepositoriesError
          error={repositoriesQuery.error}
          isFetching={repositoriesQuery.isFetching}
          onRetry={() => void repositoriesQuery.refetch()}
        />
      ) : repositoriesQuery.isSuccess && repositories.length === 0 ? (
        <PickerMessage icon={<GitPullRequest className="text-muted-foreground h-6 w-6" />}>
          {t("picker.noRepositories")}
        </PickerMessage>
      ) : (
        <>
          <div className="border-border flex flex-wrap items-center gap-2 border-b px-4 py-2">
            <RepositoryCombobox
              repositories={repositories}
              value={repositoryId}
              onValueChange={handleRepositoryChange}
              disabled={repositories.length === 0}
              className="w-full max-w-full sm:w-80"
            />
            {/* Together, so the row wraps before the search input and the funnel
                are squeezed, and the funnel never lands on a row of its own. */}
            <div className="flex min-w-40 flex-1 items-center gap-2">
              <SenseySearchInput
                value={search}
                onSearchChange={handleSearchChange}
                placeholder={t("picker.searchPlaceholder")}
                className="min-w-0 flex-1 sm:w-64 sm:flex-initial"
              />
              <div className="hidden flex-1 sm:block" />
              <PullRequestFilterPanel
                filters={picker.filters}
                onChange={picker.setFilters}
                onClear={picker.clearFilters}
                activeCount={picker.activeFilterCount}
                githubRepositoryId={githubRepositoryId}
                loadedAuthors={loadedAuthors}
                onSavePreferences={() => picker.prefs.saveFilters(picker.filters)}
                onResetPreferences={async () => {
                  if (await picker.prefs.resetFilters()) {
                    picker.clearFilters();
                  }
                }}
                saveDisabled={!picker.prefs.hasFilterChanges(picker.filters)}
                isSaving={picker.prefs.isSaving}
                hasSavedPreferences={picker.prefs.hasSavedFilters}
              />
            </div>
          </div>
          <PullRequestList
            repository={repository}
            isLoadingRepositories={repositoriesQuery.isPending || repositoryId === undefined}
            listQuery={listQuery}
            rows={rows}
            search={search}
            hasActiveFilters={picker.activeFilterCount > 0}
            selected={selected}
            onSelectedChange={onSelectedChange}
          />
        </>
      )}
    </section>
  );
}

// ── List ──────────────────────────────────────────────────────────────

function PullRequestList({
  repository,
  isLoadingRepositories,
  listQuery,
  rows,
  search,
  hasActiveFilters,
  selected,
  onSelectedChange,
}: {
  repository: GithubRepositoryResponse | undefined;
  isLoadingRepositories: boolean;
  listQuery: ReturnType<typeof useGithubPullRequests>;
  rows: GithubPullRequestSummaryResponse[];
  search: string;
  hasActiveFilters: boolean;
  selected: PickedPullRequest | null;
  onSelectedChange: (picked: PickedPullRequest | null) => void;
}) {
  const t = useTranslations("pullRequests");
  const columns = usePullRequestPickerColumns();

  const selectedNumber =
    selected && repository && selected.githubRepositoryId === repository.githubRepositoryId
      ? selected.summary.number
      : undefined;
  const rowSelection = useMemo<RowSelectionState>(
    () => (selectedNumber !== undefined ? { [String(selectedNumber)]: true } : {}),
    [selectedNumber],
  );

  const pick = useCallback(
    (row: GithubPullRequestSummaryResponse) => {
      if (!repository) return;
      onSelectedChange({
        githubRepositoryId: repository.githubRepositoryId,
        repositoryFullName: repository.fullName,
        summary: row,
      });
    },
    [repository, onSelectedChange],
  );

  const toggleRow = useCallback(
    (row: GithubPullRequestSummaryResponse) => {
      if (row.number === selectedNumber) onSelectedChange(null);
      else pick(row);
    },
    [selectedNumber, onSelectedChange, pick],
  );

  // Only one row is chosen: a newly ticked row replaces the chosen one, and
  // ticking every row at once (the header checkbox) changes nothing.
  const handleRowSelectionChange = useCallback(
    (next: RowSelectionState) => {
      const ticked = Object.keys(next).filter((id) => next[id] && id !== String(selectedNumber));
      if (ticked.length === 1) {
        const row = rows.find((candidate) => String(candidate.number) === ticked[0]);
        if (row) pick(row);
        return;
      }
      if (ticked.length === 0 && selectedNumber !== undefined && !next[String(selectedNumber)]) {
        onSelectedChange(null);
      }
    },
    [rows, selectedNumber, pick, onSelectedChange],
  );

  if (listQuery.isError) {
    return (
      <PickerError
        title={t("picker.loadError")}
        error={listQuery.error}
        isFetching={listQuery.isFetching}
        onRetry={() => void listQuery.refetch()}
      />
    );
  }

  const emptyMessage = (
    <p className="text-muted-foreground text-sm">
      {hasActiveFilters ? t("picker.noFilterResults") : search ? t("picker.noResults") : t("picker.empty")}
    </p>
  );
  const resultsLimited = listQuery.data?.pages.some((page) => page.resultsLimited) ?? false;

  return (
    // A bounded height, so a long list scrolls inside the section.
    <div className="flex max-h-[28rem] min-h-0 flex-col gap-3 p-3 lg:max-h-[calc(100vh-14rem)]">
      <SenseyTable<GithubPullRequestSummaryResponse>
        columns={columns}
        data={rows}
        isLoading={isLoadingRepositories || listQuery.isPending}
        emptyMessage={emptyMessage}
        onRowClick={toggleRow}
        enableSelection
        rowSelection={rowSelection}
        onRowSelectionChange={handleRowSelectionChange}
        getRowId={(row) => String(row.number)}
      />
      {resultsLimited && (
        <p className="text-muted-foreground text-center text-xs">{t("picker.resultsLimited")}</p>
      )}
      {listQuery.isFetchNextPageError && (
        <p role="alert" className="text-destructive flex items-center justify-center gap-1.5 text-xs">
          <AlertCircle className="h-3.5 w-3.5 shrink-0" />
          {t("picker.loadMoreError")}
        </p>
      )}
      {listQuery.hasNextPage && (
        <div className="flex justify-center">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={listQuery.isFetchingNextPage}
            onClick={() => void listQuery.fetchNextPage()}
          >
            {listQuery.isFetchingNextPage && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {t("picker.loadMore")}
          </Button>
        </div>
      )}
    </div>
  );
}

// ── States ────────────────────────────────────────────────────────────

function PickerMessage({ icon, children }: { icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center p-6 text-center">
      {icon}
      <div className="mt-2 max-w-md">{children}</div>
    </div>
  );
}

/** GitHub not set up is a state of the organization, not a failure: it gets no Retry. */
function RepositoriesError({
  error,
  isFetching,
  onRetry,
}: {
  error: Error;
  isFetching: boolean;
  onRetry: () => void;
}) {
  const t = useTranslations("pullRequests");
  if (isGithubNotConnected(error)) {
    return (
      <PickerMessage icon={<GitPullRequest className="text-muted-foreground h-6 w-6" />}>
        <p className="text-foreground text-sm font-medium">{t("errors.notConnected")}</p>
        <p className="text-muted-foreground mt-1 text-xs">{t("picker.notConnectedDescription")}</p>
      </PickerMessage>
    );
  }
  return (
    <PickerError title={t("picker.repositoriesError")} error={error} isFetching={isFetching} onRetry={onRetry} />
  );
}

/** A failed GitHub read: what went wrong, in the reader's words where the code is known, and Retry. */
export function PickerError({
  title,
  error,
  isFetching,
  onRetry,
}: {
  title: string;
  error: Error;
  isFetching: boolean;
  onRetry: () => void;
}) {
  const t = useTranslations("pullRequests");
  const errorKey = pullRequestErrorKey(error);
  const detail = errorKey ? t(`errors.${errorKey}`) : error.message;
  return (
    <div role="alert" className="flex flex-col items-center justify-center p-6 text-center">
      <AlertCircle className="text-destructive h-6 w-6" />
      <p className="text-foreground mt-2 text-sm font-medium">{title}</p>
      {detail && <p className="text-muted-foreground mt-1 max-w-md text-xs">{detail}</p>}
      <Button type="button" variant="outline" size="sm" className="mt-3" disabled={isFetching} onClick={onRetry}>
        {t("retry")}
      </Button>
    </div>
  );
}
