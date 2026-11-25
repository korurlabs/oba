import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  CampaignDetail,
  CampaignListApiResponse,
  CampaignQueryRequest,
  CampaignRequest,
} from "../schemas";

/**
 * Every campaign of the organization that matches, unpaged: the scheduled
 * ones soonest planned first, any other list newest change first.
 */
export async function listCampaigns(params: CampaignQueryRequest) {
  const res = await api.get<CampaignListApiResponse>(API_ROUTES.campaigns.list, { params });
  return res.data;
}

export async function getCampaign(campaignId: string) {
  const res = await api.get<ApiResponse<CampaignDetail>>(API_ROUTES.campaigns.detail(campaignId));
  return res.data;
}

/**
 * A second campaign with the same name, case ignored, is refused with
 * `OTK_STA_015`; moments that break the status rules with `OTK_VAL_016`.
 */
export async function createCampaign(data: CampaignRequest) {
  const res = await api.post<ApiResponse<CampaignDetail>>(API_ROUTES.campaigns.list, data);
  return res.data;
}

/** A full replace: a null optional field is cleared and the texts replace the stored ones. */
export async function updateCampaign(campaignId: string, data: CampaignRequest) {
  const res = await api.put<ApiResponse<CampaignDetail>>(
    API_ROUTES.campaigns.detail(campaignId),
    data
  );
  return res.data;
}

/**
 * Removes the campaign with its texts and images; its outreaches and
 * relations stay and lose the link. ADMIN or OWNER only.
 */
export async function deleteCampaign(campaignId: string): Promise<void> {
  await api.delete(API_ROUTES.campaigns.detail(campaignId));
}
