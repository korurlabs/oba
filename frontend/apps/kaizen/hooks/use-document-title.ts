"use client";

/**
 * useDocumentTitle
 *
 * Shows `formatPageTitle(title)` in the browser tab while the calling
 * component is mounted, for titles that come from client data (a ticket
 * loaded from the query cache) and so cannot come from server metadata.
 * The title follows every change of `title`, so an edit, a rollback or a
 * language switch shows in the tab without a reload. `null` leaves the
 * server-rendered title alone.
 *
 * The tab has one title, so callers do not write it themselves. Every
 * mounted caller holds a claim in one shared registry, ranked by the order in
 * which the callers first rendered, and the tab shows the title of the
 * last-ranked caller whose title is not `null`. A dialog opened over a page
 * renders after the page, so the dialog's title wins and the page's title
 * comes back when the dialog closes. When two callers each re-asserted their
 * own title, every write woke the other's watcher; the watchers run as
 * microtasks, so the render that would have ended the overlap never came,
 * and the endless title writes took the browser down.
 *
 * Route metadata can overwrite the tab after a title is shown: Next streams a
 * route's `<title>` and may commit it after the page's effects, and a language
 * switch re-commits the page title in the new language. While any caller holds
 * the tab, one shared watcher keeps that incoming title as the one to give
 * back later and shows the owner's title again. It writes nothing when the tab
 * already shows the owner's title, so its own write cannot wake it again.
 *
 * When the last caller with a title leaves, the remembered title comes back,
 * but only when the tab still shows the value set here: if the next route's
 * metadata has already replaced it, that newer title wins and stays.
 *
 * `useDocumentTitleCount` puts a count in front of the title, "(4) Create
 * Ticket | Korur", on top of whichever title owns the tab. While a count is
 * shown and no caller holds a title, the route's metadata title is the owner:
 * the shared watcher takes each incoming metadata title as the new one to
 * give back and shows it with the count. The count is written by the same
 * writer, so the tab still has one writer and one watcher, and it changes
 * only when the count or the owner's title changes.
 */

import { useLayoutEffect, useState } from "react";
import { formatPageTitle } from "@/lib/page-title";

interface TitleClaim {
  rank: number;
  title: string | null;
}

/** Mounted callers, ordered by rank; the last one with a title owns the tab. */
const claims: TitleClaim[] = [];
let lastRank = 0;
/** "(4) " while a count is shown in front of the title, otherwise empty. */
let countPrefix = "";
let watcher: MutationObserver | null = null;
/** The title last written to the tab, as assigned. */
let shownSource: string | null = null;
/**
 * The tab as read back after that write: the browser strips and collapses
 * whitespace, so a ticket title with a double space never equals the string
 * assigned, and comparing with that string would rewrite the tab endlessly.
 */
let shownTitle: string | null = null;
/**
 * The title the tab had before it was held here, or route metadata since,
 * without the count: given back when the tab is released, and shown with the
 * count while no caller holds a title.
 */
let givenBackTitle: string | null = null;

function findOwner(): TitleClaim | null {
  for (let index = claims.length - 1; index >= 0; index -= 1) {
    const claim = claims[index];
    if (claim && claim.title !== null) return claim;
  }
  return null;
}

/** The watcher's callback: only a title that is not the one written here wakes it. */
function reassertTitle(): void {
  if (shownTitle === null || document.title === shownTitle) return;
  syncTab();
}

function releaseTab(): void {
  if (watcher === null) return;
  // `disconnect()` also drops records still queued from this commit, so the
  // title given back below is never taken for an overwrite.
  watcher.disconnect();
  watcher = null;
  if (givenBackTitle !== null && document.title === shownTitle) {
    document.title = givenBackTitle;
  }
  shownSource = null;
  shownTitle = null;
  givenBackTitle = null;
}

/** Brings the tab in line with the current owner and count; writes only on a change. */
function syncTab(): void {
  const owner = findOwner();
  if ((owner === null || owner.title === null) && countPrefix === "") {
    releaseTab();
    return;
  }

  if (watcher === null) {
    givenBackTitle = document.title;
    watcher = new MutationObserver(reassertTitle);
    // The whole document, not only `<head>`: streamed metadata is rendered in
    // a hidden element in `<body>`, and its `<title>` may stay there.
    watcher.observe(document.documentElement, {
      childList: true,
      subtree: true,
      characterData: true,
    });
  } else if (document.title !== shownTitle) {
    // Route metadata replaced the tab since the last write, possibly in this
    // same commit, before the watcher saw it: that is now the title to give back.
    givenBackTitle = document.title;
  }

  const ownerTitle =
    owner !== null && owner.title !== null ? formatPageTitle(owner.title) : (givenBackTitle ?? "");
  const wanted = `${countPrefix}${ownerTitle}`;
  if (wanted === shownSource && document.title === shownTitle) return;
  if (document.title !== wanted) {
    document.title = wanted;
  }
  shownSource = wanted;
  shownTitle = document.title;
}

export function useDocumentTitle(title: string | null): void {
  // Ranked at first render, not in an effect: effects run children first, so
  // a dialog inside a page would otherwise rank below the page that hosts it.
  const [claim] = useState<TitleClaim>(() => {
    lastRank += 1;
    return { rank: lastRank, title: null };
  });

  // Layout effects, so a caller joins and leaves inside the commit that
  // mounts or unmounts it: the hand-over between two callers then happens in
  // one commit, and a `<title>` the next route commits in that same commit is
  // never taken for an overwrite.
  useLayoutEffect(() => {
    claims.push(claim);
    claims.sort((left, right) => left.rank - right.rank);
    return () => {
      const index = claims.indexOf(claim);
      if (index !== -1) claims.splice(index, 1);
      syncTab();
    };
  }, [claim]);

  useLayoutEffect(() => {
    claim.title = title;
    syncTab();
  }, [claim, title]);
}

/**
 * Shows `shownCount` in front of the tab title, "(4) Create Ticket | Korur",
 * on every page while the calling component is mounted; `null` shows none.
 * Pass the count as displayed elsewhere ("99+"), so the tab and the badge
 * agree. Mount one caller at a time: the count is not ranked like titles.
 */
export function useDocumentTitleCount(shownCount: string | null): void {
  useLayoutEffect(() => {
    countPrefix = shownCount === null ? "" : `(${shownCount}) `;
    syncTab();
  }, [shownCount]);

  // Cleared only on unmount: a cleanup on every change would show the bare
  // title for a moment between two counts, one extra write each time.
  useLayoutEffect(
    () => () => {
      countPrefix = "";
      syncTab();
    },
    []
  );
}
