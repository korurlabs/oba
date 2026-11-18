import type { Owner, PackResponse, RepositoryPackEntryResponse, RepositoryPackResponse } from "../schemas";

/** Each pack as an owner, with the repositories whose pack lists name it. */
export function ownersOf(
  packs: readonly PackResponse[],
  lists: readonly RepositoryPackResponse[]
): Owner[] {
  return packs.map((pack: PackResponse) => ({
    id: pack.id,
    name: pack.name,
    parentPackId: pack.parentPackId ?? null,
    currentVersionId: pack.currentVersionId ?? null,
    required: pack.required,
    mountPaths: pack.mountPaths,
    repositoryIds: lists
      .filter((list: RepositoryPackResponse) =>
        list.packs.some((entry: RepositoryPackEntryResponse) => entry.id === pack.id)
      )
      .map((list: RepositoryPackResponse) => list.repositoryId),
  }));
}

export function ownerById(owners: readonly Owner[], id: string): Owner | undefined {
  return owners.find((owner: Owner) => owner.id === id);
}

/**
 * A pack and its ancestors, root first. The walk stops after as many steps
 * as there are packs and at a parent the list does not hold, so a broken or
 * looping parent link cannot hang the page.
 */
export function chainOf(owners: readonly Owner[], packId: string): Owner[] {
  const chain: Owner[] = [];
  let current = ownerById(owners, packId);
  while (current && chain.length < owners.length && !chain.includes(current)) {
    chain.unshift(current);
    current = current.parentPackId ? ownerById(owners, current.parentPackId) : undefined;
  }
  return chain;
}

/** Every pack below a pack, nearest level first; the pack itself is not one. */
export function descendantIds(owners: readonly Owner[], packId: string): string[] {
  const found: string[] = [];
  let level = [packId];
  while (level.length > 0 && found.length < owners.length) {
    const next = owners
      .filter((owner: Owner) => owner.parentPackId !== null && level.includes(owner.parentPackId))
      .map((owner: Owner) => owner.id)
      .filter((id: string) => id !== packId && !found.includes(id));
    found.push(...next);
    level = next;
  }
  return found;
}

/** A pack of the tree with its child packs, each level by name. */
export type PackNode = { owner: Owner; children: PackNode[] };

function byName(left: Owner, right: Owner): number {
  return left.name.localeCompare(right.name);
}

/**
 * The organization's packs as a tree by `parentPackId`, root packs first,
 * each level by name. A pack whose parent the list does not hold is shown
 * as a root, and a pack caught in a parent loop is shown once as a root, so
 * every pack stays reachable whatever the links say.
 */
export function packTree(owners: readonly Owner[]): PackNode[] {
  const placed = new Set<string>();
  const childrenOf = (parentId: string): Owner[] =>
    owners
      .filter((owner: Owner) => owner.parentPackId === parentId && !placed.has(owner.id))
      .sort(byName);
  const build = (owner: Owner): PackNode => {
    placed.add(owner.id);
    return { owner, children: childrenOf(owner.id).map(build) };
  };
  const roots = owners
    .filter(
      (owner: Owner) => owner.parentPackId === null || ownerById(owners, owner.parentPackId) === undefined
    )
    .sort(byName)
    .map(build);
  const unplaced = owners.filter((owner: Owner) => !placed.has(owner.id)).sort(byName);
  for (const owner of unplaced) {
    if (!placed.has(owner.id)) roots.push(build(owner));
  }
  return roots;
}

/** The tree in reading order with each pack's depth, for a flat list such as a select. */
export function flattenPackTree(nodes: readonly PackNode[], depth = 0): { owner: Owner; depth: number }[] {
  return nodes.flatMap((node: PackNode) => [
    { owner: node.owner, depth },
    ...flattenPackTree(node.children, depth + 1),
  ]);
}
