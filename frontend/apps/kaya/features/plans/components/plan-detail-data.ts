import { describeError } from "@/lib/api/error-text";
import { KayaApiError } from "@/lib/api/errors";
import { dateTimeText } from "@/lib/format-time";

import type {
  PlanFeedbackDecision,
  PlanFeedbackResponse,
  PlanHandoffResponse,
  PlanVersionSource,
  PlanVersionSummaryResponse,
} from "../schemas";

export const DECISION_LABELS: Readonly<Record<PlanFeedbackDecision, string>> = {
  APPROVE: "Approved",
  REQUEST_CHANGES: "Requested changes",
  COMMENT: "Commented",
};

export const DECISION_BUTTONS: Readonly<Record<PlanFeedbackDecision, string>> = {
  APPROVE: "Approve",
  REQUEST_CHANGES: "Request changes",
  COMMENT: "Comment",
};

export const SOURCE_LABELS: Readonly<Record<PlanVersionSource, string>> = {
  FILE: "File",
  PLAN_MODE: "Plan mode",
  MESSAGE: "Message",
};

/**
 * The decisions the form offers. An older version takes only a comment, since
 * the server refuses a decision there; Approve is left out for a member who
 * is neither the plan's user nor an admin or owner.
 */
export function feedbackDecisions(
  canApprove: boolean,
  isLatestVersion: boolean
): PlanFeedbackDecision[] {
  if (!isLatestVersion) return ["COMMENT"];
  return canApprove ? ["REQUEST_CHANGES", "COMMENT", "APPROVE"] : ["REQUEST_CHANGES", "COMMENT"];
}

/** A version as the select lists it: number, source, session and time. */
export function versionLabel(version: PlanVersionSummaryResponse): string {
  return [
    `Version ${version.number}`,
    SOURCE_LABELS[version.source],
    version.sessionId ? `session ${version.sessionId.slice(0, 8)}` : "session deleted",
    dateTimeText(version.capturedAt),
  ].join(" · ");
}

/** Only the reviewer withdraws feedback, and only while no session has received it. */
export function mayWithdraw(feedback: PlanFeedbackResponse, userId: string): boolean {
  return feedback.reviewer.id === userId && feedback.deliveredAt == null;
}

/** The status of a unit: that of the first feature handoff that implements it, if any. */
export function unitStatus(
  handoffs: readonly PlanHandoffResponse[],
  unitNumber: number | null
): { handoff: PlanHandoffResponse | null; text: string } {
  const handoff =
    unitNumber === null
      ? undefined
      : handoffs.find((candidate: PlanHandoffResponse) => candidate.unitNumber === unitNumber);
  if (handoff === undefined) return { handoff: null, text: "No handoff yet" };
  return { handoff, text: handoff.status ?? "No status line" };
}

export type FeedbackAction = "feedback" | "withdraw";

/**
 * Plain words for a refused feedback or withdrawal. The plan codes are this
 * feature's own; a role refusal is told by what was tried; anything else
 * reads as every other form in Kaya reads it.
 */
export function planRefusalText(error: unknown, action: FeedbackAction): string {
  if (error instanceof KayaApiError) {
    switch (error.code) {
      case "KAY_PLN_001":
        return "A newer version of this plan exists. On an older version you can only comment; open the latest version to approve or request changes.";
      case "KAY_PLN_002":
        return "Kaya refused the comment or the line range: a decision other than Approve needs a comment, and the lines must lie within this version.";
      case "KAY_PLN_003":
        return "This feedback has already reached a session, so it can no longer be withdrawn.";
      case "GEN_SEC_003":
        return action === "withdraw"
          ? "You can withdraw only your own feedback."
          : "Only the user who wrote the plan, an admin or an owner can approve it.";
    }
  }
  return describeError(error);
}
