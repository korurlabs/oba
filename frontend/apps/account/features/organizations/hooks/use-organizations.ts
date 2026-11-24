"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useSession } from "next-auth/react";
import { useTranslations } from "next-intl";
import { ULAK_LINKS } from "@oba/shared/constants/apps";
import { invalidateAllOrganizationQueries } from "@oba/shared/hooks/use-invalidate-org-queries";
import type { OrganizationCreateRequest } from "@oba/shared/features/organizations/schemas";
import { useUpdateMe } from "@oba/shared/features/users/hooks/use-update-me";
import { createOrganization } from "../api";

/**
 * Creates an organization (`POST /organizations`) for the signed-in user.
 *
 * The creator becomes its OWNER, and the new organization becomes the user's
 * default organization (`PUT /users/me`). Ulak reads the default from the
 * shared session, not from the API, so the session is updated too before the
 * browser opens Ulak on the new organization. Account has no organization
 * screens of its own.
 *
 * The organization exists once the create succeeded, so a refused default
 * does not undo it: the switch error is shown and Ulak still opens, on the
 * organization that was the default before.
 */
export function useCreateOrganization() {
  const t = useTranslations("organization.new");
  const tShared = useTranslations();
  const queryClient = useQueryClient();
  const { mutateAsync: updateMe } = useUpdateMe();
  const { data: session, update } = useSession();
  return useMutation({
    mutationFn: (data: OrganizationCreateRequest) => createOrganization(data),
    onSuccess: async (response) => {
      await invalidateAllOrganizationQueries(queryClient);
      toast.success(t("createSuccess"));
      const organizationId = response.data?.id;
      try {
        if (!organizationId) {
          throw new Error("POST /organizations answered without the new organization's id");
        }
        await updateMe({ defaultOrganizationId: organizationId });
        await update({ user: { ...session?.user, defaultOrganizationId: organizationId } });
      } catch (error) {
        console.error("[useCreateOrganization] saving the new default organization failed:", error);
        toast.error(tShared("organizationSwitchFailed"));
      }
      window.location.assign(ULAK_LINKS.organizations);
    },
    onError: (error: Error) => {
      toast.error(error.message || t("createError"));
    },
  });
}
