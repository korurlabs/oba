"use client";

import Link from "next/link";
import { useState } from "react";

import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { InfoTip } from "@oba/ui/sky/info-tip";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";
import { useMembers } from "@/features/members/hooks/use-members";
import { describeError } from "@/lib/api/error-text";
import { dateTimeText } from "@/lib/format-time";

import { usePackVersions } from "../hooks/use-packs";
import { versionRows, type VersionRow } from "./pack-data";

const VERSIONS_HELP =
  "Open: a change being edited by its proposer. In review: submitted and waiting for an admin or owner. Approved: the pack's current version, delivered to its repositories. Superseded: approved earlier and replaced by a newer version.";

/** A pack's versions, newest first and paged, each opening its change. */
export function PackVersions({ packId }: { packId: string }) {
  const [page, setPage] = useState(1);
  const versions = usePackVersions(packId, page);
  const members = useMembers();
  const names = new Map(
    (members.data?.data ?? []).map((member) => [member.userId, member.displayName])
  );
  const rows = versionRows(versions.data?.data ?? [], names);
  const pagination = versions.data?.pagination ?? null;

  return (
    <section className="flex flex-col gap-2">
      <div className="flex items-center gap-1.5">
        <h3 className="text-base font-semibold">Versions</h3>
        <InfoTip label="What do the statuses mean?">{VERSIONS_HELP}</InfoTip>
      </div>
      {versions.error !== null && versions.data === undefined ? (
        <LoadError
          title="Could not load the versions"
          message={describeError(versions.error)}
          onRetry={() => void versions.refetch()}
        />
      ) : versions.data === undefined ? (
        <TableLoading label="Loading the versions" />
      ) : rows.length === 0 ? (
        <p className="text-muted-foreground text-sm">No version yet.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Version</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Created</TableHead>
              <TableHead>By</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row: VersionRow) => (
              <TableRow key={row.id}>
                <TableCell className="font-medium">
                  <Link href={`/changes/${encodeURIComponent(row.id)}`} className="hover:underline">
                    #{row.number}
                  </Link>
                </TableCell>
                <TableCell>
                  <Badge variant={row.statusVariant}>{row.statusLabel}</Badge>
                </TableCell>
                <TableCell className="whitespace-nowrap">{dateTimeText(row.createdAt)}</TableCell>
                <TableCell className="whitespace-nowrap">{row.by}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {pagination !== null && pagination.totalPages > 1 && (
        <nav aria-label="Pages" className="flex items-center justify-between gap-4 text-sm">
          <Button
            variant="outline"
            disabled={pagination.first}
            onClick={() => setPage(pagination.currentPage - 1)}
          >
            Newer
          </Button>
          <span className="text-muted-foreground">
            Page {pagination.currentPage} of {pagination.totalPages}, {pagination.totalElements}{" "}
            versions
          </span>
          <Button
            variant="outline"
            disabled={pagination.last}
            onClick={() => setPage(pagination.currentPage + 1)}
          >
            Older
          </Button>
        </nav>
      )}
    </section>
  );
}
