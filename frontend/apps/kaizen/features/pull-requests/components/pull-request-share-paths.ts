/**
 * Routes of the pull request share screens.
 *
 * Not marked "use client": the chat header and the share card link here, and
 * a server module may import them too; inside a "use client" module these
 * would become client-reference proxies there.
 */

import { PULL_REQUESTS_PATH } from "@/features/chat/lib/chat-route";

/** Route under which every pull request share screen sits: the pull requests channel. */
const PULL_REQUEST_SHARE_ROUTE_PREFIX = PULL_REQUESTS_PATH;

/** The screen that shares a pull request in the PR channel. */
export const PULL_REQUEST_SHARE_CREATE_PATH = `${PULL_REQUEST_SHARE_ROUTE_PREFIX}/new` as const;

/** The author's edit screen of one share. */
export function pullRequestShareEditPath(shareId: string): string {
  return `${PULL_REQUEST_SHARE_ROUTE_PREFIX}/${encodeURIComponent(shareId)}/edit`;
}
