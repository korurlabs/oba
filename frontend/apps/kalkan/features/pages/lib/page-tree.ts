import { KalkanLocale, PAGE_LIMITS, type PageListItem } from "../schemas";

/**
 * The content locale that fits the interface locale: Turkish for `tr`, else
 * English, the language a Dutch reader is likelier to read than Turkish.
 */
export function contentLocaleOf(uiLocale: string): KalkanLocale {
  return uiLocale === "tr" ? KalkanLocale.TR : KalkanLocale.EN;
}

/**
 * The title a page shows to a reader of `uiLocale`: the title in the fitting
 * content locale, else the other one, else the slug, so a page without a
 * translation still has a name.
 */
export function pageTitle(page: Pick<PageListItem, "titles" | "slug">, uiLocale: string): string {
  const preferred = contentLocaleOf(uiLocale);
  const other = preferred === KalkanLocale.TR ? KalkanLocale.EN : KalkanLocale.TR;
  return page.titles[preferred] ?? page.titles[other] ?? page.slug;
}

/** The live children of `parentId` in the flat tree, in their sibling order. */
export function childrenOf(pages: readonly PageListItem[], parentId: string): PageListItem[] {
  return pages.filter((page) => page.parentId === parentId).sort((a, b) => a.position - b.position);
}

/**
 * The root page whose slug names the section, compared without regard to
 * case as the server compares reserved root slugs; null when the section has
 * not been set up or the caller holds no level on it.
 */
export function findSectionRoot(pages: readonly PageListItem[], slug: string): PageListItem | null {
  const wanted = slug.toLowerCase();
  return pages.find((page) => page.parentId === null && page.slug.toLowerCase() === wanted) ?? null;
}

/** How deep a page sits: a root page is at depth 1. */
export function depthOf(page: Pick<PageListItem, "path">): number {
  return page.path.split("/").length;
}

/**
 * The ids of the pages above `pageId`, its parent first and its root last,
 * so a menu can open the branch that holds the page in view. Empty for a
 * root page, for no page, and for an id the tree does not hold.
 */
export function ancestorIds(pages: readonly PageListItem[], pageId: string | null): string[] {
  if (pageId === null) return [];
  const byId = new Map(pages.map((page) => [page.id, page]));
  const ids: string[] = [];
  let parentId = byId.get(pageId)?.parentId ?? null;
  // The depth limit bounds the walk, so a malformed tree with a cycle cannot hang it.
  while (parentId !== null && ids.length < PAGE_LIMITS.maxDepth) {
    ids.push(parentId);
    parentId = byId.get(parentId)?.parentId ?? null;
  }
  return ids;
}

/**
 * The id of the page a path shows, such as `/pages/{id}` or its edit and
 * history screens; null for any other path, such as a section or the
 * new-page screen.
 */
export function pageIdFromPathname(pathname: string): string | null {
  const match = /^\/pages\/([^/]+)/.exec(pathname);
  if (!match || match[1] === "new") return null;
  return match[1] ?? null;
}
