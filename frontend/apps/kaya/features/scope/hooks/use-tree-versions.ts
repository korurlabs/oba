"use client";

import { useQueries } from "@tanstack/react-query";

import type { ReleaseResponse } from "@/features/releases/schemas";
import { useVersionDetails } from "@/features/versions/hooks/use-versions";
import type { VersionDetailResponse } from "@/features/versions/schemas";
import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import { getRepositoryPacks } from "../api";
import type { Owner } from "../schemas";

/** What the tree knows of a pack's version or a repository's release: loading, failed, none, or the value. */
export type TreeMark<T> =
  { status: "loading" } | { status: "error" } | { status: "ready"; value: T | null };

/**
 * Each pack's current version by pack id, for the `v<N>` of the pack tree.
 * The versions are immutable and shared with the pages that show them.
 */
export function usePackVersions(
  owners: readonly Owner[]
): ReadonlyMap<string, TreeMark<VersionDetailResponse>> {
  const versioned = owners.filter((owner: Owner) => owner.currentVersionId !== null);
  const reads = useVersionDetails(versioned.map((owner: Owner) => owner.currentVersionId ?? ""));
  const marks = new Map<string, TreeMark<VersionDetailResponse>>();
  owners.forEach((owner: Owner) => {
    if (owner.currentVersionId === null) marks.set(owner.id, { status: "ready", value: null });
  });
  versioned.forEach((owner: Owner, index: number) => {
    const read = reads[index];
    const version = read?.data?.data ?? undefined;
    if (version !== undefined) marks.set(owner.id, { status: "ready", value: version });
    else if (read?.error) marks.set(owner.id, { status: "error" });
    else marks.set(owner.id, { status: "loading" });
  });
  return marks;
}

/**
 * Each repository's newest release by repository id, for the `r<N>` of the
 * pack tree. It reads the pack lists the scope already loads, so it adds no
 * request of its own.
 */
export function useNewestReleases(
  repositoryIds: readonly string[]
): ReadonlyMap<string, TreeMark<ReleaseResponse>> {
  const { organization } = useIdentity();
  const reads = useQueries({
    queries: repositoryIds.map((repositoryId: string) => ({
      queryKey: VERSIONED_KEYS.repositoryPacks(organization.organizationId, repositoryId),
      queryFn: () => getRepositoryPacks(repositoryId),
    })),
  });
  const marks = new Map<string, TreeMark<ReleaseResponse>>();
  repositoryIds.forEach((repositoryId: string, index: number) => {
    const read = reads[index];
    const list = read?.data?.data ?? undefined;
    if (list !== undefined)
      marks.set(repositoryId, { status: "ready", value: list.release ?? null });
    else if (read?.error) marks.set(repositoryId, { status: "error" });
    else marks.set(repositoryId, { status: "loading" });
  });
  return marks;
}
