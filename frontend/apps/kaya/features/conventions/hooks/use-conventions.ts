"use client";

import { useQuery } from "@tanstack/react-query";

import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import { listConventions } from "../api";

/** The server reads the conventions of at most this many versions at once. */
export const CONVENTION_VERSIONS_MAX = 20;

const KEYS = {
  list: (organizationId: string, versionIds: readonly string[]) =>
    [...VERSIONED_KEYS.kinds(organizationId), "conventions", [...versionIds].sort()] as const,
};

/** The convention docs the server reads from the named versions; disabled while none is named. */
export function useConventions(versionIds: readonly string[]) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, versionIds),
    queryFn: () => listConventions({ versionId: [...versionIds] }),
    enabled: versionIds.length > 0 && versionIds.length <= CONVENTION_VERSIONS_MAX,
  });
}
