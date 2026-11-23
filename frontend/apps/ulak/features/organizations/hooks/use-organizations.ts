"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import type { ApiResponse } from "@oba/shared/schema";
import type { UserMe } from "@oba/shared/features/users/schema";
import { USERS_KEYS } from "@oba/shared/features/users/hooks/use-get-me";
import { getOrganization, updateOrganization } from "../api";
import type { OrganizationUpdateRequest } from "@oba/shared/features/organizations/schemas";
import { ORGANIZATION_KEYS } from "./query-keys";

/**
 * Query keys live in the boundary-safe `./query-keys` module (no "use client")
 * so the organization detail page can prefetch under the exact same key
 * without the factory becoming a client-reference proxy. Re-exported here for
 * client consumers.
 */
export { ORGANIZATION_KEYS };

export function useGetOrganization(
  organizationId: string | null | undefined,
) {
  return useQuery({
    queryKey: ORGANIZATION_KEYS.detail(organizationId ?? ""),
    queryFn: () => getOrganization(organizationId!),
    enabled: !!organizationId,
    staleTime: 30_000,
  });
}

export function useUpdateOrganization(organizationId: string) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: OrganizationUpdateRequest) =>
      updateOrganization(organizationId, data),
    onSuccess: async (response) => {
      toast.success(t("updateSuccess"));

      const updatedName = response.data?.name;

      queryClient.setQueryData(
        ORGANIZATION_KEYS.detail(organizationId),
        response,
      );

      // Update the org name in the current-user ("me") cache so the
      // OrganizationSelector shows the new name immediately.
      if (updatedName) {
        queryClient.setQueryData<ApiResponse<UserMe>>(
          USERS_KEYS.me,
          (prev) => {
            if (!prev?.data?.organizations) return prev;
            return {
              ...prev,
              data: {
                ...prev.data,
                organizations: prev.data.organizations.map((org) =>
                  org.id === organizationId
                    ? { ...org, name: updatedName }
                    : org,
                ),
              },
            };
          },
        );
      }

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ORGANIZATION_KEYS.all }),
        queryClient.invalidateQueries({ queryKey: USERS_KEYS.me }),
      ]);
    },
    onError: (error: Error) => {
      toast.error(error.message || t("updateError"));
    },
  });
}
