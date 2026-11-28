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
