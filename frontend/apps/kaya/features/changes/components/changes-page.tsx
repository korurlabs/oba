"use client";

import Link from "next/link";
import { parseAsStringEnum, useQueryState } from "nuqs";

import { Badge } from "@oba/ui/sky/badge";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { RepositoryReleases } from "@/features/releases/components/repository-releases";
import { ownerLabel } from "@/features/scope/components/owner-labels";
import { useOwners } from "@/features/scope/hooks/use-owners";
import type { Owner } from "@/features/scope/schemas";
import type { VersionStatus } from "@/features/versions/schemas";
import { useMembers } from "@/features/members/hooks/use-members";
import { describeError } from "@/lib/api/error-text";
import { ageText } from "@/lib/format-time";
import { memberName } from "@/lib/identity/member-name";
import { useNow } from "@/lib/use-now";

import { useChangeFilterParams } from "../hooks/use-change-filter-params";
import { useChanges } from "../hooks/use-changes";
import type { ChangeResponse } from "../schemas";
import {
  SOURCE_LABELS,
  STATUS_BADGES,
  STATUS_FILTERS,
  approverText,
  toChangeQuery,
  versionOwnerLabel,
} from "./change-data";

export type ChangeRowsProps = {
  changes: readonly ChangeResponse[];
  /** Every pack, to name each change's pack; undefined while they load. */
  owners: readonly Owner[] | undefined;
  memberNames: ReadonlyMap<string, string>;
  now: number;
};

/** The changes as table rows: pack and number, status, source, proposer, reason, approver. */
export function ChangeRows({ changes, owners, memberNames, now }: ChangeRowsProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Change</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Source</TableHead>
          <TableHead>Proposed by</TableHead>
          <TableHead>Reason</TableHead>
          <TableHead>Approval</TableHead>
          <TableHead>Updated</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {changes.map((change: ChangeResponse) => {
          const { version } = change;
          const status = STATUS_BADGES[version.status];
          return (
            <TableRow key={version.id} className="align-top">
              <TableCell className="font-medium">
                <Link
                  href={`/changes/${encodeURIComponent(version.id)}`}
                  className="hover:underline"
                >
                  {versionOwnerLabel(version, owners)} #{version.number}
                </Link>
              </TableCell>
              <TableCell>
                <Badge variant={status.variant}>{status.label}</Badge>
              </TableCell>
              <TableCell className="whitespace-nowrap">{SOURCE_LABELS[version.source]}</TableCell>
              <TableCell className="whitespace-nowrap">
                {memberName(version.proposedBy, memberNames)}
              </TableCell>
              <TableCell className="max-w-sm break-words whitespace-normal">
                {version.reason ?? <span className="text-muted-foreground">Not submitted</span>}
              </TableCell>
              <TableCell className="whitespace-nowrap">
                <div>{approverText(change.requiredRole)}</div>
                <div className="text-muted-foreground text-xs">
                  {change.reviews.length} {change.reviews.length === 1 ? "review" : "reviews"}
                </div>
              </TableCell>
              <TableCell className="whitespace-nowrap">
                {ageText(version.updatedAt ?? version.createdAt, now)}
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

const VIEWS = ["changes", "releases"] as const;

/**
 * The organization's config changes, and the releases they produced per
 * repository; the tab lives in the URL as `view`.
 */
export function ChangesPage() {
  const [view, setView] = useQueryState(
    "view",
    parseAsStringEnum([...VIEWS]).withDefault("changes")
  );
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1>Changes</h1>
        <p className="text-muted-foreground">
          Config changes proposed in Kaya, on the web or with <code>kaya propose</code>. A change is
          released once an admin or owner other than its proposer approves it.
        </p>
      </div>
      <Tabs
        value={view}
        onValueChange={(next: string) => {
          const found = VIEWS.find((entry) => entry === next);
          if (found) void setView(found);
        }}
      >
        <TabsList>
          <TabsTrigger value="changes">Changes</TabsTrigger>
          <TabsTrigger value="releases">Releases</TabsTrigger>
        </TabsList>
        <TabsContent value="changes">{view === "changes" && <ChangeList />}</TabsContent>
        <TabsContent value="releases">{view === "releases" && <RepositoryReleases />}</TabsContent>
      </Tabs>
    </div>
  );
}

/**
 * Every proposed config change of the organization, newest first: drafts,
 * changes in review and released versions, filtered by status and by pack.
 */
function ChangeList() {
  const [filters, setFilters] = useChangeFilterParams();
  const changes = useChanges(toChangeQuery(filters));
  const { owners, error: ownersError } = useOwners();
  const members = useMembers();
  const now = useNow(60_000);
  const rows = changes.data?.data ?? [];
  const pagination = changes.data?.pagination ?? null;
  const memberNames = new Map(
    (members.data?.data ?? []).map((member) => [member.userId, member.displayName])
  );
  const lookupError = members.error ?? ownersError;
  const selectedOwner = filters.packId ?? "";

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-xs font-medium">
          <span className="text-muted-foreground">Status</span>
          <NativeSelect
            value={filters.status ?? ""}
            onChange={(event) =>
              void setFilters({
                status: event.target.value === "" ? null : (event.target.value as VersionStatus),
                page: 1,
              })
            }
          >
            <option value="">All</option>
            {STATUS_FILTERS.map((status: VersionStatus) => (
              <option key={status} value={status}>
                {STATUS_BADGES[status].label}
              </option>
            ))}
          </NativeSelect>
        </label>
        <label className="flex flex-col gap-1 text-xs font-medium">
          <span className="text-muted-foreground">Pack</span>
          <NativeSelect
            value={selectedOwner}
            onChange={(event) => {
              void setFilters({
                packId: event.target.value === "" ? null : event.target.value,
                page: 1,
              });
            }}
          >
            <option value="">Every pack</option>
            {(owners ?? []).map((owner: Owner) => (
              <option key={owner.id} value={owner.id}>
                {ownerLabel(owner)}
              </option>
            ))}
          </NativeSelect>
        </label>
      </div>
      {lookupError !== null && (
        <p className="text-warning-text text-sm" role="status">
          Names of members or packs could not be loaded: {describeError(lookupError)}
        </p>
      )}
      {changes.error !== null && changes.data === undefined ? (
        <LoadError
          title="Could not load the changes"
          message={describeError(changes.error)}
          onRetry={() => void changes.refetch()}
        />
      ) : changes.data === undefined ? (
        <TableLoading label="Loading the changes" />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No change"
          description={
            filters.status === null && filters.packId === null
              ? "No config change has been proposed yet."
              : "No change matches these filters."
          }
        />
      ) : (
        <ChangeRows changes={rows} owners={owners} memberNames={memberNames} now={now} />
      )}
      {pagination !== null && pagination.totalPages > 1 && (
        <nav aria-label="Pages" className="flex items-center justify-between gap-4 text-sm">
          <Button
            variant="outline"
            disabled={pagination.first}
            onClick={() => void setFilters({ page: pagination.currentPage - 1 })}
          >
            Newer
          </Button>
          <span className="text-muted-foreground">
            Page {pagination.currentPage} of {pagination.totalPages} · {pagination.totalElements}{" "}
            changes
          </span>
          <Button
            variant="outline"
            disabled={pagination.last}
            onClick={() => void setFilters({ page: pagination.currentPage + 1 })}
          >
            Older
          </Button>
        </nav>
      )}
    </div>
  );
}
