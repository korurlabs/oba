"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { parseAsStringEnum, useQueryState } from "nuqs";

import { Badge } from "@oba/ui/sky/badge";
import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { ListEmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@oba/ui/sky/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { RepositoryReleases } from "@/features/releases/components/repository-releases";
import { ownerLabel } from "@/features/scope/components/owner-labels";
import { useOwners } from "@/features/scope/hooks/use-owners";
import type { Owner } from "@/features/scope/schemas";
import { VersionStatusSchema, type VersionStatus } from "@/features/versions/schemas";
import { useMembers } from "@/features/members/hooks/use-members";
import {
  CLEAR_FILTERS_LABEL,
  FILTER_LABELS,
  ListPagination,
  withChosen,
} from "@/components/common/list-parts";
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
 * repository. The two are different lists, so they are tabs of the toolbar,
 * kept in the URL as `view`; each list brings its own filters.
 */
export function ChangesPage() {
  const [view, setView] = useQueryState(
    "view",
    parseAsStringEnum([...VIEWS]).withDefault("changes")
  );
  const tabs = (
    <TabsList>
      <TabsTrigger value="changes">Proposals</TabsTrigger>
      <TabsTrigger value="releases">Releases</TabsTrigger>
    </TabsList>
  );
  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title="Changes"
        description={
          <p className="text-muted-foreground">
            Config changes proposed in Kaya, on the web or with <code>oba kaya propose</code>. A
            change is released once an admin or owner other than its proposer approves it.
          </p>
        }
      />
      <Tabs
        value={view}
        onValueChange={(next: string) => {
          const found = VIEWS.find((entry) => entry === next);
          if (found) void setView(found);
        }}
      >
        <TabsContent value="changes">
          {view === "changes" && <ChangeList tabs={tabs} />}
        </TabsContent>
        <TabsContent value="releases">
          {view === "releases" && <RepositoryReleases tabs={tabs} />}
        </TabsContent>
      </Tabs>
    </div>
  );
}

/**
 * Every proposed config change of the organization, newest first: drafts,
 * changes in review and released versions, filtered by status and by pack.
 */
function ChangeList({ tabs }: { tabs: ReactNode }) {
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
  const filtered = filters.status !== null || filters.packId !== null;
  const clear = () => void setFilters({ status: null, packId: null, page: 1 });
  const categories: FilterCategory[] = [
    {
      key: "status",
      label: "Status",
      mode: "single",
      options: STATUS_FILTERS.map((status: VersionStatus) => ({
        value: status,
        label: STATUS_BADGES[status].label,
      })),
      value: filters.status,
      onChange: (value: string | null) =>
        void setFilters({
          status: value === null ? null : VersionStatusSchema.parse(value),
          page: 1,
        }),
    },
    {
      key: "packId",
      label: "Pack",
      mode: "single",
      searchable: true,
      options: withChosen(
        (owners ?? []).map((owner: Owner) => ({ value: owner.id, label: ownerLabel(owner) })),
        filters.packId
      ),
      value: filters.packId,
      onChange: (packId: string | null) => void setFilters({ packId, page: 1 }),
    },
  ];

  return (
    <div className="flex flex-col gap-6">
      <ListToolbar
        tabs={tabs}
        filters={<FilterPopover categories={categories} onClear={clear} labels={FILTER_LABELS} />}
      />
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
        <ListEmptyState
          filtered={filtered || filters.page > 1}
          onClearFilters={clear}
          labels={{
            emptyTitle: "No changes yet",
            emptyDescription:
              "Propose a change from a pack's rules, skills or conventions, or with oba kaya propose.",
            filteredTitle: "No change matches",
            filteredDescription: "No change matches these filters.",
            clearFilters: CLEAR_FILTERS_LABEL,
          }}
        />
      ) : (
        <ChangeRows changes={rows} owners={owners} memberNames={memberNames} now={now} />
      )}
      <ListPagination
        pagination={pagination}
        noun="changes"
        onPageChange={(page: number) => void setFilters({ page })}
      />
    </div>
  );
}
