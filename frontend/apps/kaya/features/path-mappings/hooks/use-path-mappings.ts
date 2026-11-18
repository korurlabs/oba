"use client";

import { useQuery } from "@tanstack/react-query";

import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import { listPathMappings } from "../api";

/** The server counts the files of at most this many versions at once. */
export const PATH_MAPPING_VERSIONS_MAX = 20;

const KEYS = {
  list: (organizationId: string, versionIds: readonly string[]) =>
    [...VERSIONED_KEYS.kinds(organizationId), "path-mappings", [...versionIds].sort()] as const,
};

/**
 * The path mapping rows with their counts in the named versions. The rows are
 * static, so the read runs with no version as well and then answers without
 * counts. It sits under the kind lists' key, so a draft save recounts.
 * `ready` holds the read back while the scope's versions are still unknown,
 * so the page does not read once without counts and again with them.
 */
export function usePathMappings(versionIds: readonly string[], ready: boolean = true) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, versionIds),
    queryFn: () => listPathMappings({ versionId: [...versionIds] }),
    enabled:
      ready && !!organization.organizationId && versionIds.length <= PATH_MAPPING_VERSIONS_MAX,
  });
}
