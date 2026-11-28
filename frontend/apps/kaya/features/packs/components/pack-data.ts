import { STATUS_BADGES } from "@/features/changes/components/change-data";
import { packTree } from "@/features/scope/components/owners";
import type { Owner, PackRepositoryResponse } from "@/features/scope/schemas";
import type { RepositoryResponse } from "@/features/sessions/schemas";
import type { VersionResponse, VersionStatus } from "@/features/versions/schemas";
import { describeError } from "@/lib/api/error-text";
import { KayaApiError } from "@/lib/api/errors";

type BadgeVariant = (typeof STATUS_BADGES)[VersionStatus]["variant"];

/**
 * The pack the page shows: the one the URL names, even when it no longer
 * exists (the page then says so), else the first pack of the tree; null
 * when the organization has no pack.
 */
export function shownPackId(requested: string | null, owners: readonly Owner[]): string | null {
  if (requested) return requested;
  return packTree(owners)[0]?.owner.id ?? null;
}

/**
 * The repositories an owner may add the pack to, by name: not archived,
 * since an archived repository takes no changes, and not one that composes
 * the pack already.
 */
export function attachableRepositories(
  repositories: readonly RepositoryResponse[],
  composing: readonly PackRepositoryResponse[]
): RepositoryResponse[] {
  const taken = new Set(composing.map((repository: PackRepositoryResponse) => repository.id));
  return repositories
    .filter(
      (repository: RepositoryResponse) =>
        repository.status !== "ARCHIVED" && !taken.has(repository.id)
    )
    .sort((left: RepositoryResponse, right: RepositoryResponse) =>
      left.name.localeCompare(right.name)
    );
}

/** One row of a pack's version table. */
export type VersionRow = {
  id: string;
  number: number;
  statusLabel: string;
  statusVariant: BadgeVariant;
  createdAt: string;
  /** The proposer's display name, or the user id when the member list does not name them. */
  by: string;
};

/** The versions as table rows, in the server's order (newest first). */
export function versionRows(
  versions: readonly VersionResponse[],
  memberNames: ReadonlyMap<string, string>
): VersionRow[] {
  return versions.map((version: VersionResponse) => {
    const badge = STATUS_BADGES[version.status];
    return {
      id: version.id,
      number: version.number,
      statusLabel: badge.label,
      statusVariant: badge.variant,
      createdAt: version.createdAt,
      by: memberNames.get(version.proposedBy) ?? version.proposedBy,
    };
  });
}

/** The server's refusal of a rename: the pack is in use, or the new name is taken. */
const PACK_IN_USE = "KAY_CFG_035";
const PACK_NAME_TAKEN = "KAY_CFG_008";

/** A refusal to show under the pack's name; `fixed` adds the tip that a used pack keeps its name. */
export type RenameFailure = { message: string; fixed: boolean };

/** What a refused rename shows: only a pack in use gets the tip, a taken name its own sentence. */
export function renameFailure(error: unknown): RenameFailure {
  if (error instanceof KayaApiError && error.code === PACK_IN_USE) {
    return { message: describeError(error), fixed: true };
  }
  if (error instanceof KayaApiError && error.code === PACK_NAME_TAKEN) {
    return { message: "That name is already taken.", fixed: false };
  }
  return { message: describeError(error), fixed: false };
}
