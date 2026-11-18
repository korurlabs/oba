import { scopeValue } from "@/features/scope/components/scope-data";

import type { PlanFilterParams } from "../hooks/use-plan-filter-params";
import { ALL_REVIEW_STATES } from "../hooks/use-plan-filter-params";
import { PLANS_PAGE_SIZE } from "../hooks/use-plans";
import type {
  PlanKind,
  PlanQueryRequest,
  PlanReferenceResponse,
  PlanReviewState,
} from "../schemas";

type BadgeVariant = "muted" | "warning" | "success" | "error" | "info" | "secondary";

export const KIND_LABELS: Readonly<Record<PlanKind, string>> = {
  SPEC: "Spec",
  SESSION: "Session plan",
};

export const REVIEW_BADGES: Readonly<
  Record<PlanReviewState, { label: string; variant: BadgeVariant }>
> = {
  OPEN: { label: "Open", variant: "info" },
  APPROVED: { label: "Approved", variant: "success" },
  CHANGES_REQUESTED: { label: "Changes requested", variant: "warning" },
};

/** The review states in the order the filter offers them. */
export const REVIEW_FILTERS: readonly PlanReviewState[] = ["OPEN", "CHANGES_REQUESTED", "APPROVED"];

export const EMPTY_PLANS_TEXT =
  "No plans recorded yet. Plans appear when a session writes a spec or leaves plan mode.";

/** The URL filters as the list request; an unknown page falls back to the first. */
export function toPlanQuery(filters: PlanFilterParams): PlanQueryRequest {
  const query: PlanQueryRequest = {
    page: Math.max(1, filters.page),
    amount: PLANS_PAGE_SIZE,
  };
  if (filters.reviewState !== ALL_REVIEW_STATES) query.reviewState = filters.reviewState;
  if (filters.kind !== null) query.kind = filters.kind;
  if (filters.repositoryId !== null && filters.repositoryId !== "")
    query.repositoryId = filters.repositoryId;
  if (filters.packId !== null && filters.packId !== "") query.packId = filters.packId;
  if (filters.userId !== null && filters.userId !== "") query.userId = filters.userId;
  const search = filters.search?.trim() ?? "";
  if (search !== "") query.search = search;
  return query;
}

/** Whether a filter other than the review state narrows the list. */
function hasNarrowingFilter(filters: PlanFilterParams): boolean {
  return (
    filters.kind !== null ||
    filters.repositoryId !== null ||
    filters.packId !== null ||
    filters.userId !== null ||
    filters.search !== null
  );
}

/** Whether any filter differs from the default list of open plans. */
export function hasActiveFilter(filters: PlanFilterParams): boolean {
  return filters.reviewState !== "OPEN" || hasNarrowingFilter(filters);
}

/**
 * The text of an empty list. Every plan has a repository, so filter options
 * without one mean the organization has no plan at all; until they load, the
 * text speaks of the filters only.
 */
export function emptyPlansText(
  filters: PlanFilterParams,
  organizationHasPlans: boolean | undefined
): string {
  if (organizationHasPlans === false) return EMPTY_PLANS_TEXT;
  if (filters.reviewState === "OPEN" && !hasNarrowingFilter(filters)) {
    return "No plan waits for review.";
  }
  return "No plan matches these filters.";
}

/** A reference's name, or a short id when the name is no longer known. */
export function referenceName(reference: PlanReferenceResponse): string {
  return reference.name ?? reference.id.slice(0, 8);
}

/** The pack page a plan's pack opens: its rules in the pack scope. */
export function packHref(pack: PlanReferenceResponse): string {
  return `/rules?scope=${encodeURIComponent(scopeValue({ kind: "PACK", packId: pack.id }))}`;
}
