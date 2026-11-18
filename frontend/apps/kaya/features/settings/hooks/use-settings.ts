"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useIdentity } from "@/lib/identity/identity-context";

import { listRepositorySettings, updateRepository } from "../api";
import type { RepositoryUpdateRequest } from "../schemas";

const KEYS = {
  /** The wall's repository select shares this prefix, so an edit renames it there too. */
  repositories: (organizationId: string) => ["repositories", organizationId] as const,
};

export function useRepositorySettings() {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: [...KEYS.repositories(organization.organizationId), "settings"],
    queryFn: listRepositorySettings,
  });
}

export function useUpdateRepository() {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: RepositoryUpdateRequest }) =>
      updateRepository(id, data),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: KEYS.repositories(organization.organizationId),
      });
      // Sessions show the repository's name.
      await queryClient.invalidateQueries({ queryKey: ["sessions", organization.organizationId] });
    },
    onError: (error: unknown) => console.warn("[settings] Repository change refused", error),
  });
}
