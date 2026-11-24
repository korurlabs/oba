import { isSectionSlug, type SectionSlug } from "@/lib/sections";

/**
 * The section a root slug names. Reserved root slugs are compared without
 * regard to case, as the server and the sidebar compare them; null for a root
 * that names no section of the sidebar.
 */
export function sectionOfRootSlug(rootSlug: string): SectionSlug | null {
  const slug = rootSlug.toLowerCase();
  return isSectionSlug(slug) ? slug : null;
}

/**
 * The name a listed page shows: its title, or, for a page without any
 * translation, the last slug of its path, so the row still names the page.
 */
export function listedPageTitle(page: { title: string | null; path: string }): string {
  if (page.title) return page.title;
  const segments = page.path.split("/").filter(Boolean);
  return segments.at(-1) ?? page.path;
}
