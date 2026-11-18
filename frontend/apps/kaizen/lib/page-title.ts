/**
 * Brand shown in every browser tab title. Server metadata and client-side
 * `document.title` updates both read it from here, so a tab never mixes
 * brands when it switches between a server-rendered and a client-set title.
 */
const BRAND = "Korur";

/** Tab title of a page that sets no title of its own. */
export const APP_TITLE = `Kaizen | ${BRAND}`;

/** Next.js metadata title template applied to every page title. */
export const PAGE_TITLE_TEMPLATE = `%s | ${BRAND}`;

/** "{title} | Korur": the same string the template produces on the server. */
export function formatPageTitle(title: string): string {
  return PAGE_TITLE_TEMPLATE.replace("%s", title);
}
