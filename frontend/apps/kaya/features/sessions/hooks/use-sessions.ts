"use client";

import { useQuery } from "@tanstack/react-query";

import { useIdentity } from "@/lib/identity/identity-context";

import { getSessionFilters, listSessions, listRepositories } from "../api";
import type { SessionQueryRequest } from "../schemas";

/** The wall shows 60 tiles a page, the most the API returns. */
export const WALL_PAGE_SIZE = 60;

/** Every key starts with the organization, so answers of one never show under another. */
const KEYS = {
  all: (organizationId: string) => ["sessions", organizationId] as const,
  list: (organizationId: string, params: SessionQueryRequest) =>
    [...KEYS.all(organizationId), "list", params] as const,
  filters: (organizationId: string) => [...KEYS.all(organizationId), "filters"] as const,
  repositories: (organizationId: string) => ["repositories", organizationId, "list"] as const,
};

export function useSessions(params: SessionQueryRequest) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, params),
    queryFn: () => listSessions(params),
    // A new filter or page keeps the current tiles until its answer arrives,
    // but never the tiles of the organization the user just left.
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === organization.organizationId ? previous : undefined,
  });
}

export function useSessionFilters() {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.filters(organization.organizationId),
    queryFn: getSessionFilters,
  });
}

export function useRepositories() {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.repositories(organization.organizationId),
    queryFn: listRepositories,
  });
}
