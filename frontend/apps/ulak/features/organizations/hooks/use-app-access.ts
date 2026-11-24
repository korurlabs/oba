"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { USERS_KEYS } from "@oba/shared/features/users/hooks/use-get-me";
import { listAppGrants, createAppGrant, revokeAppGrant, listMemberAppAccess } from "../api";
import type { AppGrantCreateRequest } from "@oba/shared/features/organizations/schemas";
import { APP_ACCESS_KEYS, GROUP_KEYS } from "./query-keys";

/**
 * Query keys live in the boundary-safe `./query-keys` module (no "use client").
 * Re-exported here for client consumers.
 */
export { APP_ACCESS_KEYS };

export function useAppGrants(organizationId: string | null | undefined) {
  return useQuery({
    queryKey: APP_ACCESS_KEYS.grants(organizationId ?? ""),
    queryFn: () => listAppGrants(organizationId!),
    enabled: !!organizationId,
  });
}

export function useMemberAppAccess(organizationId: string | null | undefined) {
  return useQuery({
    queryKey: APP_ACCESS_KEYS.members(organizationId ?? ""),
    queryFn: () => listMemberAppAccess(organizationId!),
    enabled: !!organizationId,
  });
}

export function useCreateAppGrant(organizationId: string) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: AppGrantCreateRequest) => createAppGrant(organizationId, data),
    onSuccess: () => {
      toast.success(t("access.grantSuccess"));
      return invalidateGrantChange(queryClient, organizationId);
    },
    onError: (error: Error) => {
      toast.error(error.message || t("access.grantError"));
    },
  });
}

export function useRevokeAppGrant(organizationId: string) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (grantId: string) => revokeAppGrant(organizationId, grantId),
    onSuccess: () => {
      toast.success(t("access.revokeSuccess"));
      return invalidateGrantChange(queryClient, organizationId);
    },
    onError: (error: Error) => {
      toast.error(error.message || t("access.revokeError"));
    },
  });
}

/**
 * A grant change alters the grant list, the effective apps of members, the
 * apps of a group, and the apps of the current user, because an admin may
 * grant an app to themselves. Returned so the mutation stays pending until
 * the lists have refetched.
 */
function invalidateGrantChange(
  queryClient: ReturnType<typeof useQueryClient>,
  organizationId: string
) {
  return Promise.all([
    queryClient.invalidateQueries({
      queryKey: APP_ACCESS_KEYS.organization(organizationId),
    }),
    queryClient.invalidateQueries({
      queryKey: GROUP_KEYS.list(organizationId),
    }),
    queryClient.invalidateQueries({ queryKey: USERS_KEYS.me }),
  ]);
}
