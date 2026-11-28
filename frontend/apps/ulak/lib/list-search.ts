/**
 * Whether a row matches the search of its list. Every Ulak list endpoint
 * answers with the whole list, so the search runs in the browser: the
 * trimmed text must appear, ignoring case, in at least one of the row's
 * fields. An empty or missing search matches every row.
 */
export function matchesSearch(
  search: string | null | undefined,
  fields: readonly (string | null | undefined)[]
): boolean {
  const needle = search?.trim().toLocaleLowerCase();
  if (!needle) return true;
  for (const field of fields) {
    if (field && field.toLocaleLowerCase().includes(needle)) return true;
  }
  return false;
}

/** Whether a search narrows its list, so the list can tell "no match" from "nothing yet". */
export function isSearching(search: string | null | undefined): boolean {
  return !!search?.trim();
}
