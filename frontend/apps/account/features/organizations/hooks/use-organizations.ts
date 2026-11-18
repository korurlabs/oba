"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { ADMIN_LINKS } from "@oba/shared/constants/apps";
import { invalidateAllOrganizationQueries } from "@oba/shared/hooks/use-invalidate-org-queries";
import type { OrganizationCreateRequest } from "@oba/shared/features/organizations/schemas";
import { createOrganization } from "../api";

/**
 * Creates an organization (`POST /organizations`) for the signed-in user.
 *
 * The creator becomes its OWNER, so on success the browser opens the new
 * organization in the Admin app; Account has no organization screens.
 * The active organization does not change: the backend sets the default
 * organization only on onboarding and on accepting an invitation.
 */
export function useCreateOrganization() {
  const t = useTranslations("organization.new");
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: OrganizationCreateRequest) => createOrganization(data),
    onSuccess: async (response) => {
      await invalidateAllOrganizationQueries(queryClient);
      toast.success(t("createSuccess"));
      const organizationId = response.data?.id;
      // Without an id in the answer, the Admin list still shows the new
      // organization to its owner.
      window.location.assign(
        organizationId ? ADMIN_LINKS.organization(organizationId) : ADMIN_LINKS.organizations,
      );
    },
    onError: (error: Error) => {
      toast.error(error.message || t("createError"));
    },
  });
}
