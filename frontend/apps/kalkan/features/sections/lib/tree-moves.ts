import { childrenOf } from "@/features/pages/lib/page-tree";
import type { PageListItem, PageTrashItem } from "@/features/pages/schemas";

/**
 * The step between the positions a reorder hands out. The server stores a
 * page's position as it is sent and renumbers no sibling, so positions are
 * sort keys, not indexes. A gap between neighbours lets a later move land
 * between them with one request instead of renumbering the siblings again.
 */
export const POSITION_GAP = 1024;

/** One position change the server is asked for: a page, its new parent and its sort key. */
export type MoveStep = { pageId: string; parentId: string; position: number };

/** Where a page goes: under `parentId`, at `index` among the other children of that parent. */
export type MoveTarget = { parentId: string; index: number };

/** A page of the tree with its children, in sibling order. */
export type PageNode = { page: PageListItem; children: PageNode[] };

/** The pages below `parentId` as nested nodes, each level in sibling order. */
export function buildNodes(pages: readonly PageListItem[], parentId: string): PageNode[] {
  return childrenOf(pages, parentId).map((page) => ({
    page,
    children: buildNodes(pages, page.id),
  }));
}

/** The ancestors of a page from its root down, the page itself left out. */
export function ancestorsOf(pages: readonly PageListItem[], pageId: string): PageListItem[] {
  const byId = new Map(pages.map((page) => [page.id, page]));
  const chain: PageListItem[] = [];
  const seen = new Set<string>([pageId]);
  let parentId = byId.get(pageId)?.parentId ?? null;
  while (parentId !== null && !seen.has(parentId)) {
    const parent = byId.get(parentId);
    if (!parent) break;
    chain.unshift(parent);
    seen.add(parent.id);
    parentId = parent.parentId;
  }
  return chain;
}

/** Whether `candidateId` is `pageId` itself or sits anywhere below it. */
export function isInSubtree(
  pages: readonly PageListItem[],
  pageId: string,
  candidateId: string
): boolean {
  if (candidateId === pageId) return true;
  return ancestorsOf(pages, candidateId).some((ancestor) => ancestor.id === pageId);
}

/** How many live pages sit below `pageId`, at any depth; a delete takes them along. */
export function descendantCountOf(pages: readonly PageListItem[], pageId: string): number {
  return childrenOf(pages, pageId).reduce(
    (count, child) => count + 1 + descendantCountOf(pages, child.id),
    0
  );
}

/**
 * The requests that put `pageId` at `target`. When the neighbours at the
 * target leave a free sort key between them, one request moves the page.
 * Otherwise the other children of the target parent are spread out by
 * `POSITION_GAP` first, in their current order, and the page is moved last,
 * so the tree is never left with two pages on one key.
 */
export function planMove(
  pages: readonly PageListItem[],
  pageId: string,
  target: MoveTarget
): MoveStep[] {
  const siblings = childrenOf(pages, target.parentId).filter((page) => page.id !== pageId);
  const index = Math.max(0, Math.min(target.index, siblings.length));

  if (siblings.length === 0) {
    return [{ pageId, parentId: target.parentId, position: 0 }];
  }
  if (index === siblings.length) {
    const last = siblings[siblings.length - 1]!;
    return [{ pageId, parentId: target.parentId, position: last.position + POSITION_GAP }];
  }

  const lower = index === 0 ? -1 : siblings[index - 1]!.position;
  const upper = siblings[index]!.position;
  if (upper - lower >= 2) {
    return [{ pageId, parentId: target.parentId, position: Math.floor((lower + upper) / 2) }];
  }

  const steps: MoveStep[] = [];
  siblings.forEach((sibling, siblingIndex) => {
    const position = (siblingIndex + (siblingIndex >= index ? 1 : 0)) * POSITION_GAP;
    if (sibling.position !== position) {
      steps.push({ pageId: sibling.id, parentId: target.parentId, position });
    }
  });
  steps.push({ pageId, parentId: target.parentId, position: index * POSITION_GAP });
  return steps;
}

/** The kinds of move the tree offers on a page. */
export type MoveKind = "up" | "down" | "indent" | "outdent";

/**
 * Where a move of `kind` takes a page of the section under `rootId`, or null
 * when it cannot: the first page cannot go up or be indented, the last cannot
 * go down, and a direct child of the root cannot be outdented, because a page
 * never leaves its section or becomes a root.
 */
export function moveTargetOf(
  pages: readonly PageListItem[],
  page: PageListItem,
  kind: MoveKind,
  rootId: string
): MoveTarget | null {
  if (page.parentId === null) return null;
  const siblings = childrenOf(pages, page.parentId);
  const index = siblings.findIndex((sibling) => sibling.id === page.id);
  if (index < 0) return null;

  switch (kind) {
    case "up":
      return index === 0 ? null : { parentId: page.parentId, index: index - 1 };
    case "down":
      return index === siblings.length - 1 ? null : { parentId: page.parentId, index: index + 1 };
    case "indent": {
      const previous = index === 0 ? undefined : siblings[index - 1];
      return previous
        ? { parentId: previous.id, index: childrenOf(pages, previous.id).length }
        : null;
    }
    case "outdent": {
      if (page.parentId === rootId) return null;
      const parent = pages.find((candidate) => candidate.id === page.parentId);
      if (!parent || parent.parentId === null) return null;
      const parentIndex = childrenOf(pages, parent.parentId).findIndex(
        (candidate) => candidate.id === parent.id
      );
      return { parentId: parent.parentId, index: parentIndex + 1 };
    }
  }
}

/**
 * Where a drop puts the dragged page: just before the page it was dropped
 * on, under that page's parent. Null when the drop would put the page into
 * its own subtree, onto the root, or nowhere new.
 */
export function dropTargetOf(
  pages: readonly PageListItem[],
  draggedId: string,
  onto: PageListItem
): MoveTarget | null {
  if (onto.parentId === null || isInSubtree(pages, draggedId, onto.id)) return null;
  const siblings = childrenOf(pages, onto.parentId).filter((page) => page.id !== draggedId);
  const index = siblings.findIndex((page) => page.id === onto.id);
  const current = childrenOf(pages, onto.parentId).map((page) => page.id);
  const draggedIndex = current.indexOf(draggedId);
  if (draggedIndex >= 0 && current[draggedIndex + 1] === onto.id) return null;
  return { parentId: onto.parentId, index };
}

/**
 * The deleted pages of a section: the trash entries whose path is the
 * section's root or lies below it. Paths start with the root's slug, which
 * the server compares without regard to case.
 */
export function deletedPagesOf(trash: readonly PageTrashItem[], rootSlug: string): PageTrashItem[] {
  const root = rootSlug.toLowerCase();
  return trash.filter((item) => {
    const path = item.path.toLowerCase();
    return path === root || path.startsWith(`${root}/`);
  });
}
