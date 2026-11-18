"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { getRepositoryPacks, listPacks } from "@/features/scope/api";
import type { RepositoryPackUpdateRequest } from "@/features/scope/schemas";
import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import { updateRepositoryPacks } from "../api";

/**
 * The packs of the organization and the pack list of one repository, the
 * two reads the repository packs dialog edits. They share the keys of the
 * scope tree, so its cached reads serve the dialog too.
 */
export function useRepositoryPackChoice(repositoryId: string, enabled: boolean) {
  const { organization } = useIdentity();
  const organizationId = organization.organizationId;
  const packs = useQuery({
    queryKey: VERSIONED_KEYS.packs(organizationId),
    queryFn: () => listPacks(),
    enabled,
  });
  const list = useQuery({
    queryKey: VERSIONED_KEYS.repositoryPacks(organizationId, repositoryId),
    queryFn: () => getRepositoryPacks(repositoryId),
    enabled: enabled && !!repositoryId,
  });
  return { packs, list };
}

type RepositoryPacksUpdate = { repositoryId: string; data: RepositoryPackUpdateRequest };

/**
 * Saves a repository's pack list; owners only. The dialog shows the refusal,
 * so the hook only logs it. A saved list moves the packs' mount folders and
 * publishes a release, so the pack tree and the repository's releases reload.
 */
export function useUpdateRepositoryPacks() {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  const organizationId = organization.organizationId;
  return useMutation({
    mutationFn: ({ repositoryId, data }: RepositoryPacksUpdate) =>
      updateRepositoryPacks(repositoryId, data),
    onSuccess: async (_response: unknown, { repositoryId }: RepositoryPacksUpdate) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: VERSIONED_KEYS.owners(organizationId) }),
        queryClient.invalidateQueries({
          queryKey: VERSIONED_KEYS.releases(organizationId, repositoryId),
        }),
      ]);
    },
    onError: (error: unknown) => {
      console.warn("[repositories] Pack list change refused", error);
    },
  });
}
