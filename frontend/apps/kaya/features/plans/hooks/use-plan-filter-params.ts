"use client";

import { parseAsInteger, parseAsString, parseAsStringEnum, useQueryStates } from "nuqs";
import type { Values } from "nuqs";

import { PlanKindSchema, PlanReviewStateSchema } from "../schemas";

/** The review filter's value for every state; without it the list shows open plans. */
export const ALL_REVIEW_STATES = "ALL";

/**
 * The plan list's filters, kept in the URL so a filtered list can be reloaded
 * and shared. The list opens on the plans that wait for review.
 */
export const planFilterParams = {
  search: parseAsString,
  repositoryId: parseAsString,
  packId: parseAsString,
  kind: parseAsStringEnum(PlanKindSchema.options),
  reviewState: parseAsStringEnum([...PlanReviewStateSchema.options, ALL_REVIEW_STATES]).withDefault(
    "OPEN"
  ),
  userId: parseAsString,
  page: parseAsInteger.withDefault(1),
};

export type PlanFilterParams = Values<typeof planFilterParams>;

export function usePlanFilterParams() {
  return useQueryStates(planFilterParams);
}
