/**
 * How many changed pages each list asks for. Kept out of the "use client"
 * views, so a server page can prefetch under the exact key the views read.
 */

/** How many changed pages the Recent page lists. */
export const RECENT_PAGE_LIMIT = 50;

/** How many changes the overview lists; the Recent page lists more. */
export const OVERVIEW_RECENT_LIMIT = 10;
