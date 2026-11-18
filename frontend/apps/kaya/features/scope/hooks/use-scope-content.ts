"use client";

import { useLatestRelease, useRelease } from "@/features/releases/hooks/use-releases";
import type { ReleaseDetailResponse } from "@/features/releases/schemas";
import { useVersion } from "@/features/versions/hooks/use-versions";
import type { VersionDetailResponse } from "@/features/versions/schemas";

import { filesFromRelease, filesFromVersion, type ScopeFile } from "../components/scope-files";
import { ownerById } from "../components/owners";
import { releaseToShow, scopeRepositoryId, type Scope } from "../components/scope-data";
import type { Owner } from "../schemas";

/** What a scope holds, once its reads have answered. */
export type ScopeContent =
  | { status: "loading" }
  | { status: "error"; error: unknown; retry: () => void }
  /** A repository without a release, or a pack that no longer exists. */
  | { status: "empty"; reason: "no-release" | "unknown-pack" }
  | {
      status: "ready";
      files: ScopeFile[];
      owners: Owner[];
      release: ReleaseDetailResponse | null;
      /** The repository's newest release number, which `release` may be older than; null for a pack. */
      newestReleaseNumber: number | null;
      /** The pack's current version; null for a repository and for a new pack without one. */
      version: VersionDetailResponse | null;
    };

/**
 * The files of a scope: the newest release of a repository, or the one
 * `release` names, or a pack's current version. A new pack has no version
 * yet and shows no files, so its first draft can still be started. Only the
 * reads of the chosen kind run.
 */
export function useScopeContent(
  scope: Scope | null,
  owners: Owner[],
  requestedRelease: number | null = null
): ScopeContent {
  const repositoryId = scopeRepositoryId(scope) ?? "";
  const latest = useLatestRelease(repositoryId);
  const newest = latest.data?.data?.[0]?.number ?? 0;
  const number = releaseToShow(requestedRelease, newest);
  const release = useRelease(repositoryId, number);
  const pack = scope?.kind === "PACK" ? ownerById(owners, scope.packId) : undefined;
  const version = useVersion(pack?.currentVersionId ?? "");

  const failed = [latest, release, version].find(
    (query) => query.error !== null && query.data === undefined
  );
  if (failed) {
    return { status: "error", error: failed.error, retry: () => void failed.refetch() };
  }
  if (scope === null) return { status: "loading" };

  if (scope.kind === "REPOSITORY") {
    if (latest.data === undefined) return { status: "loading" };
    if (number === 0) return { status: "empty", reason: "no-release" };
    const detail = release.data?.data;
    if (!detail) return { status: "loading" };
    return {
      status: "ready",
      files: filesFromRelease(detail),
      owners,
      release: detail,
      newestReleaseNumber: newest === 0 ? null : newest,
      version: null,
    };
  }
  if (!pack) return { status: "empty", reason: "unknown-pack" };
  if (!pack.currentVersionId) {
    return {
      status: "ready",
      files: [],
      owners,
      release: null,
      newestReleaseNumber: null,
      version: null,
    };
  }
  const detail = version.data?.data;
  if (!detail) return { status: "loading" };
  return {
    status: "ready",
    files: filesFromVersion(detail),
    owners,
    release: null,
    newestReleaseNumber: null,
    version: detail,
  };
}
