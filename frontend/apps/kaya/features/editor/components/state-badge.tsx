import Link from "next/link";

import { Alert, AlertDescription, AlertTitle } from "@oba/ui/sky/alert";
import { Badge } from "@oba/ui/sky/badge";
import { dateTimeText } from "@/lib/format-time";
import { memberName } from "@/lib/identity/member-name";

import { stateBadge, type DocumentState } from "./document-state";

/** The document's state in its header. */
export function StateBadge({ state }: { state: DocumentState }) {
  const { label, variant } = stateBadge(state);
  return <Badge variant={variant}>{label}</Badge>;
}

/**
 * What a state says beyond its badge: the reviewer's comment on a change sent
 * back, and for a file in review since when and where the change is.
 */
export function StateNote({
  state,
  memberNames,
}: {
  state: DocumentState;
  memberNames: ReadonlyMap<string, string>;
}) {
  if (state.kind === "CHANGES_REQUESTED") {
    const review = state.review;
    return (
      <Alert>
        <AlertTitle>Changes requested</AlertTitle>
        <AlertDescription>
          {review ? (
            <>
              <p>
                {memberName(review.reviewerId, memberNames)} sent this change back on{" "}
                {dateTimeText(review.createdAt)}.
              </p>
              {review.comment && (
                <p className="text-foreground break-words whitespace-pre-wrap">{review.comment}</p>
              )}
            </>
          ) : (
            <p>A reviewer sent this change back. Change it and submit it again.</p>
          )}
        </AlertDescription>
      </Alert>
    );
  }
  if (state.kind === "IN_REVIEW") {
    const { entry } = state;
    return (
      <Alert>
        <AlertTitle>
          In review since {dateTimeText(entry.submittedAt)}, version {entry.number}
        </AlertTitle>
        <AlertDescription>
          <p>
            A change to this file waits for review; this is the current version.{" "}
            <Link
              href={`/changes/${encodeURIComponent(entry.versionId)}`}
              className="text-foreground underline underline-offset-2"
            >
              Open the change
            </Link>
          </p>
        </AlertDescription>
      </Alert>
    );
  }
  return null;
}
