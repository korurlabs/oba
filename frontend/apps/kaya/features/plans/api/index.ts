import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type {
  PlanDetailResponse,
  PlanFeedbackRequest,
  PlanFeedbackResponse,
  PlanFiltersResponse,
  PlanListResponse,
  PlanQueryRequest,
  PlanVersionContentResponse,
  PlanVersionDiffResponse,
} from "../schemas";

/** One page of the organization's plans, newest change first. */
export async function listPlans(params: PlanQueryRequest) {
  const res = await api.get<ApiResponse<PlanListResponse[]>>(API_ROUTES.plans.list, { params });
  return res.data;
}

/** The repositories, packs and users the organization's plans hold. */
export async function getPlanFilters() {
  const res = await api.get<ApiResponse<PlanFiltersResponse>>(API_ROUTES.plans.filters);
  return res.data;
}

/** A plan with its versions, feedback, feature handoffs and whether the caller may approve. */
export async function getPlan(planId: string) {
  const res = await api.get<ApiResponse<PlanDetailResponse>>(API_ROUTES.plans.detail(planId));
  return res.data;
}

/** The content of one version. */
export async function getPlanVersion(planId: string, number: number) {
  const res = await api.get<ApiResponse<PlanVersionContentResponse>>(
    API_ROUTES.plans.version(planId, number)
  );
  return res.data;
}

/** The unified diff of one version against the one before; empty for version 1. */
export async function getPlanDiff(planId: string, number: number) {
  const res = await api.get<ApiResponse<PlanVersionDiffResponse>>(
    API_ROUTES.plans.diff(planId, number)
  );
  return res.data;
}

/** Approves, requests changes or comments; APPROVE and REQUEST_CHANGES only on the latest version. */
export async function giveFeedback(planId: string, data: PlanFeedbackRequest) {
  const res = await api.post<ApiResponse<PlanFeedbackResponse>>(
    API_ROUTES.plans.feedback(planId),
    data
  );
  return res.data;
}

/** Withdraws the caller's own feedback while no session has received it. */
export async function withdrawFeedback(planId: string, feedbackId: string): Promise<void> {
  await api.delete(API_ROUTES.plans.feedbackItem(planId, feedbackId));
}
