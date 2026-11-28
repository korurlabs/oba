"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { parseAsInteger, parseAsString, useQueryStates } from "nuqs";

import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@oba/ui/sky/table";
import { FILTER_LABELS, ListPagination } from "@/components/common/list-parts";
import { useRepositories } from "@/features/sessions/hooks/use-sessions";
import { describeError } from "@/lib/api/error-text";
import { dateTimeText } from "@/lib/format-time";

import { useReleases } from "../hooks/use-releases";
import type { ReleaseResponse } from "../schemas";
import {
  releaseHref,
  scopeOptions,
  type ScopeOption,
} from "@/features/scope/components/scope-data";

export const RELEASES_PAGE_SIZE = 25;

/** The repository and page of the Releases tab, kept in the URL. */
const releaseParams = {
  repository: parseAsString,
  releasePage: parseAsInteger.withDefault(1),
};

export function ReleaseRows({
  repositoryId,
  releases,
}: {
  repositoryId: string;
  releases: readonly ReleaseResponse[];
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Release</TableHead>
          <TableHead>Created</TableHead>
          <TableHead>Cause</TableHead>
          <TableHead>Delivered paths</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {releases.map((release: ReleaseResponse) => (
          <TableRow key={release.id}>
            <TableCell className="font-medium">
              <Link href={releaseHref(repositoryId, release.number)} className="hover:underline">
                Release {release.number}
              </Link>
            </TableCell>
            <TableCell className="whitespace-nowrap">{dateTimeText(release.createdAt)}</TableCell>
            <TableCell>
              <Link
                href={`/changes/${encodeURIComponent(release.causeVersionId)}`}
                className="hover:underline"
              >
                The approved version
              </Link>
            </TableCell>
            <TableCell>{release.fileCount}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * The signed releases of one repository, newest first, each linking to the
 * files it delivered and to the version whose approval or pack change made it.
 * The list always shows one repository, the first until another is chosen,
 * so the repository is a filter with that default.
 */
export function RepositoryReleases({ tabs }: { tabs?: ReactNode }) {
  const [{ repository, releasePage }, setParams] = useQueryStates(releaseParams);
  const repositories = useRepositories();
  const options = scopeOptions(repositories.data?.data ?? [], []).repositories;
  const chosen =
    options.find(
      (option: ScopeOption) =>
        option.scope.kind === "REPOSITORY" && option.scope.repositoryId === repository
    ) ?? options[0];
  const repositoryId = chosen?.scope.kind === "REPOSITORY" ? chosen.scope.repositoryId : "";
  const page = Math.max(1, releasePage);
  const releases = useReleases(repositoryId, page, RELEASES_PAGE_SIZE);
  const rows = releases.data?.data ?? [];
  const pagination = releases.data?.pagination ?? null;
  const defaultRepositoryId =
    options[0]?.scope.kind === "REPOSITORY" ? options[0].scope.repositoryId : undefined;
  const repositoryCategory: FilterCategory = {
    key: "repository",
    label: "Repository",
    mode: "single",
    searchable: true,
    options: options.map((option: ScopeOption) => ({
      value: option.scope.kind === "REPOSITORY" ? option.scope.repositoryId : "",
      label: option.label,
    })),
    value: repositoryId === "" ? null : repositoryId,
    defaultValue: defaultRepositoryId,
    // The first repository is the default, so choosing it keeps the URL short.
    onChange: (value: string | null) =>
      void setParams({
        repository: value === null || value === defaultRepositoryId ? null : value,
        releasePage: 1,
      }),
  };
  const toolbar = (
    <ListToolbar
      tabs={tabs}
      filters={
        chosen && (
          <FilterPopover
            categories={[repositoryCategory]}
            onClear={() => void setParams({ repository: null, releasePage: 1 })}
            labels={FILTER_LABELS}
          />
        )
      }
    />
  );
  if (repositories.error !== null && repositories.data === undefined) {
    return (
      <div className="flex flex-col gap-6">
        {toolbar}
        <LoadError
          title="Could not load the repositories"
          message={describeError(repositories.error)}
          onRetry={() => void repositories.refetch()}
        />
      </div>
    );
  }
  if (repositories.data === undefined) {
    return (
      <div className="flex flex-col gap-6">
        {toolbar}
        <TableLoading label="Loading the repositories" />
      </div>
    );
  }
  if (!chosen) {
    return (
      <div className="flex flex-col gap-6">
        {toolbar}
        <EmptyState
          title="No repository yet"
          description="A repository appears once it records sessions."
        />
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-6">
      {toolbar}
      {releases.error !== null && releases.data === undefined ? (
        <LoadError
          title="Could not load the releases"
          message={describeError(releases.error)}
          onRetry={() => void releases.refetch()}
        />
      ) : releases.data === undefined ? (
        <TableLoading label="Loading the releases" />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No release yet"
          description="A release is created when the repository's packs are set or a change to one of its packs is approved."
        />
      ) : (
        <ReleaseRows repositoryId={repositoryId} releases={rows} />
      )}
      <ListPagination
        pagination={pagination}
        noun="releases"
        onPageChange={(page: number) => void setParams({ releasePage: page })}
      />
    </div>
  );
}
