"use client";

import { useCallback, useMemo } from "react";
import type { SelectOption } from "@oba/shared/features/form";
import { BoardMemberRoleChip } from "@/features/boards/components/board-member-role-chip";
import { BOARD_TREE_PARAMS, useBoards } from "@/features/boards/hooks/use-boards";
import { flattenBoardsForSelect } from "@/features/boards/hooks/use-board-select";
import type { BoardMemberRole } from "@/features/boards/schemas";
import { AlertRouteBoardLabel } from "../components/alert-route-board-label";
import type { AlertRoute } from "../schemas";

/** A route needs MEMBER or above on its board; the server refuses a VIEWER. */
const READ_ONLY_ROLE: BoardMemberRole = "VIEWER";

interface BoardOptionMeta extends Record<string, unknown> {
  name: string;
  prefix: string;
  color: string | null;
  depth: number;
  readOnlyRole?: BoardMemberRole;
}

/**
 * The board picker of the alert map: the user's board tree, flattened so a
 * child board is listed under its parent. The option label joins name, prefix
 * and id, because the picker's search matches the label, so typing any of the
 * three finds the board; `renderOption` draws them apart.
 *
 * `currentRoute` keeps the board an existing mapping points to selectable when
 * it is missing from the user's tree (set by another admin); saving it then
 * fails on the server's membership check, which the error toast reports.
 */
export function useAlertRouteBoardOptions(currentRoute?: AlertRoute) {
  // The sidebar's cached board tree: same query key, no extra request.
  const { data: boardTreeResponse, isPending } = useBoards(BOARD_TREE_PARAMS);

  const options = useMemo((): SelectOption[] => {
    const boards = flattenBoardsForSelect(boardTreeResponse?.data ?? []);
    const result: SelectOption[] = boards.map((board) => {
      const isReadOnly = board.currentUserRole === READ_ONLY_ROLE;
      const meta: BoardOptionMeta = {
        name: board.name,
        prefix: board.prefix,
        color: board.color ?? null,
        depth: board.depth,
        readOnlyRole: isReadOnly ? READ_ONLY_ROLE : undefined,
      };
      return {
        value: board.id,
        label: optionLabel(board.name, board.prefix, board.id),
        disabled: isReadOnly,
        meta,
      };
    });

    if (currentRoute && !result.some((option) => option.value === currentRoute.boardId)) {
      const meta: BoardOptionMeta = {
        name: currentRoute.boardName,
        prefix: currentRoute.boardPrefix,
        color: currentRoute.boardColor,
        depth: 0,
      };
      result.unshift({
        value: currentRoute.boardId,
        label: optionLabel(currentRoute.boardName, currentRoute.boardPrefix, currentRoute.boardId),
        meta,
      });
    }
    return result;
  }, [boardTreeResponse?.data, currentRoute]);

  const renderOption = useCallback((option: SelectOption) => {
    const meta = option.meta as BoardOptionMeta | undefined;
    if (!meta) return option.label;
    return (
      <span
        className="flex w-full min-w-0 items-center gap-1.5"
        style={{ paddingLeft: meta.depth > 0 ? `${meta.depth * 16}px` : undefined }}
      >
        {meta.depth > 0 && <span className="text-muted-foreground text-xs">{"↳"}</span>}
        <AlertRouteBoardLabel
          name={meta.name}
          prefix={meta.prefix}
          color={meta.color}
          boardId={option.value}
          className="flex-1"
        />
        {meta.readOnlyRole && <BoardMemberRoleChip role={meta.readOnlyRole} size="sm" />}
      </span>
    );
  }, []);

  return { options, renderOption, isLoading: isPending };
}

function optionLabel(name: string, prefix: string, boardId: string): string {
  return `${name} · ${prefix} · ${boardId}`;
}
