"use client";

import { useState, type Ref } from "react";

import { Button } from "@oba/ui/sky/button";
import { Field, Textarea } from "@oba/ui/sky/field";

import { useGiveFeedback } from "../hooks/use-plans";
import { PLAN_LIMITS, planFeedbackRequestSchema, type PlanFeedbackDecision } from "../schemas";
import { DECISION_BUTTONS, feedbackDecisions, planRefusalText } from "./plan-detail-data";
import { rangeText, type LineRange } from "./line-selection";

export type PlanFeedbackFormProps = {
  planId: string;
  versionNumber: number;
  isLatestVersion: boolean;
  canApprove: boolean;
  /** The lines the feedback is anchored to; null for feedback on the whole plan. */
  range: LineRange | null;
  /** Up to 500 characters of the selected lines. */
  quote?: string;
  /** Runs after the feedback was stored, such as to clear the line selection. */
  onSaved?: () => void;
  onCancel?: () => void;
  textareaRef?: Ref<HTMLTextAreaElement>;
};

/**
 * Feedback on one version: request changes, comment or approve, each with the
 * reviewer's words. The server decides; a refusal is shown in the form in
 * plain words and the plan reloads, since it often means a newer version.
 */
export function PlanFeedbackForm({
  planId,
  versionNumber,
  isLatestVersion,
  canApprove,
  range,
  quote,
  onSaved,
  onCancel,
  textareaRef,
}: PlanFeedbackFormProps) {
  const give = useGiveFeedback();
  const [comment, setComment] = useState("");
  const [error, setError] = useState<string | null>(null);
  const decisions = feedbackDecisions(canApprove, isLatestVersion);

  const submit = async (decision: PlanFeedbackDecision) => {
    const parsed = planFeedbackRequestSchema.safeParse({
      versionNumber,
      decision,
      comment,
      lineStart: range?.start,
      lineEnd: range?.end,
      quote: range === null || quote === undefined || quote === "" ? undefined : quote,
    });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? "Check the feedback entered.");
      return;
    }
    setError(null);
    try {
      await give.mutateAsync({ planId, data: parsed.data });
      setComment("");
      onSaved?.();
    } catch (failure: unknown) {
      setError(planRefusalText(failure, "feedback"));
    }
  };

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        void submit("COMMENT");
      }}
      className="flex flex-col gap-2"
      aria-label={range === null ? "Feedback on the plan" : `Feedback on ${rangeText(range)}`}
    >
      {range !== null && (
        <p className="text-muted-foreground text-xs">
          {rangeText(range)} of version {versionNumber}
        </p>
      )}
      <Field
        label={range === null ? "Feedback on the plan" : "Comment on these lines"}
        hint={`At most ${PLAN_LIMITS.commentMax} characters; Claude reads it with your name. Approve needs no comment.`}
      >
        <Textarea
          ref={textareaRef}
          value={comment}
          maxLength={PLAN_LIMITS.commentMax}
          onChange={(event) => setComment(event.target.value)}
        />
      </Field>
      {!isLatestVersion && (
        <p className="text-muted-foreground text-xs">
          This is not the latest version, so you can only comment on it.
        </p>
      )}
      {error !== null && (
        <p className="text-error text-sm" role="alert">
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        {decisions.map((decision: PlanFeedbackDecision) => (
          <Button
            key={decision}
            type="button"
            size="sm"
            variant={decision === "COMMENT" ? "outline" : "default"}
            disabled={give.isPending}
            onClick={() => void submit(decision)}
          >
            {DECISION_BUTTONS[decision]}
          </Button>
        ))}
        {onCancel && (
          <Button type="button" size="sm" variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  );
}
