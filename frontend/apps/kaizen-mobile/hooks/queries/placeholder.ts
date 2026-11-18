import type { QueryKey } from "@tanstack/react-query";

/** The part of a previous query a placeholder reads: its key. */
type PreviousQuery = { queryKey: QueryKey };

function startsWith(key: QueryKey, scope: QueryKey): boolean {
  return scope.length <= key.length && scope.every((part, index) => key[index] === part);
}

/**
 * A `placeholderData` that keeps the previous query's data on screen while the
 * next key loads (a new filter, search or period), but only when the previous
 * key lies inside `scope`. Pass the organization's root key, such as
 * `homeKeys.all(orgId)`, so that after an organization switch the screen shows
 * its loading state instead of the other organization's data.
 */
export function keepPreviousWithin(
  scope: QueryKey
): <TData>(
  previous: TData | undefined,
  previousQuery: PreviousQuery | undefined
) => TData | undefined {
  return <TData>(previous: TData | undefined, previousQuery: PreviousQuery | undefined) =>
    previousQuery && startsWith(previousQuery.queryKey, scope) ? previous : undefined;
}
