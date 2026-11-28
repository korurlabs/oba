import { childrenOf, pageTitle } from "@/features/pages/lib/page-tree";
import type { PageListItem } from "@/features/pages/schemas";

/**
 * Whether a page answers a search: its title for the reader or its address
 * holds the text, without regard to case. The tree endpoint sends every page
 * at once, so the search runs here instead of on the server.
 */
export function pageMatchesSearch(page: PageListItem, search: string, uiLocale: string): boolean {
  const wanted = search.toLocaleLowerCase(uiLocale);
  return (
    pageTitle(page, uiLocale).toLocaleLowerCase(uiLocale).includes(wanted) ||
    page.slug.toLocaleLowerCase(uiLocale).includes(wanted)
  );
}

/**
 * The pages below `rootId` that a search keeps in the tree: each page that
 * answers it, and every page above one up to the root, so a hit is shown in
 * its place in the section rather than on its own. The root itself is left
 * out, as the tree shows only what lies below it.
 */
export function visiblePageIds(
  pages: readonly PageListItem[],
  rootId: string,
  search: string,
  uiLocale: string
): Set<string> {
  const visible = new Set<string>();
  const walk = (parentId: string, depth: number): boolean => {
    let anyKept = false;
    // The depth bound keeps a malformed tree with a cycle from looping.
    if (depth > pages.length) return false;
    for (const page of childrenOf(pages, parentId)) {
      const childKept = walk(page.id, depth + 1);
      if (childKept || pageMatchesSearch(page, search, uiLocale)) {
        visible.add(page.id);
        anyKept = true;
      }
    }
    return anyKept;
  };
  walk(rootId, 0);
  return visible;
}

/**
 * Which pages of the tree are folded. The member's own folds are kept apart
 * from those made during a search, so a search can open every page above a
 * hit without losing what the member had folded, and clearing it brings that
 * back. `searchKey` names the search on screen, null when there is none.
 */
export interface TreeFolds {
  own: ReadonlySet<string>;
  search: ReadonlySet<string>;
  searchKey: string | null;
}

export const NO_FOLDS: TreeFolds = { own: new Set(), search: new Set(), searchKey: null };

/** A key that changes whenever a search keeps other pages; null without a search. */
export function searchKeyOf(visibleIds: ReadonlySet<string> | undefined): string | null {
  return visibleIds ? [...visibleIds].sort().join("\n") : null;
}

/**
 * The folds for the search on screen: a new or changed search starts with
 * every page open, so each hit shows; the member's own folds stay untouched.
 * Returns `folds` itself when nothing changed.
 */
export function syncFoldsToSearch(folds: TreeFolds, searchKey: string | null): TreeFolds {
  if (folds.searchKey === searchKey) return folds;
  return { own: folds.own, search: new Set(), searchKey };
}

/** Folds or opens a page, in the search's folds while a search is on screen. */
export function toggleFold(folds: TreeFolds, pageId: string): TreeFolds {
  const current = folds.searchKey === null ? folds.own : folds.search;
  const next = new Set(current);
  if (next.has(pageId)) next.delete(pageId);
  else next.add(pageId);
  return folds.searchKey === null ? { ...folds, own: next } : { ...folds, search: next };
}

/** The pages folded on screen now. */
export function collapsedOf(folds: TreeFolds): ReadonlySet<string> {
  return folds.searchKey === null ? folds.own : folds.search;
}
