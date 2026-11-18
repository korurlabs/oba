import type { UseQueryResult } from "@tanstack/react-query";
import type * as React from "react";
import type { RefreshControlProps } from "react-native";

import type { BoardRouteState } from "@/components/boards/boardModel";
import type {
  BoardSelection,
  SelectionGroup,
} from "@/components/boards/selection/useTicketSelection";
import type { BoardListItem, BoardTicket, Sprint } from "@/lib/schemas/boards";

/** What the board screen hands each of its views. */
export interface BoardViewProps {
  board: BoardListItem;
  state: BoardRouteState;
  onChange: (state: BoardRouteState) => void;
  sprints: UseQueryResult<Sprint[]>;
  /** The sprint the view shows: the chosen one on All and Sprint, the next planned one on Backlog. */
  sprint: Sprint | null;
  sprintTickets: UseQueryResult<BoardTicket[]>;
  backlogTickets: UseQueryResult<BoardTicket[]>;
  /** The board's backlog, the target of a move out of a sprint; null until it has loaded. */
  backlogId: string | null;
  /** The user may create and change tickets and sprints on this board. */
  canWrite: boolean;
  meId: string | undefined;
  /** Opens the Filters sheet; left out while the board has none. */
  onOpenFilters?: () => void;
  /**
   * Selection mode, null while it is off. While it is on the lists show
   * checkboxes instead of drags, swipes and menus, and hide their add actions.
   */
  selection: BoardSelection | null;
  /** Starts selection mode with the given tickets of `group` picked, from a held card's Select. */
  onStartSelection: (group: SelectionGroup, ids: readonly string[]) => void;
  /** A new pull-to-refresh control; Kanban columns each need their own. */
  renderRefreshControl: () => React.ReactElement<RefreshControlProps>;
}
