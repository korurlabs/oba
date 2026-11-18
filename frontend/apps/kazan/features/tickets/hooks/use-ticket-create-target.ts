"use client";

/**
 * useTicketCreateTarget
 *
 * A create url names its target the way people read it: the board's slug,
 * the sprint's slug on that board, a backlog flag and the parent's key. The
 * form submits ids, so this hook resolves each named part to its id:
 *
 *   board    by slug (`GET /kazan/boards/by-slug/{slug}`)
 *   sprint   by slug in the board's sprint list
 *   backlog  the board's one backlog
 *   parent   by key (`GET /kazan/tickets/by-key/{key}`)
 *
 * A UUID in place of a slug or key comes from a link made before slugs and
 * keys existed and is taken as the id itself, so old create links still
 * work. Its record is still looked up, to learn the slug or key the url
 * should show (`urlRewrite`). That lookup only improves the url: when it
 * fails, the id stands and the form opens as it would have anyway. Every
 * lookup reuses the query the list pages already cache.
 */

import { useMemo } from "react";
import { isApiError } from "@oba/shared/api/axios";
import { isUuid } from "@/features/boards/lib/board-route";
import { useBoard, useBoardBySlug } from "@/features/boards/hooks/use-boards";
import { useSprints } from "@/features/sprints/hooks/use-sprints";
import { useBacklog } from "@/features/backlog/hooks/use-backlog";
import { useTicketByKey } from "@/features/all-tickets/hooks/use-ticket-by-key";
import type { TicketStatus } from "@/types/enums";
import {
  TICKET_BACKLOG_PLACEMENT,
  TICKET_ROUTE_PARAM,
  type TicketCreateTarget,
} from "../lib/ticket-route-params";
import { useTicket } from "./use-tickets";

/** The create target as the form takes it: ids, plus the slug and key for the expand url. */
export interface ResolvedTicketCreateTarget {
  /** Empty string when the url names no board: the form then offers the full board tree. */
  boardId: string;
  boardSlug: string | null;
  sprintId?: string;
  backlogId?: string;
  parentTicketId: string | null;
  parentTicketKey: string | null;
  initialStatus?: TicketStatus;
}

/** The create params the url should change, by param name. */
export type TicketCreateUrlRewrite = Partial<
  Record<
    | typeof TICKET_ROUTE_PARAM.board
    | typeof TICKET_ROUTE_PARAM.sprint
    | typeof TICKET_ROUTE_PARAM.parent
    | typeof TICKET_ROUTE_PARAM.backlog,
    string
  >
>;

export type TicketCreateTargetState =
  | { status: "loading" }
  | {
      status: "ready";
      target: ResolvedTicketCreateTarget;
      /** Non-null while the url still holds a legacy id with a known slug or key. */
      urlRewrite: TicketCreateUrlRewrite | null;
    }
  /** The named parent ticket does not exist or is not accessible. */
  | { status: "parentNotFound"; parentKey: string }
  /**
   * The named board, sprint or backlog does not exist or is not accessible:
   * the board answered 404, the sprint slug is not on the board, or the
   * board has no backlog. `reason` says which, for the log.
   */
  | { status: "targetNotFound"; reason: string }
  /**
   * A lookup failed before it returned anything. A failed background
   * refetch keeps the target it already resolved, so an open form is never
   * pulled away.
   */
  | { status: "failed"; error: unknown };

