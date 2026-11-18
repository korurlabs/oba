"use client";

import { useMemo } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import type { ApiResponse } from "@oba/shared/schema";
import { useKazanOpen } from "@/components/layout/use-kazan-access";
import { ROADMAP_KEYS } from "@/features/milestones/hooks/query-keys";
import {
  getBoard,
  getBoardBySlug,
  listBoards,
  listRelatedBoards,
  createBoard,
  updateBoard,
  deleteBoard,
  addChildBoards,
  updateBoardParent,
} from "../api";
import { findBoardInTree, moveBoardInTree } from "../lib/board-tree";
import type {
  BoardCreateRequest,
  BoardDeleteRequest,
  BoardDetailResponse,
  BoardListWithChildrenResponse,
  BoardParentUpdateRequest,
  BoardUpdateRequest,
} from "../schemas";
import type { BoardFilterParams } from "./use-board-filter-params";
import { BOARD_KEYS, BOARD_TREE_PARAMS } from "./query-keys";

/**
 * Query keys live in the boundary-safe `./query-keys` module (no "use client")
 * so server components can prefetch under the exact same keys without the keys
 * becoming client-reference proxies. Re-exported here for client consumers.
 */
export { BOARD_KEYS, BOARD_TREE_PARAMS };

const KEYS = BOARD_KEYS;

/** 5 minutes -- prevents duplicate fetches when multiple components share the same query key */
const BOARDS_STALE_TIME = 5 * 60 * 1000;

/**
 * The board lists. The sidebar tree and the home page read them on every
 * visit, so these list queries, and the cached readers below that share the
 * tree's key, wait until Kazan is open in the selected organization.
 */
export function useBoards(params?: BoardFilterParams | Record<string, unknown>) {
  const kazanOpen = useKazanOpen();
  return useQuery({
    queryKey: KEYS.list((params ?? {}) as Record<string, unknown>),
    queryFn: () => listBoards(params as Record<string, unknown>),
    enabled: kazanOpen,
    staleTime: BOARDS_STALE_TIME,
  });
}

/**
 * Resolves the board a URL names by its slug. The board tree cache holds at
 * most `BOARD_TREE_PARAMS.amount` root boards, so a slug is resolved by the
 * server rather than looked up in that cache. A slug that matches no member
 * board fails with a 404 `ApiError`.
 */
export function useBoardBySlug(slug: string | null | undefined) {
  return useQuery({
    queryKey: KEYS.bySlug(slug ?? ""),
    queryFn: () => getBoardBySlug(slug as string),
    enabled: !!slug,
    staleTime: BOARDS_STALE_TIME,
  });
}

/** Reads one member board by id; a board that is gone or not shared fails with a 404 `ApiError`. */
export function useBoard(boardId: string | null | undefined) {
  return useQuery({
    queryKey: KEYS.detail(boardId ?? ""),
    queryFn: () => getBoard(boardId as string),
    enabled: !!boardId,
    staleTime: BOARDS_STALE_TIME,
  });
}

/**
 * Returns a single board's data by reading from the cached boards list.
 * Uses the same query key as the sidebar so it reads from cache without
 * triggering a refetch (Infinity staleTime + the sidebar already fetches).
 */
export function useCachedBoard(boardId: string) {
  const kazanOpen = useKazanOpen();
  const { data: boardsResponse, isLoading } = useQuery({
    queryKey: KEYS.list(BOARD_TREE_PARAMS as unknown as Record<string, unknown>),
    queryFn: () => listBoards(BOARD_TREE_PARAMS as unknown as Record<string, unknown>),
    enabled: kazanOpen,
    staleTime: BOARDS_STALE_TIME,
  });

  const board = useMemo(() => {
    function findBoard(boards: BoardListWithChildrenResponse[]): BoardListWithChildrenResponse | null {
      for (const b of boards) {
        if (b.id === boardId) return b;
        if (b.childBoards?.length) {
          const found = findBoard(b.childBoards as typeof boards);
          if (found) return found;
        }
      }
      return null;
    }
    return findBoard(boardsResponse?.data ?? []);
  }, [boardsResponse?.data, boardId]);

  return { data: board, isLoading };
}

/**
 * Returns child boards for a given boardId by reading from the cached
 * boards list. Same query key as the sidebar -- no extra API call.
 */
export function useCachedChildBoards(boardId: string) {
  const kazanOpen = useKazanOpen();
  const { data: boardsResponse, isLoading } = useQuery({
    queryKey: KEYS.list(BOARD_TREE_PARAMS as unknown as Record<string, unknown>),
    queryFn: () => listBoards(BOARD_TREE_PARAMS as unknown as Record<string, unknown>),
    enabled: kazanOpen,
    staleTime: BOARDS_STALE_TIME,
  });

  const childBoards = useMemo(() => {
    function findChildBoards(boards: BoardListWithChildrenResponse[]): BoardListWithChildrenResponse[] {
      for (const b of boards) {
        if (b.id === boardId) return b.childBoards ?? [];
        if (b.childBoards?.length) {
          const found = findChildBoards(b.childBoards as typeof boards);
          if (found.length) return found;
        }
      }
      return [];
    }
    return findChildBoards(boardsResponse?.data ?? []);
  }, [boardsResponse?.data, boardId]);

  return { data: childBoards, isLoading };
}

