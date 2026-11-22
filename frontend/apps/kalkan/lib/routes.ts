import type { SectionSlug } from "./sections";

/** The route of a section: its slug, the same as the slug of its root page. */
export function sectionHref(slug: SectionSlug): string {
  return `/${slug}`;
}

/** The route of a page; a page is opened by its id, wherever it sits in the tree. */
export function pageHref(pageId: string): string {
  return `/pages/${pageId}`;
}

/** The route that creates a page. */
export const NEW_PAGE_HREF = "/pages/new";

/** The recently changed pages. */
export const RECENT_HREF = "/recent";

/** The deleted pages a member can restore. */
export const TRASH_HREF = "/trash";
