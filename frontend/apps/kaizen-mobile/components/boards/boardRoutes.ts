import { boardRouteParams, type BoardRouteState } from "@/components/boards/boardModel";

/*
 * The app paths of the Boards tab, without React, so other tabs link into it
 * without importing the tab's hooks.
 */

/** The app path of a board screen with its view, sprint, search and filters. */
export function boardHref(boardId: string, state: Partial<BoardRouteState> = {}) {
  return {
    pathname: "/boards/board/[boardId]" as const,
    params: { boardId, ...boardRouteParams(state) },
  };
}

/**
 * The Filters sheet of a board. It carries the board's whole route state, so
 * the sheet counts tickets for the same view, sprint and search, and hands
 * the state back with only the filters changed.
 */
export function boardFiltersHref(boardId: string, state: BoardRouteState) {
  return {
    pathname: "/boards/board/[boardId]/filters" as const,
    params: { boardId, ...boardRouteParams(state) },
  };
}

/** The Board settings screen of a board. */
export function boardSettingsHref(boardId: string) {
  return { pathname: "/boards/board/[boardId]/settings" as const, params: { boardId } };
}

/** The two option lists of Board settings, labels and projects. */
export type BoardOptionListKind = "labels" | "projects";

/** The Labels or Projects screen of a board: this board's options and its related boards'. */
export function boardOptionsHref(boardId: string, kind: BoardOptionListKind) {
  return kind === "labels"
    ? { pathname: "/boards/board/[boardId]/labels" as const, params: { boardId } }
    : { pathname: "/boards/board/[boardId]/projects" as const, params: { boardId } };
}

/**
 * The Label or Project sheet of a board: empty for a new option, or filled
 * with the option `optionId` of the board's list, which names the board that
 * owns it.
 */
export function boardOptionFormHref(boardId: string, kind: BoardOptionListKind, optionId?: string) {
  if (kind === "labels") {
    return {
      pathname: "/boards/board/[boardId]/label-form" as const,
      params: optionId ? { boardId, labelId: optionId } : { boardId },
    };
  }
  return {
    pathname: "/boards/board/[boardId]/project-form" as const,
    params: optionId ? { boardId, projectId: optionId } : { boardId },
  };
}

/** The Milestones screen of a board. */
export function boardMilestonesHref(boardId: string) {
  return { pathname: "/boards/board/[boardId]/milestones" as const, params: { boardId } };
}

/** The Milestone sheet of a board: empty for a new milestone, or filled with `milestoneId`. */
export function milestoneFormHref(boardId: string, milestoneId?: string) {
  return {
    pathname: "/boards/board/[boardId]/milestone-form" as const,
    params: milestoneId ? { boardId, milestoneId } : { boardId },
  };
}

/** The Child boards screen of a board: every descendant, indented per level. */
export function childBoardsHref(boardId: string) {
  return { pathname: "/boards/board/[boardId]/child-boards" as const, params: { boardId } };
}

/** The Add existing boards sheet, which moves picked boards under the board. */
export function addChildBoardsHref(boardId: string) {
  return { pathname: "/boards/board/[boardId]/add-child-boards" as const, params: { boardId } };
}

/** The Members screen of a board: who is on it and with which role. */
export function boardMembersHref(boardId: string) {
  return { pathname: "/boards/board/[boardId]/members" as const, params: { boardId } };
}

/** The Add member sheet, which puts a user of the organization on the board. */
export function addBoardMemberHref(boardId: string) {
  return { pathname: "/boards/board/[boardId]/add-member" as const, params: { boardId } };
}

/** The Delete board sheet: what happens to the board's tickets and child boards. */
export function deleteBoardHref(boardId: string) {
  return { pathname: "/boards/board/[boardId]/delete-board" as const, params: { boardId } };
}

/** The Move selected tickets sheet of a board; the board screen hands it the selection first. */
export function bulkMoveHref(boardId: string) {
  return { pathname: "/boards/board/[boardId]/bulk-move" as const, params: { boardId } };
}

/** The Create sprint sheet of a board. */
export function createSprintHref(boardId: string) {
  return { pathname: "/boards/board/[boardId]/create-sprint" as const, params: { boardId } };
}

/** The Join a parent board's sprint sheet of a board without sprints. */
export function joinParentSprintHref(boardId: string) {
  return { pathname: "/boards/board/[boardId]/join-parent-sprint" as const, params: { boardId } };
}

/** The tab Edit sprint opens on: the name, goal and dates, the boards sharing the sprint, or the automation. */
export type EditSprintTab = "general" | "boards" | "automation";

/** The Edit sprint sheet of a sprint of the board, on `tab`. */
export function editSprintHref(boardId: string, sprintId: string, tab: EditSprintTab = "general") {
  return {
    pathname: "/boards/board/[boardId]/edit-sprint" as const,
    params: { boardId, sprintId, tab },
  };
}

/** The Complete sprint sheet of a sprint of the board. */
export function completeSprintHref(boardId: string, sprintId: string) {
  return {
    pathname: "/boards/board/[boardId]/complete-sprint" as const,
    params: { boardId, sprintId },
  };
}

/** The Rename sheet of a board, opened from the board's menu on the boards list. */
export function renameBoardHref(boardId: string) {
  return { pathname: "/boards/rename-board" as const, params: { boardId } };
}

/** The New board sheet of the Boards tab. */
export const NEW_BOARD_HREF = "/boards/new-board";

/** The New board sheet creating a child of `parentBoardId`, opened from its Child boards. */
export function newChildBoardHref(parentBoardId: string) {
  return { pathname: "/boards/new-board" as const, params: { parentBoardId } };
}
