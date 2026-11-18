"use client";

import Link from "next/link";

import { Badge } from "@oba/ui/sky/badge";
import { describeError } from "@/lib/api/error-text";
import { dateTimeText } from "@/lib/format-time";

import { PLANS_PAGE_SIZE, usePlans } from "../hooks/use-plans";
import type { PlanListResponse } from "../schemas";
import { KIND_LABELS, REVIEW_BADGES } from "./plan-data";

/** The plans a session wrote a version of, newest change first, each linking to its plan page. */
export function SessionPlansPanel({ sessionId }: { sessionId: string }) {
  const plans = usePlans({ sessionId, page: 1, amount: PLANS_PAGE_SIZE });
  const rows = plans.data?.data;
  if (plans.error !== null && rows == null) {
    return (
      <p className="text-error text-sm" role="alert">
        The plans of this session could not be loaded: {describeError(plans.error)}
      </p>
    );
  }
  if (rows == null) {
    return <p className="text-muted-foreground text-sm">Loading the plans</p>;
  }
  if (rows.length === 0) {
    return (
      <p className="text-muted-foreground text-sm">
        This session wrote no spec and left no plan mode plan.
      </p>
    );
  }
  return (
    <ul className="flex flex-col divide-y rounded-md border">
      {rows.map((plan: PlanListResponse) => {
        const review = REVIEW_BADGES[plan.reviewState];
        return (
          <li key={plan.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2">
            <div className="flex min-w-0 flex-1 flex-col">
              <Link
                href={`/plans/${encodeURIComponent(plan.id)}`}
                className="font-medium break-words hover:underline"
              >
                {plan.title}
              </Link>
              {plan.path && (
                <span className="text-muted-foreground font-mono text-xs break-all">
                  {plan.path}
                </span>
              )}
            </div>
            <Badge variant="secondary">{KIND_LABELS[plan.kind]}</Badge>
            <Badge variant={review.variant}>{review.label}</Badge>
            <span className="text-muted-foreground text-xs tabular-nums">
              {plan.latestVersionNumber} {plan.latestVersionNumber === 1 ? "version" : "versions"}
              {plan.openFeedbackCount > 0 && `, ${plan.openFeedbackCount} open feedback`}
            </span>
            <span className="text-muted-foreground text-xs">
              {dateTimeText(plan.lastChangedAt)}
            </span>
          </li>
        );
      })}
    </ul>
  );
}
