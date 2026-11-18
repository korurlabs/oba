"use client";

/**
 * Where the full ticket page's Close button goes.
 *
 * The page cannot use `router.back()`: it may have been reached by a shared
 * link pasted into an existing tab, where history counts the whole tab
 * session and backing out lands on someone else's site. So the list the
 * user left is remembered when the app itself leaves it for a full page (the
 * dialog's expand button, sub-ticket navigation), and Close returns there.
 *
 * It lives in `sessionStorage`, not in the url: the ticket url stays
 * readable, the value survives the expand's hard navigation and a refresh,
 * and it is per tab, so a full page opened in another tab (where the user
 * left no list) falls back to the ticket's board.
 *
 * Storage can be unavailable (Safari private mode, blocked site data). Then
 * nothing is remembered and Close falls back to the ticket's board, which
 * is still a working way out.
 */

import { safeReturnPath } from "./ticket-route-params";

const STORAGE_KEY = "kazan:ticket-return-path";

/** Remembers `path` (an app-relative `path?query`) as the list to return to. */
export function saveTicketReturnPath(path: string): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.setItem(STORAGE_KEY, path);
  } catch (error) {
    // eslint-disable-next-line no-empty -- reason: Close then returns to the ticket's board instead of the list, which the user can still reach from the sidebar
    console.warn("[ticket-return-path] could not remember the list:", error);
  }
}

/** The remembered list, or `null` when there is none or it is not an app path. */
export function readTicketReturnPath(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return safeReturnPath(window.sessionStorage.getItem(STORAGE_KEY));
  } catch (error) {
    // eslint-disable-next-line no-empty -- reason: with no readable list, Close falls back to the ticket's board, which is still a working way out
    console.warn("[ticket-return-path] could not read the list:", error);
    return null;
  }
}

/**
 * Forgets the remembered list once Close has used it, so a full page reached
 * later by a plain link does not return to a list the user left long ago.
 */
export function clearTicketReturnPath(): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.removeItem(STORAGE_KEY);
  } catch (error) {
    // eslint-disable-next-line no-empty -- reason: a value left behind only changes where a later Close goes, and it is still validated as an app path when read
    console.warn("[ticket-return-path] could not forget the list:", error);
  }
}
