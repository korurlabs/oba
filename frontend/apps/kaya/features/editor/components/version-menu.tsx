"use client";

import { History } from "lucide-react";

import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@oba/ui/sky/dropdown-menu";
import { SOURCE_LABELS } from "@/features/changes/components/change-data";
import { useMembers } from "@/features/members/hooks/use-members";
import { useFileHistory } from "@/features/changes/hooks/use-changes";
import type { FileHistoryResponse } from "@/features/changes/schemas";
import { describeError } from "@/lib/api/error-text";
import { dateTimeText } from "@/lib/format-time";
import { memberName } from "@/lib/identity/member-name";

import { historyEmptyText, historyEntriesOf, historyEntryTitle } from "./version-menu-data";

export type VersionMenuProps = {
  /** Any version of the file's pack, which names the owner. */
  versionId: string;
  path: string;
  /** The version shown read-only; null for the live document. */
  at: string | null;
  onChoose: (entry: FileHistoryResponse) => void;
};

/**
 * The versions of the file's pack that added, changed or deleted
 * it, newest first, with the viewer's own drafts; choosing one opens it.
 */
export function VersionMenu({ versionId, path, at, onChoose }: VersionMenuProps) {
  const history = useFileHistory(versionId, path);
  const members = useMembers();
  const names = new Map(
    (members.data?.data ?? []).map((member) => [member.userId, member.displayName])
  );
  const entries = historyEntriesOf(history.data?.pages ?? []);
  const empty = history.data === undefined ? null : historyEmptyText(entries, !history.hasNextPage);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button size="sm" variant="outline">
          <History aria-hidden />
          Versions
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-80">
        <DropdownMenuLabel>Versions of this file</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {history.error !== null && history.data === undefined ? (
          <div className="flex flex-col gap-2 px-2 py-1.5 text-sm">
            <span className="text-error">{describeError(history.error)}</span>
            <Button size="sm" variant="outline" onClick={() => void history.refetch()}>
              Retry
            </Button>
          </div>
        ) : history.data === undefined ? (
          <div className="text-muted-foreground px-2 py-1.5 text-sm" aria-busy="true">
            Loading the versions
          </div>
        ) : (
          <>
            {entries.map((entry: FileHistoryResponse) => (
              <DropdownMenuItem
                key={entry.versionId}
                onSelect={() => onChoose(entry)}
                className="flex flex-col items-start gap-0.5"
                aria-current={entry.versionId === at}
              >
                <span className="flex items-center gap-2 text-sm">
                  {historyEntryTitle(entry)}
                  {entry.current && <Badge variant="success">Current</Badge>}
                </span>
                <span className="text-muted-foreground text-xs">
                  {SOURCE_LABELS[entry.source]}, {memberName(entry.proposedBy, names)},{" "}
                  {dateTimeText(entry.decidedAt ?? entry.submittedAt)}
                </span>
              </DropdownMenuItem>
            ))}
            {history.hasNextPage && (
              <DropdownMenuItem
                disabled={history.isFetchingNextPage}
                onSelect={(event: Event) => {
                  // Keeps the menu open, so the older entries show below.
                  event.preventDefault();
                  void history.fetchNextPage();
                }}
                aria-busy={history.isFetchingNextPage}
              >
                {history.isFetchingNextPage ? "Loading older versions" : "Older versions"}
              </DropdownMenuItem>
            )}
            {history.isFetchNextPageError && (
              <div className="text-error px-2 py-1.5 text-sm">{describeError(history.error)}</div>
            )}
            {empty !== null && (
              <div className="text-muted-foreground px-2 py-1.5 text-sm">{empty}</div>
            )}
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
