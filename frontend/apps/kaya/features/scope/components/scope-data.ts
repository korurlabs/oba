/**
 * The scope the Rules, Skills, Allowlist and Conventions pages read: a pack
 * (its current version, whose drafts the page edits) or a repository (its
 * newest release, which composes the chains of its packs, read only). It
 * lives in the URL as `scope`, written `pack:<packId>` or
 * `repository:<repositoryId>`, so a link opens the same view; `release` picks
 * an older release of the repository.
 */

import type { RepositoryResponse } from "@/features/sessions/schemas";

import type { Owner } from "../schemas";
import { ownerById } from "./owners";

export type Scope = { kind: "REPOSITORY"; repositoryId: string } | { kind: "PACK"; packId: string };

export type ScopeOption = {
  value: string;
  label: string;
  scope: Scope;
};

export type ScopeOptions = {
  repositories: ScopeOption[];
  packs: ScopeOption[];
};

const REPOSITORY_PREFIX = "repository:";
const PACK_PREFIX = "pack:";

export function scopeValue(scope: Scope): string {
  if (scope.kind === "REPOSITORY") return `${REPOSITORY_PREFIX}${scope.repositoryId}`;
  return `${PACK_PREFIX}${scope.packId}`;
}

/** The repository whose releases a scope shows; null for a pack. */
export function scopeRepositoryId(scope: Scope | null): string | null {
  return scope?.kind === "REPOSITORY" ? scope.repositoryId : null;
}

/** Reads the URL value; null for a missing or malformed one. */
export function parseScope(value: string | null | undefined): Scope | null {
  if (!value) return null;
  if (value.startsWith(REPOSITORY_PREFIX) && value.length > REPOSITORY_PREFIX.length) {
    return { kind: "REPOSITORY", repositoryId: value.slice(REPOSITORY_PREFIX.length) };
  }
  if (value.startsWith(PACK_PREFIX) && value.length > PACK_PREFIX.length) {
    return { kind: "PACK", packId: value.slice(PACK_PREFIX.length) };
  }
  return null;
}

function byLabel(left: ScopeOption, right: ScopeOption): number {
  return left.label.localeCompare(right.label);
}

/** Every pack, and every repository that is not archived, each by name. */
export function scopeOptions(
  repositories: readonly RepositoryResponse[],
  owners: readonly Owner[]
): ScopeOptions {
  const repositoryOptions = repositories
    .filter((repository: RepositoryResponse) => repository.status !== "ARCHIVED")
    .map((repository: RepositoryResponse) => {
      const scope: Scope = { kind: "REPOSITORY", repositoryId: repository.id };
      return { value: scopeValue(scope), label: repository.name, scope };
    })
    .sort(byLabel);
  const packs = owners
    .map((owner: Owner) => {
      const scope: Scope = { kind: "PACK", packId: owner.id };
      return { value: scopeValue(scope), label: owner.name, scope };
    })
    .sort(byLabel);
  return { repositories: repositoryOptions, packs };
}

/** Every option, packs first, then repositories. */
export function allOptions(options: ScopeOptions): ScopeOption[] {
  return [...options.packs, ...options.repositories];
}

/**
 * The scope to show: the URL's when it names an offered option, otherwise
 * the first pack, otherwise the first repository; null when there is neither.
 * Packs come first because only a pack is edited.
 */
export function resolveScope(value: string | null, options: ScopeOptions): Scope | null {
  const all = allOptions(options);
  const chosen = all.find((option: ScopeOption) => option.value === value);
  return (chosen ?? all[0])?.scope ?? null;
}

/**
 * The release a repository scope shows: the one the URL names (`release`),
 * so a session can link the release it ran under, otherwise the newest; 0
 * when the repository has none.
 */
export function releaseToShow(requested: number | null, newest: number): number {
  return requested !== null && Number.isInteger(requested) && requested > 0 ? requested : newest;
}

/**
 * The release the URL asks for, kept only when the scope shown is the
 * repository the URL named. Release numbers count one repository's releases,
 * so a release asked for an archived or unknown repository, which falls back
 * to another scope, must not show that other repository's release.
 */
export function requestedRelease(
  value: string | null,
  scope: Scope | null,
  release: number | null
): number | null {
  if (scope === null || scope.kind === "PACK" || value !== scopeValue(scope)) return null;
  return release !== null && Number.isInteger(release) && release > 0 ? release : null;
}

/** The Rules view of one release of a repository, which names every path it delivers. */
export function releaseHref(repositoryId: string, number: number): string {
  const scope = scopeValue({ kind: "REPOSITORY", repositoryId });
  return `/rules?scope=${encodeURIComponent(scope)}&release=${number}`;
}

export function scopeLabel(scope: Scope, options: ScopeOptions): string {
  const value = scopeValue(scope);
  return allOptions(options).find((option: ScopeOption) => option.value === value)?.label ?? value;
}

/**
 * The pack an edit in a scope changes. A repository scope is read only:
 * each of its files is edited in the pack that gives it. Null for a
 * repository and for a pack that no longer exists.
 */
export function editTarget(scope: Scope, owners: readonly Owner[]): Owner | null {
  if (scope.kind === "REPOSITORY") return null;
  return ownerById(owners, scope.packId) ?? null;
}

/** The scope that edits a pack's files. */
export function scopeOfOwner(owner: Pick<Owner, "id">): Scope {
  return { kind: "PACK", packId: owner.id };
}
