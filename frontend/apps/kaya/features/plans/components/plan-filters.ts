import type { FilterCategory, FilterOption } from "@oba/ui/sky/filter-popover";

import { withChosen } from "@/components/common/list-parts";

import {
  ALL_REVIEW_STATES,
  planFilterParams,
  type PlanFilterParams,
} from "../hooks/use-plan-filter-params";
import {
  PlanKindSchema,
  PlanReviewStateSchema,
  type PlanFiltersResponse,
  type PlanKind,
  type PlanReferenceResponse,
  type PlanReviewState,
} from "../schemas";
import { KIND_LABELS, REVIEW_BADGES, REVIEW_FILTERS, referenceName } from "./plan-data";

/** The review state the list opens on: the plans that wait for review. */
export const DEFAULT_REVIEW_STATE = planFilterParams.reviewState.defaultValue;

/**
 * Every filter of the plan list back to its default, the search included.
 * The URL drops a param that holds its default, so "Open" leaves no trace.
 */
export const NO_PLAN_FILTERS: Partial<PlanFilterParams> = {
  search: null,
  reviewState: DEFAULT_REVIEW_STATE,
  kind: null,
  repositoryId: null,
  packId: null,
  userId: null,
};

function referenceOptions(
  references: readonly PlanReferenceResponse[] | undefined
): FilterOption[] {
  return (references ?? []).map((reference: PlanReferenceResponse) => ({
    value: reference.id,
    label: referenceName(reference),
  }));
}

function reviewState(value: string | null): PlanReviewState | typeof ALL_REVIEW_STATES {
  if (value === null) return DEFAULT_REVIEW_STATE;
  if (value === ALL_REVIEW_STATES) return ALL_REVIEW_STATES;
  return PlanReviewStateSchema.parse(value);
}

/**
 * The plan list's filter categories, one per value the API offers. Review
 * always holds a state: it starts on "Open" and offers "All".
 */
export function planFilterCategories(
  filters: PlanFilterParams,
  onChange: (change: Partial<PlanFilterParams>) => void,
  options: PlanFiltersResponse | undefined
): FilterCategory[] {
  return [
    {
      key: "reviewState",
      label: "Review",
      mode: "single",
      options: [
        ...REVIEW_FILTERS.map((state: PlanReviewState) => ({
          value: state,
          label: REVIEW_BADGES[state].label,
        })),
        { value: ALL_REVIEW_STATES, label: "All" },
      ],
      value: filters.reviewState,
      defaultValue: DEFAULT_REVIEW_STATE,
      onChange: (value: string | null) => onChange({ reviewState: reviewState(value) }),
    },
    {
      key: "kind",
      label: "Kind",
      mode: "single",
      options: PlanKindSchema.options.map((kind: PlanKind) => ({
        value: kind,
        label: KIND_LABELS[kind],
      })),
      value: filters.kind,
      onChange: (value: string | null) =>
        onChange({ kind: value === null ? null : PlanKindSchema.parse(value) }),
    },
    {
      key: "repositoryId",
      label: "Repository",
      mode: "single",
      searchable: true,
      options: withChosen(referenceOptions(options?.repositories), filters.repositoryId),
      value: filters.repositoryId,
      onChange: (repositoryId: string | null) => onChange({ repositoryId }),
    },
    {
      key: "packId",
      label: "Pack",
      mode: "single",
      searchable: true,
      options: withChosen(referenceOptions(options?.packs), filters.packId),
      value: filters.packId,
      onChange: (packId: string | null) => onChange({ packId }),
    },
    {
      key: "userId",
      label: "User",
      mode: "single",
      searchable: true,
      options: withChosen(referenceOptions(options?.users), filters.userId),
      value: filters.userId,
      onChange: (userId: string | null) => onChange({ userId }),
    },
  ];
}