/** A board below another board in the tree, with its distance from that board (1 for a child). */
export interface DescendantBoard {
  id: string;
  name: string;
  prefix: string;
  color: string;
  depth: number;
}

/**
 * Returns every board below `boardId` at any depth, read from the cached
 * board tree, so no extra API call is made. A parent board's ticket views
 * span its whole subtree, so a filter over them must offer grandchildren too.
 * Boards come depth-first, each right after its parent, so an indented list
 * shows the hierarchy.
 */
export function useCachedDescendantBoards(boardId: string) {
  const kazanOpen = useKazanOpen();
  const { data: boardsResponse, isLoading } = useQuery({
    queryKey: KEYS.list(BOARD_TREE_PARAMS as unknown as Record<string, unknown>),
    queryFn: () => listBoards(BOARD_TREE_PARAMS as unknown as Record<string, unknown>),
    enabled: kazanOpen,
    staleTime: BOARDS_STALE_TIME,
  });

  const descendants = useMemo(() => {
    const root = findBoardInTree(boardsResponse?.data ?? [], boardId);
    const result: DescendantBoard[] = [];
    const visit = (boards: BoardListWithChildrenResponse[], depth: number) => {
      for (const b of boards) {
        result.push({ id: b.id, name: b.name, prefix: b.prefix, color: b.color ?? "", depth });
        visit((b.childBoards ?? []) as BoardListWithChildrenResponse[], depth + 1);
      }
    };
    if (root) visit((root.childBoards ?? []) as BoardListWithChildrenResponse[], 1);
    return result;
  }, [boardsResponse?.data, boardId]);

  return { data: descendants, isLoading };
}

export function useRelatedBoards(boardId: string) {
  return useQuery({
    queryKey: [...KEYS.all, "related", boardId],
    queryFn: () => listRelatedBoards(boardId),
    enabled: !!boardId,
    staleTime: BOARDS_STALE_TIME,
  });
}

