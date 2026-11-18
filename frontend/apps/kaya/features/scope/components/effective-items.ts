/**
 * A pack's effective items as the pages read them: which own items override
 * an ancestor's, which inherited items the pack may override and which are
 * locked, and the inherited files the "Show inherited" view lists beside the
 * pack's own. An item is a file, or a whole skill directory, which is
 * inherited and overridden as one.
 */

import type { VersionDetailResponse } from "@/features/versions/schemas";

import type { EffectiveItemResponse, EffectiveOverrideResponse, Owner } from "../schemas";
import { filesFromVersion, type ScopeFile } from "./scope-files";

const SKILLS_PREFIX = ".agents/skills/";

/** The item a path belongs to: `.agents/skills/<name>/` for every file of a skill, else the path itself. */
export function itemOf(path: string): string {
  if (!path.startsWith(SKILLS_PREFIX)) return path;
  const end = path.indexOf("/", SKILLS_PREFIX.length);
  return end < 0 ? path : path.slice(0, end + 1);
}

/** The effective items by item path. */
export function effectiveByItem(
  items: readonly EffectiveItemResponse[]
): Map<string, EffectiveItemResponse> {
  return new Map(items.map((item: EffectiveItemResponse) => [item.path, item]));
}

/** The effective item a file belongs to; undefined when the view does not name it. */
export function effectiveOf(
  effective: ReadonlyMap<string, EffectiveItemResponse>,
  path: string
): EffectiveItemResponse | undefined {
  return effective.get(itemOf(path));
}

/**
 * The files of the inherited items, each read from the ancestor version that
 * gives it. Versions not loaded yet are left out until they arrive.
 */
export function inheritedFiles(
  items: readonly EffectiveItemResponse[],
  versions: readonly VersionDetailResponse[]
): ScopeFile[] {
  const byId = new Map(versions.map((version: VersionDetailResponse) => [version.id, version]));
  const wanted = new Map(
    items
      .filter((item: EffectiveItemResponse) => !item.own && item.sourceVersionId)
      .map((item: EffectiveItemResponse) => [item.path, item.sourceVersionId as string])
  );
  return [...new Set(wanted.values())].flatMap((versionId: string) => {
    const version = byId.get(versionId);
    if (!version) return [];
    return filesFromVersion(version).filter(
      (file: ScopeFile) => wanted.get(itemOf(file.path)) === versionId
    );
  });
}

/**
 * The pack's own files with the inherited ones added, by path. An item the
 * pack holds itself, such as one a draft just overrode, keeps only its own files.
 */
export function withInherited(own: readonly ScopeFile[], inherited: readonly ScopeFile[]): ScopeFile[] {
  const ownItems = new Set(own.map((file: ScopeFile) => itemOf(file.path)));
  return [
    ...own,
    ...inherited.filter((file: ScopeFile) => !ownItems.has(itemOf(file.path))),
  ].sort((left: ScopeFile, right: ScopeFile) =>
    left.path < right.path ? -1 : left.path > right.path ? 1 : 0
  );
}

function packName(owners: readonly Owner[], packId: string): string {
  return owners.find((owner: Owner) => owner.id === packId)?.name ?? "another pack";
}

/** Why an inherited item cannot be overridden, as its lock's tooltip says it; null when it can. */
export function lockText(item: EffectiveItemResponse, owners: readonly Owner[]): string | null {
  if (!item.locked) return null;
  const name = packName(owners, item.sourcePackId);
  return item.locked === "REQUIRED" ? `Required by ${name}` : `Guardrail, set by ${name}`;
}

/**
 * The server's refusal (KAY_CFG_031) of a save at a path whose item the pack
 * inherits locked; null when the path is free.
 */
export function lockRefusal(
  path: string,
  effective: ReadonlyMap<string, EffectiveItemResponse>,
  owners: readonly Owner[]
): string | null {
  const item = effectiveOf(effective, path.trim());
  if (!item || item.own || !item.locked) return null;
  const name = packName(owners, item.sourcePackId);
  return item.locked === "REQUIRED"
    ? `The item ${item.path} is locked: pack ${name} is required, so no descendant can override it`
    : `The item ${item.path} is locked: it is a guardrail of pack ${name}, so no descendant can override it`;
}

/** "Overrides org", the badge of an own item that replaces an ancestor's. */
export function overridesLabel(override: EffectiveOverrideResponse, owners: readonly Owner[]): string {
  return `Overrides ${packName(owners, override.basePackId)}`;
}

/** What changed above an override, in one sentence; null when nothing did. */
export function overrideStatusText(
  override: EffectiveOverrideResponse,
  owners: readonly Owner[]
): string | null {
  const name = packName(owners, override.basePackId);
  if (override.status === "PARENT_REMOVED") {
    return `${name} no longer gives this item. Your pack keeps it as its own item until you decide.`;
  }
  if (override.status !== "PARENT_UPDATED") return null;
  const parent = override.parentItemVersion;
  const base = override.baseItemVersion;
  if (parent != null && base != null) return `${name} updated to v${parent}, you override v${base}.`;
  if (parent != null) {
    return `${name} is at v${parent}; the version your override is based on is not known.`;
  }
  return `${name} changed this item since your override.`;
}
