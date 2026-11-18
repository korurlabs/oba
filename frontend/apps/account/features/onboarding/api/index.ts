import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  OnboardingCompleteRequest,
  OnboardingStatusResponse,
} from "../schemas";

export async function completeOnboarding(data: OnboardingCompleteRequest) {
  const res = await api.post<ApiResponse<OnboardingStatusResponse>>(
    API_ROUTES.onboarding.complete,
    data,
  );
  return res.data;
}