export function useTicketCreateTarget(target: TicketCreateTarget): TicketCreateTargetState {
  const { boardSlug: boardValue, sprintSlug: sprintValue, backlog, parentKey: parentValue } =
    target;

  const boardIsId = !!boardValue && isUuid(boardValue);
  const boardQuery = useBoardBySlug(boardValue && !boardIsId ? boardValue : null);
  const legacyBoardQuery = useBoard(boardIsId ? boardValue : null);
  const resolvedBoard = (boardIsId ? legacyBoardQuery : boardQuery).data?.data ?? null;
  const boardId = boardIsId ? (boardValue ?? "") : (resolvedBoard?.id ?? "");

  const sprintIsId = !!sprintValue && isUuid(sprintValue);
  const sprintsQuery = useSprints(boardId, { enabled: !!sprintValue });

  const wantsBacklog = backlog && !sprintValue;
  const backlogQuery = useBacklog(wantsBacklog ? boardId : "");

  const parentIsId = !!parentValue && isUuid(parentValue);
  const parentQuery = useTicketByKey(parentValue && !parentIsId ? parentValue : null);
  // An old parent link names no board of its own, so the parent is looked up
  // on the url's board. One on another board keeps its id in the url.
  const legacyParentQuery = useTicket(parentIsId ? boardId : "", parentIsId ? (parentValue ?? "") : "");

  return useMemo((): TicketCreateTargetState => {
    const urlRewrite: TicketCreateUrlRewrite = {};

    // Board first: sprint and backlog are looked up on it.
    if (boardIsId) {
      if (legacyBoardQuery.isLoading) return { status: "loading" };
      if (resolvedBoard?.slug) urlRewrite[TICKET_ROUTE_PARAM.board] = resolvedBoard.slug;
    } else if (boardValue) {
      if (!resolvedBoard) {
        if (boardQuery.isError) {
          // The backend answers 404 alike for an unknown board and for one
          // the user may not see.
          if (isApiError(boardQuery.error) && boardQuery.error.status === 404) {
            return { status: "targetNotFound", reason: `No board "${boardValue}"` };
          }
          return { status: "failed", error: boardQuery.error };
        }
        return { status: "loading" };
      }
    }

    let sprintId: string | undefined;
    if (sprintValue) {
      if (sprintIsId) {
        sprintId = sprintValue;
        if (sprintsQuery.isLoading) return { status: "loading" };
        const sprint = sprintsQuery.data?.data?.find((s) => s.id === sprintValue);
        if (sprint?.slug) urlRewrite[TICKET_ROUTE_PARAM.sprint] = sprint.slug;
      } else {
        if (!boardId) return { status: "targetNotFound", reason: "Sprint named without a board" };
        const sprints = sprintsQuery.data?.data;
        if (!sprints) {
          if (sprintsQuery.isError) return { status: "failed", error: sprintsQuery.error };
          return { status: "loading" };
        }
        const sprint = sprints.find((s) => s.slug === sprintValue);
        if (!sprint) {
          return { status: "targetNotFound", reason: `No sprint "${sprintValue}" on the board` };
        }
        sprintId = sprint.id;
      }
    }

    let backlogId: string | undefined;
    if (wantsBacklog) {
      if (!boardId) return { status: "targetNotFound", reason: "Backlog named without a board" };
      if (!backlogQuery.data) {
        if (backlogQuery.isError) return { status: "failed", error: backlogQuery.error };
        return { status: "loading" };
      }
      backlogId = backlogQuery.data.data?.id;
      if (!backlogId) return { status: "targetNotFound", reason: "The board has no backlog" };
    }

    let parentTicketId: string | null = null;
    let parentTicketKey: string | null = null;
    if (parentValue) {
      if (parentIsId) {
        parentTicketId = parentValue;
        if (legacyParentQuery.isLoading) return { status: "loading" };
        parentTicketKey = legacyParentQuery.data?.data?.key ?? null;
        if (parentTicketKey) urlRewrite[TICKET_ROUTE_PARAM.parent] = parentTicketKey;
      } else {
        if (parentQuery.data === undefined) {
          if (parentQuery.isError) return { status: "failed", error: parentQuery.error };
          return { status: "loading" };
        }
        if (parentQuery.data === null) return { status: "parentNotFound", parentKey: parentValue };
        parentTicketId = parentQuery.data.id;
        parentTicketKey = parentQuery.data.key;
      }
    }

    const hasUrlRewrite = Object.keys(urlRewrite).length > 0;
    // An old link carried the backlog id here; the flag holds the same meaning.
    if (hasUrlRewrite && wantsBacklog) {
      urlRewrite[TICKET_ROUTE_PARAM.backlog] = TICKET_BACKLOG_PLACEMENT;
    }

    return {
      status: "ready",
      urlRewrite: hasUrlRewrite ? urlRewrite : null,
      target: {
        boardId,
        boardSlug: resolvedBoard?.slug ?? null,
        sprintId,
        backlogId,
        parentTicketId,
        parentTicketKey,
        initialStatus: target.initialStatus ?? undefined,
      },
    };
  }, [
    boardValue,
    boardIsId,
    boardQuery.isError,
    boardQuery.error,
    legacyBoardQuery.isLoading,
    resolvedBoard,
    boardId,
    sprintValue,
    sprintIsId,
    sprintsQuery.isLoading,
    sprintsQuery.isError,
    sprintsQuery.error,
    sprintsQuery.data?.data,
    wantsBacklog,
    backlogQuery.isError,
    backlogQuery.error,
    backlogQuery.data,
    parentValue,
    parentIsId,
    parentQuery.isError,
    parentQuery.error,
    parentQuery.data,
    legacyParentQuery.isLoading,
    legacyParentQuery.data?.data?.key,
    target.initialStatus,
  ]);
}
