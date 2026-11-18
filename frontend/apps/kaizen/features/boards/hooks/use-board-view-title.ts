"use client";

import { parseAsString, useQueryState } from "nuqs";
import { useDocumentTitle } from "@/hooks/use-document-title";
import { TICKET_ROUTE_PARAM } from "@/features/tickets/lib/ticket-route-params";
import { useCachedBoard } from "./use-boards";

/**
 * "{board} {label}", or the label alone when it already starts with the board
 * name as a whole word, ignoring case: a sprint named "Alpha Sprint 1" on board
 * "Alpha" would otherwise read "Alpha Alpha Sprint 1". A board "Alp" does not
 * count as present in "Alpha Sprint 1". Names are shown as stored.
 */
export function formatBoardViewTitle(boardName: string, label: string): string {
  const name = boardName.trim();
  const text = label.trim();
  if (!name) return text;

  const lowerName = name.toLocaleLowerCase();
  const lowerText = text.toLocaleLowerCase();
  if (lowerText.startsWith(lowerName)) {
    const next = lowerText.charAt(lowerName.length);
    if (next === "" || /\s/.test(next)) return text;
  }
  return `${name} ${text}`;
}

/**
 * Names the board in the tab of a board view, so tabs of several boards can
 * be told apart. The board comes from the cached board tree that the board
 * pages prefetch and the board tabs read, so no request is made; until it is
 * cached, the static view title from the route metadata stays.
 *
 * While the ticket dialog is open (`t_ticket`), the dialog owns the tab and
 * this hook holds no title. The tab shows the title of the last-ranked caller
 * that has one, so while the dialog has no title of its own, for example while
 * its ticket is still loading, the board title would flash in the tab over the
 * open dialog. Reading the same URL state as the dialog host makes the
 * hand-over happen in the commit that opens or closes the dialog.
 */
export function useBoardViewTitle(boardId: string, label: string): void {
  const { data: board } = useCachedBoard(boardId);
  const [ticketParam] = useQueryState(TICKET_ROUTE_PARAM.ticket, parseAsString);

  useDocumentTitle(board && !ticketParam ? formatBoardViewTitle(board.name, label) : null);
}
