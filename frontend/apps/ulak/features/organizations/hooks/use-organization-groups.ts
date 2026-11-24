"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { isApiError } from "@oba/shared/api/axios";
import { USERS_KEYS } from "@oba/shared/features/users/hooks/use-get-me";
import {
  listOrganizationGroups,
  createOrganizationGroup,
  updateOrganizationGroup,
  deleteOrganizationGroup,
  listOrganizationGroupMembers,
  addOrganizationGroupMembers,
  removeOrganizationGroupMember,
} from "../api";
import type {
  OrganizationGroupMemberAddRequest,
  OrganizationGroupRequest,
} from "@oba/shared/features/organizations/schemas";
import { APP_ACCESS_KEYS, GROUP_KEYS } from "./query-keys";

/**
 * Query keys live in the boundary-safe `./query-keys` module (no "use client").
 * Re-exported here for client consumers.
 */
export { GROUP_KEYS };

export interface GroupFormMutationOptions {
  /**
   * Error codes that the caller shows on its form as a field error. The hook
   * shows no toast for them, so the user does not read the same error twice.
   */
  formErrorCodes?: readonly string[];
}

function isFormError(error: Error, options: GroupFormMutationOptions | undefined): boolean {
  return options?.formErrorCodes?.some((code) => isApiError(error, code)) ?? false;
}

export function useOrganizationGroups(organizationId: string | null | undefined) {
  return useQuery({
    queryKey: GROUP_KEYS.list(organizationId ?? ""),
    queryFn: () => listOrganizationGroups(organizationId!),
    enabled: !!organizationId,
  });
}

export function useOrganizationGroupMembers(
  organizationId: string | null | undefined,
  groupId: string | null | undefined
) {
  return useQuery({
    queryKey: GROUP_KEYS.members(organizationId ?? "", groupId ?? ""),
    queryFn: () => listOrganizationGroupMembers(organizationId!, groupId!),
    enabled: !!organizationId && !!groupId,
  });
}

export function useCreateOrganizationGroup(
  organizationId: string,
  options?: GroupFormMutationOptions
) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: OrganizationGroupRequest) => createOrganizationGroup(organizationId, data),
    onSuccess: () => {
      toast.success(t("groups.createSuccess"));
      // Returned so the mutation stays pending until the lists have refetched.
      return Promise.all([
        queryClient.invalidateQueries({
          queryKey: GROUP_KEYS.list(organizationId),
        }),
        queryClient.invalidateQueries({
          queryKey: APP_ACCESS_KEYS.grants(organizationId),
        }),
      ]);
    },
    onError: (error: Error) => {
      if (isFormError(error, options)) return;
      toast.error(error.message || t("groups.createError"));
    },
  });
}

export function useUpdateOrganizationGroup(
  organizationId: string,
  options?: GroupFormMutationOptions
) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ groupId, data }: { groupId: string; data: OrganizationGroupRequest }) =>
      updateOrganizationGroup(organizationId, groupId, data),
    onSuccess: () => {
      toast.success(t("groups.updateSuccess"));
      // A grant row shows the group name, so the grant list refetches too.
      return Promise.all([
        queryClient.invalidateQueries({
          queryKey: GROUP_KEYS.list(organizationId),
        }),
        queryClient.invalidateQueries({
          queryKey: APP_ACCESS_KEYS.grants(organizationId),
        }),
      ]);
    },
    onError: (error: Error) => {
      if (isFormError(error, options)) return;
      toast.error(error.message || t("groups.updateError"));
    },
  });
}

export interface DeleteGroupMutationOptions {
  /**
   * Runs after the deleted group's member list leaves the cache and before the
   * lists refetch. A view that still holds the group stops its member query
   * here; otherwise the refetch re-renders it and the query asks the API for
   * the members of a group that no longer exists.
   */
  onBeforeInvalidate?: (groupId: string) => void;
}

export function useDeleteOrganizationGroup(
  organizationId: string,
  options?: DeleteGroupMutationOptions
) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (groupId: string) => deleteOrganizationGroup(organizationId, groupId),
    onSuccess: (_, groupId) => {
      toast.success(t("groups.deleteSuccess"));
      // Removed, not invalidated: an invalidation refetches the list of a
      // group that no longer exists.
      queryClient.removeQueries({
        queryKey: GROUP_KEYS.members(organizationId, groupId),
      });
      options?.onBeforeInvalidate?.(groupId);
      // The delete also removes the group's grants, so the members of the
      // group can lose apps, the current user among them.
      return Promise.all([
        queryClient.invalidateQueries({
          queryKey: GROUP_KEYS.list(organizationId),
        }),
        queryClient.invalidateQueries({
          queryKey: APP_ACCESS_KEYS.organization(organizationId),
        }),
        queryClient.invalidateQueries({ queryKey: USERS_KEYS.me }),
      ]);
    },
    onError: (error: Error) => {
      toast.error(error.message || t("groups.deleteError"));
    },
  });
}

export function useAddOrganizationGroupMembers(organizationId: string) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ groupId, data }: { groupId: string; data: OrganizationGroupMemberAddRequest }) =>
      addOrganizationGroupMembers(organizationId, groupId, data),
    onSuccess: (_, { groupId }) => {
      toast.success(t("groups.addMembersSuccess"));
      return invalidateGroupMembership(queryClient, organizationId, groupId);
    },
    onError: (error: Error) => {
      toast.error(error.message || t("groups.addMembersError"));
    },
  });
}

export function useRemoveOrganizationGroupMember(organizationId: string) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ groupId, userId }: { groupId: string; userId: string }) =>
      removeOrganizationGroupMember(organizationId, groupId, userId),
    onSuccess: (_, { groupId }) => {
      toast.success(t("groups.removeMemberSuccess"));
      return invalidateGroupMembership(queryClient, organizationId, groupId);
    },
    onError: (error: Error) => {
      toast.error(error.message || t("groups.removeMemberError"));
    },
  });
}

/**
 * A membership change alters the group's member list and member count, and
 * the effective apps of the member, who can be the current user.
 */
function invalidateGroupMembership(
  queryClient: ReturnType<typeof useQueryClient>,
  organizationId: string,
  groupId: string
) {
  return Promise.all([
    queryClient.invalidateQueries({
      queryKey: GROUP_KEYS.members(organizationId, groupId),
    }),
    queryClient.invalidateQueries({
      queryKey: GROUP_KEYS.list(organizationId),
    }),
    queryClient.invalidateQueries({
      queryKey: APP_ACCESS_KEYS.members(organizationId),
    }),
    queryClient.invalidateQueries({ queryKey: USERS_KEYS.me }),
  ]);
}
