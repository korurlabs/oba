import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  CampaignContent,
  CampaignContentListApiResponse,
  CampaignContentRequest,
  CampaignDetail,
  CampaignListApiResponse,
  CampaignQueryRequest,
  CampaignRequest,
} from "../schemas";

// ── Campaigns ───────────────────────────────────────────────────────

/** Every campaign of the organization that matches, unpaged, newest change first. */
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
 * `OTK_STA_015`; an end day before the start day with `OTK_VAL_017`.
 */
export async function createCampaign(data: CampaignRequest) {
  const res = await api.post<ApiResponse<CampaignDetail>>(API_ROUTES.campaigns.list, data);
  return res.data;
}

/** A full replace: a null optional field is cleared. */
export async function updateCampaign(campaignId: string, data: CampaignRequest) {
  const res = await api.put<ApiResponse<CampaignDetail>>(
    API_ROUTES.campaigns.detail(campaignId),
    data
  );
  return res.data;
}

/**
 * Removes the campaign with its contents and their images; its outreaches
 * and relations stay and lose the link. ADMIN or OWNER only.
 */
export async function deleteCampaign(campaignId: string): Promise<void> {
  await api.delete(API_ROUTES.campaigns.detail(campaignId));
}

// ── Contents ────────────────────────────────────────────────────────

/** Every content of the campaign, unpaged: soonest planned first, unplanned last. */
export async function listCampaignContents(campaignId: string) {
  const res = await api.get<CampaignContentListApiResponse>(
    API_ROUTES.campaigns.contents(campaignId)
  );
  return res.data;
}

/**
 * A legal entity of another organization is refused with `LGE_NOT_001`;
 * moments that break the status rules with `OTK_VAL_016`.
 */
export async function createCampaignContent(campaignId: string, data: CampaignContentRequest) {
  const res = await api.post<ApiResponse<CampaignContent>>(
    API_ROUTES.campaigns.contents(campaignId),
    data
  );
  return res.data;
}

/** A full replace: a null optional field is cleared. */
export async function updateCampaignContent(
  campaignId: string,
  contentId: string,
  data: CampaignContentRequest
) {
  const res = await api.put<ApiResponse<CampaignContent>>(
    API_ROUTES.campaigns.content(campaignId, contentId),
    data
  );
  return res.data;
}

/** Removes the content with its images. */
export async function deleteCampaignContent(campaignId: string, contentId: string): Promise<void> {
  await api.delete(API_ROUTES.campaigns.content(campaignId, contentId));
}
