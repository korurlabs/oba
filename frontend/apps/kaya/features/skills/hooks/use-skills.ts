"use client";

import { useQuery } from "@tanstack/react-query";

import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import { listSkills } from "../api";

/** The server reads the skills of at most this many versions at once. */
export const SKILL_VERSIONS_MAX = 20;

const KEYS = {
  list: (organizationId: string, versionIds: readonly string[]) =>
    [...VERSIONED_KEYS.kinds(organizationId), "skills", [...versionIds].sort()] as const,
};

/** The skills the server reads from the named versions; disabled while none is named. */
export function useSkills(versionIds: readonly string[]) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, versionIds),
    queryFn: () => listSkills({ versionId: [...versionIds] }),
    enabled: versionIds.length > 0 && versionIds.length <= SKILL_VERSIONS_MAX,
  });
}
