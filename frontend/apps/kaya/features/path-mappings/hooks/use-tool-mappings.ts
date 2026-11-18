"use client";

import { useQuery } from "@tanstack/react-query";

import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import { listToolMappings } from "../api";
import { PATH_MAPPING_VERSIONS_MAX } from "./use-path-mappings";

const KEYS = {
  list: (organizationId: string, versionIds: readonly string[]) =>
    [...VERSIONED_KEYS.kinds(organizationId), "tool-mappings", [...versionIds].sort()] as const,
};

/**
 * What every AI tool reads, with each read's state in the named versions. One
 * answer holds all tools, so switching between them makes no request; the read
 * waits until a tool is picked. It sits under the kind lists' key, so a draft
 * save that adds or removes a link changes the states.
 */
export function useToolMappings(versionIds: readonly string[], ready: boolean) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, versionIds),
    queryFn: () => listToolMappings({ versionId: [...versionIds] }),
    enabled:
      ready && !!organization.organizationId && versionIds.length <= PATH_MAPPING_VERSIONS_MAX,
  });
}
