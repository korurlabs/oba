"use client";

import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { LoadError } from "@oba/ui/sky/page-states";
import { DECISION_LABELS, SOURCE_LABELS } from "@/features/changes/components/change-data";
import { useFileHistory } from "@/features/changes/hooks/use-changes";
import type { FileHistoryResponse, ReviewResponse } from "@/features/changes/schemas";
import { describeError } from "@/lib/api/error-text";
import { dateTimeText } from "@/lib/format-time";
import { memberName } from "@/lib/identity/member-name";

import {
  decisionText,
  historyEmptyText,
  historyEntriesOf,
  historyEntryTitle,
} from "./version-menu-data";

export type HistoryPanelProps = {
  /** Any version of the file's pack, which names the owner. */
  versionId: string;
  path: string;
  memberNames: ReadonlyMap<string, string>;
  /** Opens a version, as the version menu does. */
  onChoose: (entry: FileHistoryResponse) => void;
};

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-1.5 text-xs">
      <dt className="text-muted-foreground">{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function HistoryEntry({
  entry,
  memberNames,
  onChoose,
}: {
  entry: FileHistoryResponse;
  memberNames: ReadonlyMap<string, string>;
  onChoose: (entry: FileHistoryResponse) => void;
}) {
  const decided = decisionText(entry);
  return (
    <li className="flex flex-col gap-2 px-3 py-2.5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="flex flex-wrap items-center gap-2 text-sm font-medium">
          {historyEntryTitle(entry)}
          {entry.current && <Badge variant="success">Current</Badge>}
        </span>
        <Button size="sm" variant="ghost" onClick={() => onChoose(entry)}>
          Open
        </Button>
      </div>
      <dl className="flex flex-col gap-0.5">
        <Meta
          label="Proposed by"
          value={`${memberName(entry.proposedBy, memberNames)}, ${SOURCE_LABELS[entry.source]}`}
        />
        {entry.submittedAt && <Meta label="Submitted" value={dateTimeText(entry.submittedAt)} />}
        {decided && entry.decidedAt && (
          <Meta label={decided} value={dateTimeText(entry.decidedAt)} />
        )}
      </dl>
      {entry.reason && <p className="text-sm break-words whitespace-pre-wrap">{entry.reason}</p>}
      {entry.reviews.length > 0 && (
        <ol className="flex flex-col gap-1.5 border-l pl-3" aria-label="Reviews">
          {entry.reviews.map((review: ReviewResponse) => (
            <li key={review.id} className="flex flex-col gap-0.5">
              <span className="text-xs">
                <span className="font-medium">{memberName(review.reviewerId, memberNames)}</span>{" "}
                {DECISION_LABELS[review.decision]}{" "}
                <span className="text-muted-foreground">{dateTimeText(review.createdAt)}</span>
              </span>
              {review.comment && (
                <p className="text-sm break-words whitespace-pre-wrap">{review.comment}</p>
              )}
            </li>
          ))}
        </ol>
      )}
    </li>
  );
}

/**
 * Who proposed, reviewed and approved each version of the file's pack that
 * changed it, newest first, with the reason and every review.
 */
export function HistoryPanel({ versionId, path, memberNames, onChoose }: HistoryPanelProps) {
  const history = useFileHistory(versionId, path);
  if (history.error !== null && history.data === undefined) {
    return (
      <LoadError
        title="Could not load the history"
        message={describeError(history.error)}
        onRetry={() => void history.refetch()}
      />
    );
  }
  if (history.data === undefined) {
    return <div className="bg-muted h-24 animate-pulse rounded-md" aria-busy="true" />;
  }
  const entries = historyEntriesOf(history.data.pages);
  const empty = historyEmptyText(entries, !history.hasNextPage);
  return (
    <div className="flex flex-col gap-3">
      {entries.length > 0 && (
        <ol className="flex flex-col divide-y rounded-md border" aria-label="Versions">
          {entries.map((entry: FileHistoryResponse) => (
            <HistoryEntry
              key={entry.versionId}
              entry={entry}
              memberNames={memberNames}
              onChoose={onChoose}
            />
          ))}
        </ol>
      )}
      {empty !== null && <p className="text-muted-foreground text-sm">{empty}</p>}
      {history.hasNextPage && (
        <Button
          size="sm"
          variant="outline"
          className="w-fit"
          disabled={history.isFetchingNextPage}
          aria-busy={history.isFetchingNextPage}
          onClick={() => void history.fetchNextPage()}
        >
          {history.isFetchingNextPage ? "Loading older versions" : "Older versions"}
        </Button>
      )}
      {history.isFetchNextPageError && (
        <p className="text-error text-sm" role="alert">
          {describeError(history.error)}
        </p>
      )}
    </div>
  );
}
