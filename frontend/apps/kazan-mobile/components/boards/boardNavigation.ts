import { useRouter } from "expo-router";
import { useCallback } from "react";

import type { BoardRouteState } from "@/components/boards/boardModel";
import {
  boardFiltersHref,
  boardHref,
  boardSettingsHref,
  completeSprintHref,
  createSprintHref,
  editSprintHref,
  NEW_BOARD_HREF,
  renameBoardHref,
  type EditSprintTab,
} from "@/components/boards/boardRoutes";
import { useHomeLinks } from "@/components/home/homeNavigation";
import { boardMoveToSprintHref } from "@/components/sprints/moveToSprintRoutes";
import { newTicketHref } from "@/components/ticket/ticketRoutes";
import { useTranslation } from "@/i18n";
import { kazanWebUrl } from "@/lib/browser";

/*
 * Where the Boards tab leads. The boards list, the board views, Filters,
 * Board settings with its labels, milestones, projects, child boards and
 * members, New board, New ticket, the sprint sheets and the ticket screen are
 * native. Only the board's web pages (the board itself, from the board menu,
 * and a roadmap milestone) and the organization's alert routes open Kazan
 * web in the in-app browser, signed in with the same Korur session; each has
 * one function here, so a native screen can replace it in one place.
 */

/** The Kazan web pages of a board that the tab opens. */
export type BoardWebPage = "board" | "sprint" | "backlog" | "roadmap";

/** The Kazan web path of a board page, in the app language. */
export function boardWebPagePath(language: string, boardSlug: string, page: BoardWebPage): string {
  const base: string = `/${language}/board/${encodeURIComponent(boardSlug)}`;
  return page === "board" ? base : `${base}/${page}`;
}

/** The Kazan web page of the organization's alert routes. */
export function alertRoutesWebPath(language: string): string {
  return `/${language}/settings/alerts`;
}

/** Opens the screens and web pages of the Boards tab. */
export function useBoardLinks() {
  const router = useRouter();
  const { language } = useTranslation();
  const { open, openTicket } = useHomeLinks();

  const openBoard = useCallback(
    (boardId: string, state: Partial<BoardRouteState> = {}) =>
      router.push(boardHref(boardId, state)),
    [router]
  );

  const openBoardSettings = useCallback(
    (boardId: string) => router.push(boardSettingsHref(boardId)),
    [router]
  );

  const openRenameBoard = useCallback(
    (boardId: string) => router.push(renameBoardHref(boardId)),
    [router]
  );

  const openFilters = useCallback(
    (boardId: string, state: BoardRouteState) => router.push(boardFiltersHref(boardId, state)),
    [router]
  );

  const openWebBoardPage = useCallback(
    (boardSlug: string, page: BoardWebPage) =>
      open(kazanWebUrl(boardWebPagePath(language, boardSlug, page))),
    [language, open]
  );

  /** New ticket on the board, added to `sprintId` when given, else to the backlog. */
  const openNewTicket = useCallback(
    (boardId: string, sprintId?: string | null) =>
      router.push(newTicketHref({ boardId, sprintId })),
    [router]
  );

  const openNewBoard = useCallback(() => router.push(NEW_BOARD_HREF), [router]);

  const openCreateSprint = useCallback(
    (boardId: string) => router.push(createSprintHref(boardId)),
    [router]
  );

  const openEditSprint = useCallback(
    (boardId: string, sprintId: string, tab?: EditSprintTab) =>
      router.push(editSprintHref(boardId, sprintId, tab)),
    [router]
  );

  const openCompleteSprint = useCallback(
    (boardId: string, sprintId: string) => router.push(completeSprintHref(boardId, sprintId)),
    [router]
  );

  /** Move to sprint for a ticket of the board, over the board view. */
  const openMoveToSprint = useCallback(
    (boardId: string, ticketKey: string) => router.push(boardMoveToSprintHref(boardId, ticketKey)),
    [router]
  );

  const openWebAlertRoutes = useCallback(
    () => open(kazanWebUrl(alertRoutesWebPath(language))),
    [language, open]
  );

  return {
    openBoard,
    openBoardSettings,
    openRenameBoard,
    openFilters,
    openTicket,
    openNewTicket,
    openNewBoard,
    openCreateSprint,
    openEditSprint,
    openCompleteSprint,
    openMoveToSprint,
    openWebBoardPage,
    openWebAlertRoutes,
  };
}
