"use client";

import { useState, type FormEvent, type ReactNode } from "react";

import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@oba/ui/sky/dialog";
import { Field, Textarea } from "@oba/ui/sky/field";
import { firstIssue } from "@/lib/forms";
import { describeError } from "@/lib/api/error-text";

import { useReviewVersion } from "../hooks/use-changes";
import { CHANGE_LIMITS, reviewRequestSchema, type ReviewDecision } from "../schemas";
import type { ActionState } from "./change-data";

/** A refused control stays visible but greyed out, with why next to it. */
function DisabledAction({ label, reason }: { label: string; reason: string }) {
  return (
    <div className="flex max-w-sm flex-col items-start gap-1">
      <Button variant="outline" size="sm" disabled>
        {label}
      </Button>
      <span className="text-muted-foreground text-xs">{reason}</span>
    </div>
  );
}

type ReviewDialogProps = {
  versionId: string;
  decision: Exclude<ReviewDecision, "COMMENT">;
  trigger: ReactNode;
  title: string;
  description: ReactNode;
  confirmLabel: string;
};

/** Approve with an optional comment, or reject with a required one. */
function ReviewDialog({
  versionId,
  decision,
  trigger,
  title,
  description,
  confirmLabel,
}: ReviewDialogProps) {
  const review = useReviewVersion();
  const [open, setOpen] = useState(false);
  const [comment, setComment] = useState("");
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const parsed = reviewRequestSchema.safeParse({ decision, comment });
    if (!parsed.success) {
      setError(firstIssue(parsed.error));
      return;
    }
    setError(null);
    try {
      await review.mutateAsync({ versionId, data: parsed.data });
      setOpen(false);
      setComment("");
    } catch (failure: unknown) {
      setError(describeError(failure));
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next: boolean) => {
        setOpen(next);
        if (!next) setError(null);
      }}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
            <DialogDescription asChild>
              <div className="flex flex-col gap-2">{description}</div>
            </DialogDescription>
          </DialogHeader>
          <Field
            label={decision === "APPROVE" ? "Comment (optional)" : "Why it is rejected"}
            hint={`At most ${CHANGE_LIMITS.commentMax} characters; the proposer reads it.`}
          >
            <Textarea
              value={comment}
              maxLength={CHANGE_LIMITS.commentMax}
              onChange={(event) => setComment(event.target.value)}
            />
          </Field>
          {error !== null && (
            <p className="text-error text-sm" role="alert">
              {error}
            </p>
          )}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
              disabled={review.isPending}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant={decision === "REJECT" ? "destructive" : "default"}
              disabled={review.isPending}
            >
              {confirmLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export type ReviewButtonsProps = {
  versionId: string;
  approve: ActionState;
  reject: ActionState;
  /** Names the repositories that get a release on approval. */
  approveDescription: string;
};

/** Approve and Reject, each shown, greyed out with a reason, or left out. */
export function ReviewButtons({
  versionId,
  approve,
  reject,
  approveDescription,
}: ReviewButtonsProps) {
  return (
    <div className="flex flex-wrap items-start gap-3">
      {approve.kind === "enabled" && (
        <ReviewDialog
          versionId={versionId}
          decision="APPROVE"
          trigger={<Button size="sm">Approve</Button>}
          title="Approve this change"
          description={<p>{approveDescription}</p>}
          confirmLabel="Approve"
        />
      )}
      {approve.kind === "disabled" && <DisabledAction label="Approve" reason={approve.reason} />}
      {reject.kind === "enabled" && (
        <ReviewDialog
          versionId={versionId}
          decision="REJECT"
          trigger={
            <Button size="sm" variant="outline">
              Reject
            </Button>
          }
          title="Reject this change"
          description={
            <p>
              The change goes back to its proposer as a draft, with your comment. They can edit it
              and submit it again.
            </p>
          }
          confirmLabel="Reject"
        />
      )}
      {reject.kind === "disabled" && <DisabledAction label="Reject" reason={reject.reason} />}
    </div>
  );
}

/** A comment on a change in review; the status does not change. */
export function CommentForm({ versionId, state }: { versionId: string; state: ActionState }) {
  const review = useReviewVersion();
  const [comment, setComment] = useState("");
  const [error, setError] = useState<string | null>(null);

  if (state.kind === "hidden") return null;
  if (state.kind === "disabled") {
    return <p className="text-muted-foreground text-sm">{state.reason}</p>;
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const parsed = reviewRequestSchema.safeParse({ decision: "COMMENT", comment });
    if (!parsed.success) {
      setError(firstIssue(parsed.error));
      return;
    }
    setError(null);
    try {
      await review.mutateAsync({ versionId, data: parsed.data });
      setComment("");
    } catch (failure: unknown) {
      setError(describeError(failure));
    }
  };

  return (
    <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-2">
      <Field label="Add a comment" hint={`At most ${CHANGE_LIMITS.commentMax} characters.`}>
        <Textarea
          value={comment}
          maxLength={CHANGE_LIMITS.commentMax}
          onChange={(event) => setComment(event.target.value)}
        />
      </Field>
      {error !== null && (
        <p className="text-error text-sm" role="alert">
          {error}
        </p>
      )}
      <div>
        <Button type="submit" size="sm" variant="outline" disabled={review.isPending}>
          Comment
        </Button>
      </div>
    </form>
  );
}
