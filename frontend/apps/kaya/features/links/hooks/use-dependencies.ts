"use client";

import { useQuery } from "@tanstack/react-query";

import type { ScopeFile } from "@/features/scope/components/scope-files";
import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import { getDependencies } from "../api";
import { DEPENDENCY_VERSIONS_MAX } from "../schemas";

/**
 * Under the kind lists: dependencies are read from the index of rule
 * conventions and skill parts that every draft write
 * refreshes, and a draft write reloads the kind lists.
 */
export const DEPENDENCY_KEYS = {
  dependencies: (organizationId: string, versionIds: readonly string[], path: string) =>
    [...VERSIONED_KEYS.kinds(organizationId), "dependencies", versionIds, path] as const,
};

/**
 * The versions a scope's dependency read names: the open file's own version
 * first, since the server takes the first version that gives a shared path,
 * then every other version of the scope, at most as many as one read takes.
 */
export function dependencyVersionIds(files: readonly ScopeFile[], file: ScopeFile): string[] {
  const ids = [file.versionId, ...files.map((entry: ScopeFile) => entry.versionId)];
  return [...new Set(ids)].filter((id: string) => id !== "").slice(0, DEPENDENCY_VERSIONS_MAX);
}

/** What one path of the scope links to and what uses it; disabled until both are known. */
export function useDependencies(versionIds: readonly string[], path: string) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: DEPENDENCY_KEYS.dependencies(organization.organizationId, versionIds, path),
    queryFn: () => getDependencies({ versionId: [...versionIds], path }),
    enabled: versionIds.length > 0 && path !== "",
  });
}
