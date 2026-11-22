import type { QueryClient } from "@tanstack/react-query";
import { getPage, listPages } from "../api";
import { PAGE_KEYS } from "../hooks/query-keys";

/**
 * Server-side prefetch of the page tree, under the key and with the api
 * function `usePageTree` uses. The sidebar reads the tree on every page, so
 * the shell's layouts prefetch it. Best-effort: on failure the error is
 * logged and the client hook fetches on mount.
 */
export async function prefetchPageTree(
  queryClient: QueryClient,
  organizationId: string,
  source: string
): Promise<void> {
  try {
    queryClient.setQueryData(PAGE_KEYS.tree(organizationId), await listPages());
  } catch (error) {
    console.error(`[${source}] page tree prefetch failed, the client will refetch:`, error);
  }
}

/**
 * Server-side prefetch of one page, under the key and with the api function
 * `usePage` uses. Best-effort: on failure, a 404 included, the error is
 * logged and the client hook fetches on mount and shows its own state.
 */
export async function prefetchPage(
  queryClient: QueryClient,
  organizationId: string,
  pageId: string
): Promise<void> {
  try {
    queryClient.setQueryData(PAGE_KEYS.detail(organizationId, pageId), await getPage(pageId));
  } catch (error) {
    console.error("[page-detail] page prefetch failed, the client will refetch:", error);
  }
}