export function useCreateBoard() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: (data: BoardCreateRequest) => createBoard(data),
    onSuccess: () => {
      toast.success(t("boards.messages.created"));
      queryClient.invalidateQueries({ queryKey: KEYS.all });
      queryClient.invalidateQueries({ queryKey: KEYS.list(BOARD_TREE_PARAMS as unknown as Record<string, unknown>) });
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useUpdateBoard() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: BoardUpdateRequest }) =>
      updateBoard(id, data),
    onSuccess: () => {
      toast.success(t("boards.messages.updated"));
      queryClient.invalidateQueries({ queryKey: KEYS.all });
      queryClient.invalidateQueries({ queryKey: KEYS.list(BOARD_TREE_PARAMS as unknown as Record<string, unknown>) });
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useDeleteBoard(options?: {
  /**
   * IDs of child/descendant boards that will also be deleted.
   * Their cached queries (sprints, tickets, backlog, members, related)
   * are removed so no 404 refetches occur.
   */
  childBoardIds?: string[];
  /**
   * Runs after cache removal but before list query invalidation.
   * Use this to close dialogs and navigate away so that unmounted
   * components don't trigger refetches for deleted resources.
   */
  onBeforeInvalidate?: () => void;
}) {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({ id, params }: { id: string; params?: BoardDeleteRequest }) =>
      deleteBoard(id, params),
    onSuccess: async (_, variables) => {
      toast.success(t("boards.messages.deleted"));

      // Collect all board IDs that were deleted (the board itself + descendants)
      const deletedBoardIds = [variables.id, ...(options?.childBoardIds ?? [])];

      // Remove all queries scoped to the deleted boards so they don't 404
      for (const boardId of deletedBoardIds) {
        // Board-specific queries
        queryClient.removeQueries({ queryKey: KEYS.detail(boardId) });
        queryClient.removeQueries({ queryKey: [...KEYS.all, "related", boardId] });
        // Board member queries
        queryClient.removeQueries({ queryKey: ["boardMembers", "list", boardId] });
        // Sprint queries scoped to this board
        queryClient.removeQueries({ queryKey: ["sprints", "list", boardId] });
        queryClient.removeQueries({ queryKey: ["sprints", "current", boardId] });
        queryClient.removeQueries({
          queryKey: ["sprints"],
          predicate: (query) => {
            const key = query.queryKey;
            return key[0] === "sprints" && key[1] === "detail" && key[2] === boardId;
          },
        });
        // Ticket queries scoped to this board
        queryClient.removeQueries({
          queryKey: ["tickets"],
          predicate: (query) => {
            const key = query.queryKey;
            // List: ["tickets", "list", boardId, ...]
            // Detail: ["tickets", "detail", boardId, ...]
            return (key[0] === "tickets" && (key[2] === boardId));
          },
        });
        // Backlog queries scoped to this board
        queryClient.removeQueries({ queryKey: ["backlog", "detail", boardId] });
      }

      // Close dialogs / navigate before invalidating remaining queries
      options?.onBeforeInvalidate?.();

      // Invalidate board list queries so the sidebar tree refreshes
      await queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useAddChildBoards() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({ boardId, childBoardIds }: { boardId: string; childBoardIds: string[] }) =>
      addChildBoards(boardId, childBoardIds),
    onSuccess: () => {
      toast.success(t("boards.messages.childrenAdded"));
      queryClient.invalidateQueries({ queryKey: KEYS.all });
      queryClient.invalidateQueries({ queryKey: KEYS.list(BOARD_TREE_PARAMS as unknown as Record<string, unknown>) });
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export interface MoveBoardTarget {
  id: string;
  name: string;
}

export interface MoveBoardVariables {
  board: MoveBoardTarget;
  /** The new parent; `null` moves the board to the top level. */
  newParent: MoveBoardTarget | null;
  /** The parent the board leaves, named in the "removed from" message. */
  previousParent?: MoveBoardTarget | null;
  /** Position of the board in the new parent's cached list; appends when left out. */
  index?: number;
  /**
   * Runs right after the board tree cache shows the move and returns its
   * undo, which runs when the server refuses the move. The sidebar passes
   * `placeBoard` here so that the saved order follows the tree.
   */
  applyOrder?: () => () => void;
  /** `removedFromParent` names `previousParent` in the success message. */
  successMessage?: "moved" | "removedFromParent";
}

type MoveBoardContext = {
  previous: ApiResponse<BoardListWithChildrenResponse[]> | undefined;
  undoOrder?: () => void;
};

/**
 * Changes a board's parent through the board update request. The board tree
 * shows the move at once and returns to its old state, with the user's saved
 * order, when the server refuses it; the server's message is shown, since it
 * names the reason (no rights on the new parent, a parent inside the board's
 * own subtree, a board that no longer exists).
 */
export function useMoveBoard() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");
  const treeKey = KEYS.list(BOARD_TREE_PARAMS as unknown as Record<string, unknown>);

  return useMutation<
    ApiResponse<BoardDetailResponse>,
    Error,
    MoveBoardVariables,
    MoveBoardContext
  >({
    mutationFn: ({ board, newParent }) => {
      const data: BoardParentUpdateRequest = newParent
        ? { parentBoardId: newParent.id }
        : { clearParent: true };
      return updateBoardParent(board.id, data);
    },
    onMutate: async (variables) => {
      await queryClient.cancelQueries({ queryKey: treeKey });
      const previous =
        queryClient.getQueryData<ApiResponse<BoardListWithChildrenResponse[]>>(treeKey);
      if (!previous?.data) return { previous };

      const moved = moveBoardInTree(
        previous.data,
        variables.board.id,
        variables.newParent?.id ?? null,
        variables.index ?? Number.MAX_SAFE_INTEGER,
      );
      // The tree helper returns the same array when it cannot place the board
      // in the cached tree; the server's answer then decides what is shown.
      if (moved === previous.data) return { previous };

      queryClient.setQueryData<ApiResponse<BoardListWithChildrenResponse[]>>(treeKey, {
        ...previous,
        data: moved,
      });
      // The saved order is reconciled against the cached tree, so it may only
      // change once the tree lists the board in its new place.
      const undoOrder = variables.applyOrder?.();
      return { previous, undoOrder };
    },
    onError: (error, variables, context) => {
      if (context?.previous) queryClient.setQueryData(treeKey, context.previous);
      context?.undoOrder?.();
      toast.error(t("boards.messages.moveError", { name: variables.board.name }), {
        description: error.message,
      });
    },
    onSuccess: (_, { board, newParent, previousParent, successMessage }) => {
      if (successMessage === "removedFromParent" && previousParent) {
        toast.success(
          t("boards.messages.removedFromParent", { name: board.name, parent: previousParent.name }),
        );
      } else if (newParent) {
        toast.success(t("boards.messages.moved", { name: board.name, parent: newParent.name }));
      } else {
        toast.success(t("boards.messages.movedToTopLevel", { name: board.name }));
      }
    },
    // Related boards and the roadmap's child lanes follow the hierarchy, so
    // both are refreshed from the server whatever the outcome.
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: KEYS.all }),
        queryClient.invalidateQueries({ queryKey: ROADMAP_KEYS.all }),
      ]),
  });
}
