"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import {
  listOrganizationMembers,
  updateMemberRole,
  updateMemberStatus,
  removeMember,
} from "../api";
import type { OrganizationMemberStatus, UpdateMemberRoleRequest } from "@oba/shared/features/organizations/schemas";
import { APP_ACCESS_KEYS, GROUP_KEYS, MEMBER_KEYS } from "./query-keys";

/**
 * Query keys live in the boundary-safe `./query-keys` module (no "use client").
 * Re-exported here for client consumers.
 */
export { MEMBER_KEYS };

export function useOrganizationMembers(
  organizationId: string | null | undefined,
) {
  return useQuery({
    queryKey: MEMBER_KEYS.list(organizationId ?? ""),
    queryFn: () => listOrganizationMembers(organizationId!),
    enabled: !!organizationId,
  });
}

export function useUpdateMemberRole(organizationId: string) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      userId,
      data,
    }: {
      userId: string;
      data: UpdateMemberRoleRequest;
    }) => updateMemberRole(organizationId, userId, data),
    onSuccess: () => {
      toast.success(t("members.updateRoleSuccess"));
      // Returned so the mutation stays pending until the list has refetched:
      // the row selects are controlled by the list data.
      return invalidateMemberChange(queryClient, organizationId);
    },
    onError: (error: Error) => {
      toast.error(error.message || t("members.updateRoleError"));
    },
  });
}

export function useUpdateMemberStatus(organizationId: string) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      userId,
      status,
    }: {
      userId: string;
      status: OrganizationMemberStatus;
    }) => updateMemberStatus(organizationId, userId, status),
    onSuccess: () => {
      toast.success(t("members.updateStatusSuccess"));
      // Returned so the mutation stays pending until the list has refetched:
      // the row selects are controlled by the list data.
      return invalidateMemberChange(queryClient, organizationId);
    },
    onError: (error: Error) => {
      toast.error(error.message || t("members.updateStatusError"));
    },
  });
}

export function useRemoveMember(organizationId: string) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (userId: string) => removeMember(organizationId, userId),
    onSuccess: () => {
      toast.success(t("members.removeSuccess"));
      invalidateMemberChange(queryClient, organizationId);
    },
    onError: (error: Error) => {
      toast.error(error.message || t("members.removeError"));
    },
  });
}

/**
 * A member change also alters groups and app access: removal deletes the
 * member's group rows and direct grants, a role change alters the member's
 * effective apps, and only ACTIVE members appear in "Everyone" and in the
 * effective access list.
 */
function invalidateMemberChange(
  queryClient: ReturnType<typeof useQueryClient>,
  organizationId: string,
) {
  return Promise.all([
    queryClient.invalidateQueries({
      queryKey: MEMBER_KEYS.list(organizationId),
    }),
    queryClient.invalidateQueries({
      queryKey: GROUP_KEYS.organization(organizationId),
    }),
    queryClient.invalidateQueries({
      queryKey: APP_ACCESS_KEYS.organization(organizationId),
    }),
  ]);
}
