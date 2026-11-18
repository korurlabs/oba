import type { BoardView } from "@/components/boards/boardModel";

/*
 * The pure rules of the board views' create actions. No React, so `bun test`
 * covers them.
 */

/**
 * Where the nav bar's New ticket adds the ticket on a board view: the sprint
 * id on Sprint, null (the backlog) on All and Backlog, and undefined where
 * the nav bar has no New ticket. Sprint offers it only with a sprint to add
 * to, and not where its floating button already does (Android). Roadmap
 * shows milestones, not tickets.
 */
export function navBarNewTicket(
  view: BoardView,
  sprintId: string | null,
  sprintHasFab: boolean
): string | null | undefined {
  switch (view) {
    case "sprint":
      return sprintId && !sprintHasFab ? sprintId : undefined;
    case "all":
    case "backlog":
      return null;
    case "roadmap":
      return undefined;
  }
}
