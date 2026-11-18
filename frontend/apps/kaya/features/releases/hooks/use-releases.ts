"use client";

import { useQuery } from "@tanstack/react-query";

import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import { getRelease, listReleases } from "../api";

/** The newest release is read as the first entry of a one-row page. */
const LATEST_RELEASE_PAGE = { page: 1, amount: 1 } as const;

/**
 * A stored release never changes, so its detail stays fresh for the
 * session; the pages and the newest release move with every approval.
 */
const KEYS = {
  page: (organizationId: string, repositoryId: string, page: number, amount: number) =>
    [...VERSIONED_KEYS.releases(organizationId, repositoryId), "page", page, amount] as const,
  latest: (organizationId: string, repositoryId: string) =>
    [...VERSIONED_KEYS.releases(organizationId, repositoryId), "latest"] as const,
  detail: (organizationId: string, repositoryId: string, number: number) =>
    [...VERSIONED_KEYS.releases(organizationId, repositoryId), "detail", number] as const,
};

const IMMUTABLE = { staleTime: Infinity } as const;

function latestReleaseQuery(organizationId: string, repositoryId: string) {
  return {
    queryKey: KEYS.latest(organizationId, repositoryId),
    queryFn: () => listReleases(repositoryId, LATEST_RELEASE_PAGE),
    enabled: repositoryId !== "",
  };
}

/** The newest release summary of a repository; `data.data` is empty before its first release. */
export function useLatestRelease(repositoryId: string) {
  const { organization } = useIdentity();
  return useQuery(latestReleaseQuery(organization.organizationId, repositoryId));
}

/** One page of a repository's releases, newest first. */
export function useReleases(repositoryId: string, page: number, amount: number) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.page(organization.organizationId, repositoryId, page, amount),
    queryFn: () => listReleases(repositoryId, { page, amount }),
    enabled: repositoryId !== "",
  });
}

export function useRelease(repositoryId: string, number: number) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.detail(organization.organizationId, repositoryId, number),
    queryFn: () => getRelease(repositoryId, number),
    enabled: repositoryId !== "" && number > 0,
    ...IMMUTABLE,
  });
}
