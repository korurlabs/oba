"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { getPack, updatePack } from "@/features/scope/api";
import type { PackUpdateRequest } from "@/features/scope/schemas";
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
