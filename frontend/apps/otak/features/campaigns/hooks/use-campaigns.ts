"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useOrganizationId } from "@/hooks/use-organization-id";
import { OUTREACH_KEYS } from "@/features/outreaches/hooks/query-keys";
import { RELATION_KEYS } from "@/features/relations/hooks/query-keys";
import { createCampaign, deleteCampaign, getCampaign, listCampaigns, updateCampaign } from "../api";
import { campaignErrorText } from "../lib/campaign-error";
import type { CampaignQueryRequest, CampaignRequest } from "../schemas";
import { CAMPAIGN_KEYS } from "./query-keys";

export { CAMPAIGN_KEYS };

/** Every campaign that matches the query, unpaged; waits for the organization. */
export function useCampaigns(params: CampaignQueryRequest) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: CAMPAIGN_KEYS.list(organizationId ?? "", params),
    queryFn: () => listCampaigns(params),
    enabled: !!organizationId,
    // A new tab, channel or search keeps the current rows until its answer
    // arrives, but never the rows of an organization the user just left.
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === organizationId ? previous : undefined,
  });
}

/** One campaign; waits for the organization and an id. */
export function useCampaign(campaignId: string, { enabled = true }: { enabled?: boolean } = {}) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: CAMPAIGN_KEYS.detail(organizationId ?? "", campaignId),
    queryFn: () => getCampaign(campaignId),
    enabled: enabled && !!organizationId && campaignId !== "",
  });
}

/** No filter: every campaign, the same query as the list's "All" tab. */
const CAMPAIGN_OPTIONS_PARAMS: CampaignQueryRequest = {};

/**
 * The campaigns an outreach or a relation can be linked to: every campaign
 * of the organization, newest change first, as select options. The list is
 * unpaged, so a stored link is always among them while the campaign exists.
 */
export function useCampaignOptions() {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: CAMPAIGN_KEYS.list(organizationId ?? "", CAMPAIGN_OPTIONS_PARAMS),
    queryFn: () => listCampaigns(CAMPAIGN_OPTIONS_PARAMS),
    enabled: !!organizationId,
    select: (response) =>
      (response.data ?? []).map((campaign) => ({ value: campaign.id, label: campaign.name })),
  });
}

/**
 * A campaign change refreshes every campaign query of the organization: the
 * lists, the details and the selects of the outreach and relation forms.
 */
function useInvalidateCampaigns() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  return () => {
    if (organizationId) {
      void queryClient.invalidateQueries({ queryKey: CAMPAIGN_KEYS.all(organizationId) });
    }
  };
}

export function useCreateCampaign() {
  const invalidate = useInvalidateCampaigns();
  const t = useTranslations("campaigns");

  return useMutation({
    mutationFn: (data: CampaignRequest) => createCampaign(data),
    onSuccess: () => {
      toast.success(t("toast.created"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(campaignErrorText(error, t, "toast.saveError"));
    },
  });
}

/** Saves a campaign; `successKey` names the toast, such as the one for marking it published. */
export function useUpdateCampaign({ successKey = "toast.updated" }: { successKey?: string } = {}) {
  const invalidate = useInvalidateCampaigns();
  const t = useTranslations("campaigns");

  return useMutation({
    mutationFn: ({ campaignId, data }: { campaignId: string; data: CampaignRequest }) =>
      updateCampaign(campaignId, data),
    onSuccess: () => {
      toast.success(t(successKey));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(campaignErrorText(error, t, "toast.saveError"));
    },
  });
}

/**
 * Deleting a campaign unlinks its outreaches and relations on the server, so
 * their queries are refreshed too.
 */
export function useDeleteCampaign() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  const t = useTranslations("campaigns");

  return useMutation({
    mutationFn: (campaignId: string) => deleteCampaign(campaignId),
    onSuccess: (_result, campaignId) => {
      toast.success(t("toast.deleted"));
      if (organizationId) {
        queryClient.removeQueries({ queryKey: CAMPAIGN_KEYS.detail(organizationId, campaignId) });
        void queryClient.invalidateQueries({ queryKey: CAMPAIGN_KEYS.all(organizationId) });
        void queryClient.invalidateQueries({ queryKey: RELATION_KEYS.all(organizationId) });
        void queryClient.invalidateQueries({ queryKey: OUTREACH_KEYS.all(organizationId) });
      }
    },
    onError: (error: unknown) => {
      toast.error(campaignErrorText(error, t, "toast.deleteError"));
    },
  });
}
