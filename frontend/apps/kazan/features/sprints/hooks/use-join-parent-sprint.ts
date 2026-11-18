"use client";

import { useMemo } from "react";
import { useMutation, useQueries, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useBoards, BOARD_TREE_PARAMS } from "@/features/boards/hooks/use-boards";
import { useManageableBoardIds } from "@/features/boards/hooks/use-board-role";
import type { BoardListWithChildrenResponse } from "@/features/boards/schemas";
import { invalidateRelatedQueries } from "@/hooks/invalidate-related-queries";
import { linkSprint, listSprints } from "../api";
import type { SprintListResponse } from "../schemas";
import { SPRINT_KEYS } from "./query-keys";
import { sprintErrorMessage } from "./use-sprints";

/** A board above the current one, with the sprint it is running now. */
export interface ParentSprintOption {
  board: BoardListWithChildrenResponse;
  /** 1 for the parent, 2 for the grandparent, and so on. */
  distance: number;
  /** The board's ACTIVE sprint; a board runs at most one at a time. */
  activeSprint: SprintListResponse | null;
  /**
   * The boards the sprint is linked to on join, top-down, ending with the
   * current board. A sprint can only be linked to a parent, child or sibling
   * of a board that already has it, so a grandparent's sprint reaches the
   * current board through each board in between that does not have it yet.
   */
  linkPath: BoardListWithChildrenResponse[];
  /** The first board of `linkPath` the user may not link a sprint to (ADMIN or OWNER needed). */
  boardWithoutRole: BoardListWithChildrenResponse | null;
}

/** The boards from a root down to `boardId`, both included, or null when it is not in the tree. */
function findPath(
  boards: readonly BoardListWithChildrenResponse[],
  boardId: string
): BoardListWithChildrenResponse[] | null {
  for (const board of boards) {
    if (board.id === boardId) return [board];
    const below = findPath((board.childBoards ?? []) as BoardListWithChildrenResponse[], boardId);
    if (below) return [board, ...below];
  }
  return null;
}

/**
 * The ancestors of a board, nearest first, each with its active sprint, for
 * the dialog that lets a board join a parent board's sprint. Reads the board
 * tree the sidebar caches and each ancestor's sprint list.
 */
export function useParentSprintOptions(boardId: string, options?: { enabled?: boolean }) {
  const enabled = options?.enabled ?? true;
  const { data: treeResponse, isLoading: isTreeLoading } = useBoards(BOARD_TREE_PARAMS);

  const path = useMemo(() => {
    const tree = (treeResponse?.data ?? []) as BoardListWithChildrenResponse[];
    return findPath(tree, boardId) ?? [];
  }, [treeResponse?.data, boardId]);
  // Root first; the current board is the last entry.
  const ancestors = useMemo(() => path.slice(0, -1), [path]);
  // The board list carries no role, so it is read from each board's members.
  const pathIds = useMemo(() => path.map((board) => board.id), [path]);
  const manageableBoardIds = useManageableBoardIds(pathIds);
  const manageableKey = pathIds.filter((id) => manageableBoardIds.has(id)).join(",");

  const sprintQueries = useQueries({
    queries: ancestors.map((ancestor) => ({
      queryKey: SPRINT_KEYS.list(ancestor.id),
      queryFn: () => listSprints(ancestor.id),
      enabled,
    })),
  });

  const activeSprintIds = sprintQueries.map(
    (query) => query.data?.data?.find((sprint) => sprint.status === "ACTIVE")?.id ?? null
  );
  const activeSprintKey = activeSprintIds.join(",");

  const parentOptions = useMemo((): ParentSprintOption[] => {
    const result: ParentSprintOption[] = [];
    ancestors.forEach((ancestor, index) => {
      const activeSprint =
        sprintQueries[index]?.data?.data?.find((sprint) => sprint.status === "ACTIVE") ?? null;
      // The boards below the ancestor, down to the current board, that do not
      // run this sprint yet.
      const linkPath = path
        .slice(index + 1)
        .filter((_board, offset) => activeSprintIds[index + 1 + offset] !== activeSprint?.id);
      result.push({
        board: ancestor,
        distance: ancestors.length - index,
        activeSprint,
        linkPath,
        boardWithoutRole: linkPath.find((board) => !manageableBoardIds.has(board.id)) ?? null,
      });
    });
    return result.reverse();
    // The keys stand for the query results, which are a new array every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ancestors, path, activeSprintKey, manageableKey]);

  const isLoading = isTreeLoading || sprintQueries.some((query) => query.isLoading);
  const hasJoinableSprint = parentOptions.some(
    (option) => option.activeSprint && !option.boardWithoutRole
  );

  return { parentOptions, isLoading, hasJoinableSprint };
}

/**
 * Links a parent board's sprint to every board of the option's `linkPath`,
 * top-down, so each link is to a child of a board that already has the
 * sprint. The links are separate requests: when one is refused, the boards
 * linked before it keep the sprint, and the error toast names them.
 */
export function useJoinParentSprint() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: async ({
      sprintId,
      linkPath,
    }: {
      sprintId: string;
      linkPath: BoardListWithChildrenResponse[];
    }) => {
      const linked: string[] = [];
      for (const board of linkPath) {
        try {
          await linkSprint(board.id, sprintId);
        } catch (error) {
          throw Object.assign(error instanceof Error ? error : new Error(String(error)), {
            linkedBoardNames: linked,
          });
        }
        linked.push(board.name);
      }
    },
    onSuccess: () => {
      toast.success(t("sprints.messages.joinedParentSprint"));
    },
    onError: (error: Error & { linkedBoardNames?: string[] }) => {
      const linked = error.linkedBoardNames ?? [];
      toast.error(sprintErrorMessage(error, t), {
        description:
          linked.length > 0
            ? t("sprints.messages.joinPartiallyLinked", { boards: linked.join(", ") })
            : undefined,
      });
    },
    onSettled: () => {
      void invalidateRelatedQueries(queryClient);
    },
  });
}
