"use client";

import Link from "next/link";
import { parseAsInteger, parseAsString, useQueryStates } from "nuqs";

import { Button } from "@oba/ui/sky/button";
import { NativeSelect } from "@oba/ui/sky/field";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";
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
 */
export function RepositoryReleases() {
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
  if (repositories.error !== null && repositories.data === undefined) {
    return (
      <LoadError
        title="Could not load the repositories"
        message={describeError(repositories.error)}
        onRetry={() => void repositories.refetch()}
      />
    );
  }
  if (repositories.data === undefined) {
    return <TableLoading label="Loading the repositories" />;
  }
  if (!chosen) {
    return (
      <EmptyState
        title="No repository yet"
        description="A repository appears once it records sessions."
      />
    );
  }
  return (
    <div className="flex flex-col gap-4">
      <label className="flex w-fit flex-col gap-1 text-xs font-medium">
        <span className="text-muted-foreground">Repository</span>
        <NativeSelect
          value={repositoryId}
          onChange={(event) => void setParams({ repository: event.target.value, releasePage: 1 })}
        >
          {options.map((option: ScopeOption) => (
            <option
              key={option.value}
              value={option.scope.kind === "REPOSITORY" ? option.scope.repositoryId : ""}
            >
              {option.label}
            </option>
          ))}
        </NativeSelect>
      </label>
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
      {pagination !== null && pagination.totalPages > 1 && (
        <nav aria-label="Release pages" className="flex items-center justify-between gap-4 text-sm">
          <Button
            variant="outline"
            disabled={pagination.first}
            onClick={() => void setParams({ releasePage: pagination.currentPage - 1 })}
          >
            Newer
          </Button>
          <span className="text-muted-foreground">
            Page {pagination.currentPage} of {pagination.totalPages} · {pagination.totalElements}{" "}
            releases
          </span>
          <Button
            variant="outline"
            disabled={pagination.last}
            onClick={() => void setParams({ releasePage: pagination.currentPage + 1 })}
          >
            Older
          </Button>
        </nav>
      )}
    </div>
  );
}
