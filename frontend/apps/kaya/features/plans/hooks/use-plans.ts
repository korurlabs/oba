"use client";

import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";

import { useIdentity } from "@/lib/identity/identity-context";

import {
  getPlan,
  getPlanDiff,
  getPlanFilters,
  getPlanVersion,
  giveFeedback,
  listPlans,
  withdrawFeedback,
} from "../api";
import type { PlanFeedbackRequest, PlanQueryRequest } from "../schemas";

export const PLANS_PAGE_SIZE = 25;

/** How often an open plan page reloads, so a delivery or a new version shows without a reload. */
export const PLAN_REFRESH_MS = 10_000;

/** Every key starts with the organization, so answers of one never show under another. */
export const PLAN_KEYS = {
  all: (organizationId: string) => ["plans", organizationId] as const,
  lists: (organizationId: string) => [...PLAN_KEYS.all(organizationId), "list"] as const,
  list: (organizationId: string, params: PlanQueryRequest) =>
    [...PLAN_KEYS.lists(organizationId), params] as const,
  filters: (organizationId: string) => [...PLAN_KEYS.all(organizationId), "filters"] as const,
  detail: (organizationId: string, planId: string) =>
    [...PLAN_KEYS.all(organizationId), "detail", planId] as const,
  version: (organizationId: string, planId: string, number: number) =>
    [...PLAN_KEYS.all(organizationId), "version", planId, number] as const,
  diff: (organizationId: string, planId: string, number: number) =>
    [...PLAN_KEYS.all(organizationId), "diff", planId, number] as const,
};

/** One page of the organization's plans; `enabled` holds the read until its filter is known. */
export function usePlans(params: PlanQueryRequest, options: { enabled?: boolean } = {}) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: PLAN_KEYS.list(organization.organizationId, params),
    queryFn: () => listPlans(params),
    enabled: options.enabled ?? true,
    // A new filter or page keeps the current rows until its answer arrives,
    // but never the rows of the organization the user just left.
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === organization.organizationId ? previous : undefined,
  });
}

export function usePlanFilters() {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: PLAN_KEYS.filters(organization.organizationId),
    queryFn: getPlanFilters,
  });
}

/** One plan, reloaded every 10 s while its page is open. */
export function usePlan(planId: string) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: PLAN_KEYS.detail(organization.organizationId, planId),
    queryFn: () => getPlan(planId),
    enabled: planId !== "",
    refetchInterval: PLAN_REFRESH_MS,
  });
}

/** A version's content never changes once captured, so it stays fresh for the session. */
export function usePlanVersion(planId: string, number: number | undefined) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: PLAN_KEYS.version(organization.organizationId, planId, number ?? 0),
    queryFn: () => getPlanVersion(planId, number ?? 0),
    enabled: planId !== "" && number !== undefined && number >= 1,
    staleTime: Infinity,
  });
}

/** The changes of a version against the one before; as fixed as the versions themselves. */
export function usePlanDiff(planId: string, number: number | undefined) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: PLAN_KEYS.diff(organization.organizationId, planId, number ?? 0),
    queryFn: () => getPlanDiff(planId, number ?? 0),
    enabled: planId !== "" && number !== undefined && number >= 1,
    staleTime: Infinity,
  });
}

/** Feedback changes the plan's state and open count, which the plan and every list show. */
export async function invalidatePlan(
  queryClient: QueryClient,
  organizationId: string,
  planId: string
): Promise<void> {
  await Promise.all(
    [PLAN_KEYS.detail(organizationId, planId), PLAN_KEYS.lists(organizationId)].map((queryKey) =>
      queryClient.invalidateQueries({ queryKey })
    )
  );
}

/**
 * A feedback mutation; the form or row that ran it shows the refusal, such
 * as 409 KAY_PLN_001 for a decision on an old version, so the hook only logs
 * it. A refusal still reloads the plan, since it often means the plan moved on.
 */
function usePlanMutation<TArgs extends { planId: string }, TResult>(
  mutationFn: (args: TArgs) => Promise<TResult>,
  refusal: string
) {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  return useMutation({
    mutationFn,
    onSuccess: (_result: TResult, args: TArgs) =>
      invalidatePlan(queryClient, organization.organizationId, args.planId),
    onError: (error: unknown, args: TArgs) => {
      console.warn(`[plans] ${refusal}`, error);
      return invalidatePlan(queryClient, organization.organizationId, args.planId);
    },
  });
}

export function useGiveFeedback() {
  return usePlanMutation(
    ({ planId, data }: { planId: string; data: PlanFeedbackRequest }) => giveFeedback(planId, data),
    "Feedback refused"
  );
}

export function useWithdrawFeedback() {
  return usePlanMutation(
    ({ planId, feedbackId }: { planId: string; feedbackId: string }) =>
      withdrawFeedback(planId, feedbackId),
    "Withdraw refused"
  );
}
