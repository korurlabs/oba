import type { QueryClient } from "@tanstack/react-query";
import { notFound } from "next/navigation";
import { z } from "zod";
import { isApiError } from "@oba/shared/api/axios";
import { getPage, listPages, listRecentPages, listTrash } from "../api";
import { PAGE_KEYS } from "../hooks/query-keys";
import type { KalkanLocale } from "../schemas";
import { contentLocaleOf } from "./page-tree";

/** The refusal of a page the server does not show: deleted, never there or out of reach. */
const PAGE_NOT_FOUND = "KLK_NOT_001";

/** The status of a request the caller's organization role is too low for. */
const FORBIDDEN = 403;

/**
 * Answers HTTP 404 for a route segment that cannot be a page id. Pages are
 * addressed by their UUID, so any other segment names no page: it is refused
 * before it reaches the api routes, where the server would answer 400 and the
 * client would offer a retry that can never succeed.
 */
export function requirePageId(pageId: string): void {
  if (!z.guid().safeParse(pageId).success) {
    notFound();
  }
}

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
 * Server-side prefetch of the most recently changed pages, under the key and
 * with the api function `useRecentPages(limit)` uses. `uiLocale` is the locale
 * of the route; the list is titled in its content locale, as on the client.
 * Best-effort: on failure the error is logged and the client hook fetches on
 * mount.
 */
export async function prefetchRecentPages(
  queryClient: QueryClient,
  organizationId: string,
  limit: number,
  uiLocale: string,
  source: string
): Promise<void> {
  const locale = contentLocaleOf(uiLocale);
  try {
    queryClient.setQueryData(
      PAGE_KEYS.recent(organizationId, limit, locale),
      await listRecentPages(limit, locale)
    );
  } catch (error) {
    console.error(`[${source}] recent pages prefetch failed, the client will refetch:`, error);
  }
}

/**
 * Server-side prefetch of the caller's trash, under the key and with the api
 * function `useTrash` uses. The server decides who may read the trash: a
 * GUEST is refused with 403, and the trash view then shows its members-only
 * state without reading the cache, so that refusal leaves the cache empty
 * and is not logged. Any other failure is logged and the client hook fetches
 * on mount.
 */
export async function prefetchTrash(
  queryClient: QueryClient,
  organizationId: string,
  uiLocale: string,
  source: string
): Promise<void> {
  const locale = contentLocaleOf(uiLocale);
  try {
    queryClient.setQueryData(PAGE_KEYS.trash(organizationId, locale), await listTrash(locale));
  } catch (error) {
    if (isApiError(error) && error.status === FORBIDDEN) return;
    console.error(`[${source}] trash prefetch failed, the client will refetch:`, error);
  }
}

/**
 * Server-side prefetch of one page, under the key and with the api function
 * `usePage` uses, for the screens of one page. Resolves to false for a page
 * the server does not show, so the route answers HTTP 404, unless the page is
 * in the caller's trash: the page view then offers its restore. Any other
 * failure is best-effort: the error is logged, the result is true and the
 * client hook fetches on mount and shows its own state. `uiLocale` is the
 * locale of the route.
 */
export async function prefetchPage(
  queryClient: QueryClient,
  organizationId: string,
  pageId: string,
  uiLocale: string
): Promise<boolean> {
  try {
    queryClient.setQueryData(PAGE_KEYS.detail(organizationId, pageId), await getPage(pageId));
    return true;
  } catch (error) {
    if (!isApiError(error, PAGE_NOT_FOUND)) {
      console.error("[page-detail] page prefetch failed, the client will refetch:", error);
      return true;
    }
  }
  return isInTrash(queryClient, organizationId, pageId, contentLocaleOf(uiLocale));
}

/**
 * Whether the caller's trash holds the page, which it does only for the top
 * page of a deleted subtree the caller may restore. The trash is cached under
 * the key `useTrash` reads, so the page view's restore needs no request of its
 * own. A trash that cannot be read (a GUEST is refused it) counts as not
 * holding the page; the error is logged.
 */
async function isInTrash(
  queryClient: QueryClient,
  organizationId: string,
  pageId: string,
  locale: KalkanLocale
): Promise<boolean> {
  try {
    const trash = await listTrash(locale);
    queryClient.setQueryData(PAGE_KEYS.trash(organizationId, locale), trash);
    return (trash.data ?? []).some((item) => item.id === pageId);
  } catch (error) {
    console.error("[page-detail] trash lookup for a missing page failed:", error);
    return false;
  }
}
