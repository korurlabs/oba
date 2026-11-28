import type { PageTrashItem } from "@/features/pages/schemas";

/**
 * Whether a deleted page answers a search: its title or its path holds the
 * text, without regard to case. The trash endpoint sends every entry at once,
 * so the search runs here instead of on the server.
 */
export function trashItemMatchesSearch(
  item: PageTrashItem,
  search: string,
  uiLocale: string
): boolean {
  const wanted = search.toLocaleLowerCase(uiLocale);
  return [item.title, item.path].some(
    (text) => text !== null && text.toLocaleLowerCase(uiLocale).includes(wanted)
  );
}
