import { findSectionRoot } from "@/features/pages/lib/page-tree";
import type { PageListItem } from "@/features/pages/schemas";
import { SECTION_SLUGS, type SectionSlug } from "@/lib/sections";

/**
 * A section as the overview shows it: null when the section has no root the
 * user can see (not set up, or no level on it), else the number of live pages
 * under its root, the root itself not counted.
 */
export type SectionCount = { pageCount: number } | null;

/**
 * The page count of every section, from the flat page tree. Pages are counted
 * by walking the parent links down from each section root, so the count does
 * not depend on how the server fills `rootId`.
 */
export function sectionCounts(pages: readonly PageListItem[]): Record<SectionSlug, SectionCount> {
  const childrenByParent = new Map<string, string[]>();
  for (const page of pages) {
    if (page.parentId === null) continue;
    const siblings = childrenByParent.get(page.parentId);
    if (siblings) siblings.push(page.id);
    else childrenByParent.set(page.parentId, [page.id]);
  }

  const countBelow = (rootId: string): number => {
    let count = 0;
    const pending = [...(childrenByParent.get(rootId) ?? [])];
    while (pending.length > 0) {
      const id = pending.pop()!;
      count += 1;
      pending.push(...(childrenByParent.get(id) ?? []));
    }
    return count;
  };

  return Object.fromEntries(
    SECTION_SLUGS.map((slug) => {
      const root = findSectionRoot(pages, slug);
      return [slug, root ? { pageCount: countBelow(root.id) } : null];
    })
  ) as Record<SectionSlug, SectionCount>;
}
