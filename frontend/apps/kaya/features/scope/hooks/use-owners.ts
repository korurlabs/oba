"use client";

import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";

import { useRepositories } from "@/features/sessions/hooks/use-sessions";
import type { RepositoryResponse } from "@/features/sessions/schemas";
import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import { createPack, getRepositoryPacks, listPacks } from "../api";
import { ownersOf } from "../components/owners";
import type { Owner, PackCreateRequest } from "../schemas";

export type OwnersState = {
  /** Undefined until every read has answered. */
  owners: Owner[] | undefined;
  repositories: RepositoryResponse[] | undefined;
  /** The first read that failed with nothing to show; null otherwise. */
  error: unknown;
  retry: () => void;
};

/**
 * Every pack of the organization, with each repository's pack list: the
 * reads behind the pack tree and the labels of changes.
 */
export function useOwners(): OwnersState {
  const { organization } = useIdentity();
  const organizationId = organization.organizationId;
  const repositories = useRepositories();
  const packs = useQuery({
    queryKey: VERSIONED_KEYS.packs(organizationId),
    queryFn: () => listPacks(),
  });
  const repositoryList = repositories.data?.data ?? [];
  const lists = useQueries({
    queries: repositoryList.map((repository: RepositoryResponse) => ({
      queryKey: VERSIONED_KEYS.repositoryPacks(organizationId, repository.id),
      queryFn: () => getRepositoryPacks(repository.id),
    })),
  });

  const reads = [repositories, packs, ...lists];
  const failed = reads.find((query) => query.error !== null && query.data === undefined);
  const repositoryData = repositories.data?.data ?? undefined;
  const packData = packs.data?.data ?? undefined;
  const ready =
    repositoryData !== undefined &&
    packData !== undefined &&
    lists.every((query) => query.data !== undefined);
  return {
    owners:
      ready && repositoryData && packData
        ? ownersOf(
            packData,
            lists.flatMap((query) => (query.data?.data ? [query.data.data] : []))
          )
        : undefined,
    repositories: repositoryData,
    error: failed?.error ?? null,
    retry: () => void failed?.refetch(),
  };
}

/**
 * Creates a root pack, or a child pack of the given parent; owners only. The
 * dialog that runs it shows the refusal, so the hook only logs it. A new pack
 * has no version and is used by no repository, so only the packs reload.
 */
export function useCreatePack() {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  return useMutation({
    mutationFn: (data: PackCreateRequest) => createPack(data),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: VERSIONED_KEYS.packs(organization.organizationId),
      }),
    onError: (error: unknown) => {
      console.warn("[scope] Pack creation refused", error);
    },
  });
}
