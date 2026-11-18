"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { updateRepositoryPacks } from "@/features/repositories/api";
import { checkRows, type PackRow } from "@/features/repositories/components/repository-pack-rows";
import { getPack, getRepositoryPacks, updatePack } from "@/features/scope/api";
import type { PackUpdateRequest, RepositoryPackEntryResponse } from "@/features/scope/schemas";
import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import { listPackVersions } from "../api";
import { PACK_VERSIONS_PAGE_SIZE } from "../schemas";

/**
 * A pack's detail sits under the owners key, so every change of a pack or of
 * a repository's pack list reloads it with the tree. Its versions sit under
 * the versioned root, so an approval reloads them.
 */
const KEYS = {
  detail: (organizationId: string, packId: string) =>
    [...VERSIONED_KEYS.owners(organizationId), "pack", packId] as const,
  versions: (organizationId: string, packId: string, page: number) =>
    [...VERSIONED_KEYS.all(organizationId), "pack-versions", packId, page] as const,
};

/** One pack with the repositories that compose it. */
export function usePackDetail(packId: string) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.detail(organization.organizationId, packId),
    queryFn: () => getPack(packId),
    enabled: !!packId,
  });
}

/** One page of a pack's versions, newest first; `page` counts from 1. */
export function usePackVersions(packId: string, page: number) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.versions(organization.organizationId, packId, page),
    queryFn: () => listPackVersions({ packId, page, amount: PACK_VERSIONS_PAGE_SIZE }),
    enabled: !!packId,
  });
}

/**
 * Sets a pack's name and description; owners only. The page shows the
 * refusal, so the hook only logs it. The name labels the tree, changes and
 * releases, so every pack read reloads.
 */
export function useUpdatePack() {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  return useMutation({
    mutationFn: ({ packId, data }: { packId: string; data: PackUpdateRequest }) =>
      updatePack(packId, data),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: VERSIONED_KEYS.owners(organization.organizationId),
      }),
    onError: (error: unknown) => {
      console.warn("[packs] Pack update refused", error);
    },
  });
}

export type RepositoryPackEdit = {
  repositoryId: string;
  /** The repository's new own pack list, built from its current one. */
  edit: (entries: readonly RepositoryPackEntryResponse[]) => PackRow[];
};

/**
 * Attaches a pack to a repository or removes it; owners only. The PUT
 * replaces the whole list, so the repository's current list is read fresh
 * first and changed by `edit`, then checked as the repository packs dialog
 * checks it. The caller shows the refusal, so the hook only logs it. A saved
 * list moves mount folders and publishes a release, so the pack reads and
 * the repository's releases reload.
 */
export function useEditRepositoryPacks() {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  const organizationId = organization.organizationId;
  return useMutation({
    mutationFn: async ({ repositoryId, edit }: RepositoryPackEdit) => {
      const current = await queryClient.fetchQuery({
        queryKey: VERSIONED_KEYS.repositoryPacks(organizationId, repositoryId),
        queryFn: () => getRepositoryPacks(repositoryId),
        staleTime: 0,
      });
      const entries = current.data?.packs;
      if (!entries) throw new Error("Kaya did not answer the repository's pack list.");
      const checked = checkRows(edit(entries));
      if (!checked.ok) throw new Error(checked.message);
      return updateRepositoryPacks(repositoryId, checked.data);
    },
    onSuccess: async (_response: unknown, { repositoryId }: RepositoryPackEdit) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: VERSIONED_KEYS.owners(organizationId) }),
        queryClient.invalidateQueries({
          queryKey: VERSIONED_KEYS.releases(organizationId, repositoryId),
        }),
      ]);
    },
    onError: (error: unknown) => {
      console.warn("[packs] Pack list change refused", error);
    },
  });
}
