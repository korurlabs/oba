import { KalkanLocale, type PageListItem } from "../schemas";

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
