import { STATUS_BADGES } from "@/features/changes/components/change-data";
import type { ReleaseDetailResponse } from "@/features/releases/schemas";
import type { VersionDetailResponse } from "@/features/versions/schemas";
import { dateTimeText } from "@/lib/format-time";

import type { Scope } from "./scope-data";

/** What the scope source reads of a loaded scope. */
export type SourceScope = {
  scope: Scope;
  release: Pick<ReleaseDetailResponse, "number" | "createdAt" | "fileCount"> | null;
  newestReleaseNumber: number | null;
  version: Pick<VersionDetailResponse, "number" | "status" | "files"> | null;
};

/** Which release or version the page shows, in one sentence; `name` is the scope's label. */
export function scopeSourceText(source: SourceScope, name: string): string {
  if (source.release) {
    return `${name}: config release ${source.release.number} of ${dateTimeText(source.release.createdAt)}, ${source.release.fileCount} delivered paths. Read only: each file is edited in its pack.`;
  }
  if (source.version) {
    return `Pack ${name}: version ${source.version.number} (${STATUS_BADGES[source.version.status].label.toLowerCase()}), ${source.version.files.length} files.`;
  }
  return `Pack ${name}: no approved version yet. Its first change starts empty.`;
}

/** The scope in a few words, "Pack village" or the repository's name, for the slim source line. */
export function scopeSourceLabel(source: Pick<SourceScope, "scope">, name: string): string {
  return source.scope.kind === "PACK" ? `Pack ${name}` : name;
}

/** The newest release number when the page shows an older release of a repository, else null. */
export function newerRelease(
  source: Pick<SourceScope, "release" | "newestReleaseNumber">
): number | null {
  const newest = source.newestReleaseNumber;
  return source.release !== null && newest !== null && source.release.number !== newest
    ? newest
    : null;
}

/** A pack's grey mark in the pack tree, `v<N>` of its current version; null without one. */
export function packVersionMark(
  version: Pick<VersionDetailResponse, "number"> | null
): string | null {
  return version === null ? null : `v${version.number}`;
}

/** A repository's grey mark in the pack tree, `r<N>` of its newest release; null without one. */
export function releaseMark(release: Pick<ReleaseDetailResponse, "number"> | null): string | null {
  return release === null ? null : `r${release.number}`;
}

/** The tooltip of a pack's row in the tree: its current version, status and file count. */
export function packVersionTip(
  version: Pick<VersionDetailResponse, "number" | "status" | "files"> | null
): string {
  if (version === null) return "No approved version yet.";
  return `Version ${version.number} (${STATUS_BADGES[version.status].label.toLowerCase()}), ${version.files.length} files.`;
}

/** The tooltip of a repository's row in the tree: its newest release, date and delivered paths. */
export function releaseTip(
  release: Pick<ReleaseDetailResponse, "number" | "createdAt" | "fileCount"> | null
): string {
  if (release === null) return "No config release yet.";
  return `Config release ${release.number} of ${dateTimeText(release.createdAt)}, ${release.fileCount} delivered paths.`;
}
