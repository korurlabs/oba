import type { SectionSlug } from "./sections";

/** The route of a section: its slug, the same as the slug of its root page. */
export function sectionHref(slug: SectionSlug): string {
  return `/${slug}`;
}

/** The route of a page; a page is opened by its id, wherever it sits in the tree. */
export function pageHref(pageId: string): string {
  return `/pages/${pageId}`;
}

/** The route that edits a page. */
export function pageEditHref(pageId: string): string {
  return `${pageHref(pageId)}/edit`;
}

/** The access screen of a section; levels are held on root pages only. */
export function pagePermissionsHref(rootPageId: string): string {
  return `${pageHref(rootPageId)}/permissions`;
}

/** The revisions of a page. */
export function pageHistoryHref(pageId: string): string {
  return `${pageHref(pageId)}/history`;
}

/** The route that creates a page. */
export const NEW_PAGE_HREF = "/pages/new";

/** The route that creates a page under the given parent. */
export function newPageHref(parentId: string): string {
  return `${NEW_PAGE_HREF}?${new URLSearchParams({ parent: parentId }).toString()}`;
}

/** The recently changed pages. */
export const RECENT_HREF = "/recent";

/** The deleted pages a member can restore. */
export const TRASH_HREF = "/trash";
