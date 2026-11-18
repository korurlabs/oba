"use client";

import { useState, useCallback, useEffect, useRef, useMemo } from "react";
import { arrayMove } from "@dnd-kit/sortable";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { useUpdateMe } from "@oba/shared/features/users/hooks/use-update-me";
import { findParentId, indexBoardTree } from "@/features/boards/lib/board-tree";
import type {
  BoardListWithChildrenResponse,
  BoardListResponse,
} from "@/features/boards/schemas";

// ── Types ────────────────────────────────────────────────────────────

/** Stored at userPreferences.sidebar */
interface SidebarPreferences {
  /** Ordered array of UNPINNED parent board IDs only */
  boardOrder: string[];
  /** Array of pinned board IDs */
  pinnedBoardIds: string[];
  /** Child board ordering per parent board — UNPINNED children only */
  childBoardOrder: Record<string, string[]>;
  /** Pinned child board IDs per parent board */
  pinnedChildBoardIds: Record<string, string[]>;
  /** Unified display order of ALL pinned items (parent + child board IDs) */
  pinnedDisplayOrder?: string[];
}

/** A pinned board item for the flat pinned section at the top */
export interface PinnedBoardItem {
  id: string;
  name: string;
  slug: string;
  prefix: string;
  color?: string | null;
  icon?: string | null;
  /** If this is a child board, the parent's ID */
  parentBoardId?: string;
  /** Whether this board has child boards, so its pinned row can expand in place */
  hasChildren: boolean;
}

// ── Reconciliation (pure functions) ──────────────────────────────────

/**
 * Reconcile board order against API boards.
 * `savedOrder` should only contain UNPINNED parent board IDs.
 * Only non-pinned API boards are considered.
 */
export function reconcileBoardOrder(
  apiBoards: BoardListWithChildrenResponse[],
  savedOrder: string[],
  savedPinned: string[],
): {
  orderedBoards: BoardListWithChildrenResponse[];
  pinnedIds: string[];
  cleanOrder: string[];
  isDirty: boolean;
} {
  const boardMap = new Map(apiBoards.map((b) => [b.id, b]));
  const pinnedSet = new Set(savedPinned.filter((id) => boardMap.has(id)));

  // Filter saved order to only IDs that exist in API and are NOT pinned,
  // deduplicating to prevent multiplied boards
  const seenIds = new Set<string>();
  const validOrder = savedOrder.filter((id) => {
    if (seenIds.has(id) || !boardMap.has(id) || pinnedSet.has(id)) return false;
    seenIds.add(id);
    return true;
  });

  // Find unpinned boards not in saved order
  const savedSet = new Set(validOrder);
  const newBoardIds = apiBoards
    .filter((b) => !savedSet.has(b.id) && !pinnedSet.has(b.id))
    .map((b) => b.id);

  // Build final order: existing valid order + new boards appended
  const finalOrder = [...validOrder, ...newBoardIds];

  const isDirty =
    validOrder.length !== savedOrder.length ||
    pinnedSet.size !== savedPinned.length ||
    newBoardIds.length > 0;

  // orderedBoards = ALL boards in order (pinned first, then unpinned in order)
  // This is used for drill-down navigation where we need all boards
  const pinnedBoards = apiBoards.filter((b) => pinnedSet.has(b.id));
  const unpinnedBoards = finalOrder
    .map((id) => boardMap.get(id))
    .filter((b): b is BoardListWithChildrenResponse => b !== undefined);
  const orderedBoards = [...pinnedBoards, ...unpinnedBoards];

  return {
    orderedBoards,
    pinnedIds: [...pinnedSet],
    cleanOrder: finalOrder,
    isDirty,
  };
}

/**
 * Reconcile child board order against API children.
 * Only non-pinned children are considered.
 */
export function reconcileChildBoardOrder(
  apiChildren: BoardListResponse[],
  savedOrder: string[],
  pinnedChildIds?: string[],
): { orderedChildren: BoardListResponse[]; cleanOrder: string[]; isDirty: boolean } {
  const childMap = new Map(apiChildren.map((c) => [c.id, c]));
  const pinnedSet = new Set(pinnedChildIds ?? []);

  // Filter saved order to only IDs that exist in API and are NOT pinned,
  // deduplicating to prevent multiplied children
  const seenIds = new Set<string>();
  const validOrder = savedOrder.filter((id) => {
    if (seenIds.has(id) || !childMap.has(id) || pinnedSet.has(id)) return false;
    seenIds.add(id);
    return true;
  });
  const savedSet = new Set(validOrder);
  const newChildIds = apiChildren
    .filter((c) => !savedSet.has(c.id) && !pinnedSet.has(c.id))
    .map((c) => c.id);

  const finalOrder = [...validOrder, ...newChildIds];
  const isDirty = validOrder.length !== savedOrder.length || newChildIds.length > 0;

  const orderedChildren = finalOrder
    .map((id) => childMap.get(id))
    .filter((c): c is BoardListResponse => c !== undefined);

  return { orderedChildren, cleanOrder: finalOrder, isDirty };
}

/** A board placed in a new list whose move the board tree does not show yet. */
interface PendingPlacement {
  boardId: string;
  /** Parent of the list the board was placed in; `null` for the top level. */
  parentId: string | null;
}

// ── Hook ──────────────────────────────────────────────────────────────

export function useSidebarBoardPreferences(
  apiBoards: BoardListWithChildrenResponse[],
) {
  const t = useTranslations("preferences");
  const { data: meResponse, isFetched: isMeFetched } = useGetMe();
  const updateMeMutation = useUpdateMe();

  const userPreferences = meResponse?.data?.userPreferences;

  // Extract sidebar preferences from userPreferences
  const savedPrefs = useMemo((): SidebarPreferences | null => {
    if (!userPreferences) return null;
    const sidebarPrefs = userPreferences["sidebar"] as SidebarPreferences | undefined;
    if (!sidebarPrefs) return null;
    return sidebarPrefs;
  }, [userPreferences]);

  // ── Local state ─────────────────────────────────────────────────────
  /** Only unpinned parent board IDs */
  const [boardOrder, setBoardOrder] = useState<string[]>([]);
  const [pinnedBoardIds, setPinnedBoardIds] = useState<string[]>([]);
  /** Only unpinned child board IDs per parent */
  const [childBoardOrder, setChildBoardOrder] = useState<Record<string, string[]>>({});
  const [pinnedChildBoardIds, setPinnedChildBoardIds] = useState<Record<string, string[]>>({});
  /** ALL pinned board IDs (parent + child) in display order */
  const [pinnedDisplayOrder, setPinnedDisplayOrder] = useState<string[]>([]);
  const [isInitialized, setIsInitialized] = useState(false);

  // Refs to avoid stale closures in debounced saves
  const boardOrderRef = useRef(boardOrder);
  const pinnedBoardIdsRef = useRef(pinnedBoardIds);
  const childBoardOrderRef = useRef(childBoardOrder);
  const pinnedChildBoardIdsRef = useRef(pinnedChildBoardIds);
  const pinnedDisplayOrderRef = useRef(pinnedDisplayOrder);

  useEffect(() => { boardOrderRef.current = boardOrder; }, [boardOrder]);
  useEffect(() => { pinnedBoardIdsRef.current = pinnedBoardIds; }, [pinnedBoardIds]);
  useEffect(() => { childBoardOrderRef.current = childBoardOrder; }, [childBoardOrder]);
  useEffect(() => { pinnedChildBoardIdsRef.current = pinnedChildBoardIds; }, [pinnedChildBoardIds]);
  useEffect(() => { pinnedDisplayOrderRef.current = pinnedDisplayOrder; }, [pinnedDisplayOrder]);

  // Initialize from saved prefs when user data loads
  useEffect(() => {
    if (!isMeFetched) return;

    setBoardOrder(savedPrefs?.boardOrder ?? []);
    setPinnedBoardIds(savedPrefs?.pinnedBoardIds ?? []);
    setChildBoardOrder(savedPrefs?.childBoardOrder ?? {});
    setPinnedChildBoardIds(savedPrefs?.pinnedChildBoardIds ?? {});
    setPinnedDisplayOrder(savedPrefs?.pinnedDisplayOrder ?? []);
    setIsInitialized(true);
  }, [isMeFetched]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Reconciled boards ───────────────────────────────────────────────
  const { orderedBoards, pinnedIds, isDirty } = useMemo(() => {
    if (!isInitialized || apiBoards.length === 0) {
      return {
        orderedBoards: apiBoards,
        pinnedIds: [] as string[],
        isDirty: false,
      };
    }
    return reconcileBoardOrder(apiBoards, boardOrder, pinnedBoardIds);
  }, [apiBoards, boardOrder, pinnedBoardIds, isInitialized]);

  const pinnedIdSet = useMemo(() => new Set(pinnedIds), [pinnedIds]);

  // Every board at any depth, because pinned children and child order are
  // keyed by a parent that can sit anywhere in the tree.
  const boardIndex = useMemo(() => indexBoardTree(apiBoards), [apiBoards]);

  // Set while a placed board's list differs from where the board tree still
  // shows it. Reconciling in that window would drop the board from the list
  // it was placed in, because the tree does not list it there yet.
  const pendingPlacementRef = useRef<PendingPlacement | null>(null);

  // ── Save helpers ────────────────────────────────────────────────────

  const saveTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  const saveSidebarPreferences = useCallback(
    async (prefs: SidebarPreferences) => {
      const currentPrefs = (userPreferences ?? {}) as Record<string, unknown>;

      const newPrefs = {
        ...currentPrefs,
        sidebar: prefs,
      };

      await updateMeMutation.mutateAsync({
        userPreferences: newPrefs,
      });
    },
    [userPreferences, updateMeMutation],
  );

  const debouncedSave = useCallback(
    (
      order: string[],
      pinned: string[],
      childOrder: Record<string, string[]>,
      pinnedChildren?: Record<string, string[]>,
      displayOrder?: string[],
    ) => {
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
      saveTimerRef.current = setTimeout(async () => {
        try {
          await saveSidebarPreferences({
            boardOrder: order,
            pinnedBoardIds: pinned,
            childBoardOrder: childOrder,
            pinnedChildBoardIds: pinnedChildren ?? pinnedChildBoardIdsRef.current,
            pinnedDisplayOrder: displayOrder ?? pinnedDisplayOrderRef.current,
          });
        } catch (error) {
          // The new order stays on screen, so without the toast the user would
          // take it for saved and lose it on the next page load.
          console.error("[useSidebarBoardPreferences] sidebar order save failed:", error);
          toast.error(t("sidebarSaveError"));
        }
      }, 2000);
    },
    [saveSidebarPreferences, t],
  );

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    };
  }, []);

  // Auto-save when reconciliation detects stale entries
  useEffect(() => {
    const pending = pendingPlacementRef.current;
    if (pending) {
      const settled =
        boardIndex.has(pending.boardId) &&
        findParentId(apiBoards, pending.boardId) === pending.parentId;
      if (!settled) return;
      pendingPlacementRef.current = null;
    }
    if (isDirty && isInitialized && apiBoards.length > 0) {
      // Reconcile parent board order (unpinned only)
      const { cleanOrder, pinnedIds: cleanPinned } = reconcileBoardOrder(
        apiBoards,
        boardOrder,
        pinnedBoardIds,
      );

      // Clean child board order (unpinned children only) and pinned child board IDs
      const cleanChildOrder: Record<string, string[]> = {};
      const cleanPinnedChildren: Record<string, string[]> = {};

      for (const [parentId, pinned] of Object.entries(pinnedChildBoardIds)) {
        const parent = boardIndex.get(parentId);
        if (parent?.childBoards) {
          const childIdSet = new Set((parent.childBoards as BoardListResponse[]).map((c) => c.id));
          const validPinned = pinned.filter((id) => childIdSet.has(id));
          if (validPinned.length > 0) {
            cleanPinnedChildren[parentId] = validPinned;
          }
        }
      }

      for (const [parentId, order] of Object.entries(childBoardOrder)) {
        const parent = boardIndex.get(parentId);
        if (parent?.childBoards) {
          const pinnedForParent = cleanPinnedChildren[parentId] ?? [];
          const { cleanOrder: cleanChildren } = reconcileChildBoardOrder(
            parent.childBoards as BoardListResponse[],
            order,
            pinnedForParent,
          );
          if (cleanChildren.length > 0) {
            cleanChildOrder[parentId] = cleanChildren;
          }
        }
      }

      // Clean pinnedDisplayOrder: keep only IDs that are still pinned (parent or child)
      const allValidPinnedIds = new Set([
        ...cleanPinned,
        ...Object.values(cleanPinnedChildren).flat(),
      ]);
      const cleanDisplayOrder = pinnedDisplayOrder.filter((id) => allValidPinnedIds.has(id));
      // Append any pinned IDs not yet in display order
      const displaySet = new Set(cleanDisplayOrder);
      const missingPinned = [...allValidPinnedIds].filter((id) => !displaySet.has(id));
      const finalDisplayOrder = [...cleanDisplayOrder, ...missingPinned];

      setBoardOrder(cleanOrder);
      setPinnedBoardIds(cleanPinned);
      setChildBoardOrder(cleanChildOrder);
      setPinnedChildBoardIds(cleanPinnedChildren);
      setPinnedDisplayOrder(finalDisplayOrder);
      debouncedSave(cleanOrder, cleanPinned, cleanChildOrder, cleanPinnedChildren, finalDisplayOrder);
    }
  }, [isDirty, isInitialized, apiBoards]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Actions ─────────────────────────────────────────────────────────

  /** Reorder unpinned parent boards */
  const reorderBoards = useCallback(
    (activeId: string, overId: string) => {
      if (activeId === overId) return;

      setBoardOrder((prev) => {
        // Reconcile with current API boards — only unpinned
        const { cleanOrder } = reconcileBoardOrder(apiBoards, prev, pinnedBoardIdsRef.current);
        const oldIndex = cleanOrder.indexOf(activeId);
        const newIndex = cleanOrder.indexOf(overId);
        if (oldIndex === -1 || newIndex === -1) return prev;

        const newOrder = arrayMove(cleanOrder, oldIndex, newIndex);
        debouncedSave(newOrder, pinnedBoardIdsRef.current, childBoardOrderRef.current);
        return newOrder;
      });
    },
    [apiBoards, debouncedSave],
  );

  /** Reorder unpinned children of a parent */
  const reorderChildBoards = useCallback(
    (parentId: string, activeId: string, overId: string) => {
      if (activeId === overId) return;

      setChildBoardOrder((prev) => {
        const parentBoard = boardIndex.get(parentId);
        const apiChildren = (parentBoard?.childBoards ?? []) as BoardListResponse[];
        const currentOrder = prev[parentId] ?? [];
        const pinnedForParent = pinnedChildBoardIdsRef.current[parentId] ?? [];

        const { cleanOrder } = reconcileChildBoardOrder(apiChildren, currentOrder, pinnedForParent);
        const oldIndex = cleanOrder.indexOf(activeId);
        const newIndex = cleanOrder.indexOf(overId);
        if (oldIndex === -1 || newIndex === -1) return prev;

        const newOrder = arrayMove(cleanOrder, oldIndex, newIndex);
        const updated = { ...prev, [parentId]: newOrder };

        debouncedSave(boardOrderRef.current, pinnedBoardIdsRef.current, updated);
        return updated;
      });
    },
    [boardIndex, debouncedSave],
  );

  /**
   * Moves a board from the list of `fromParentId` to position `index` of the
   * list of `toParentId` (`null` is the top level) in the saved order, and
   * returns a function that restores both lists. With the same parent on
   * both sides it reorders within that list.
   *
   * Call it after the board tree shows the move: reconciliation waits for the
   * tree to list the board under `toParentId` (and, after the undo, under
   * `fromParentId` again) before it prunes ids the tree does not hold.
   */
  const placeBoard = useCallback(
    (
      boardId: string,
      fromParentId: string | null,
      toParentId: string | null,
      index: number,
    ): (() => void) => {
      const previousOrder = boardOrderRef.current;
      const previousChildOrder = childBoardOrderRef.current;
      const pinned = pinnedBoardIdsRef.current;
      const pinnedChildren = pinnedChildBoardIdsRef.current;

      const listOrder = (parentId: string | null): string[] => {
        if (parentId === null) {
          return reconcileBoardOrder(apiBoards, previousOrder, pinned).cleanOrder;
        }
        const apiChildren = (boardIndex.get(parentId)?.childBoards ?? []) as BoardListResponse[];
        return reconcileChildBoardOrder(
          apiChildren,
          previousChildOrder[parentId] ?? [],
          pinnedChildren[parentId] ?? [],
        ).cleanOrder;
      };

      const target = listOrder(toParentId).filter((id) => id !== boardId);
      const at = Math.max(0, Math.min(index, target.length));
      target.splice(at, 0, boardId);

      let nextOrder = previousOrder;
      let nextChildOrder = previousChildOrder;
      const setList = (parentId: string | null, list: string[]) => {
        if (parentId === null) nextOrder = list;
        else nextChildOrder = { ...nextChildOrder, [parentId]: list };
      };
      if (fromParentId !== toParentId) {
        setList(fromParentId, listOrder(fromParentId).filter((id) => id !== boardId));
      }
      setList(toParentId, target);

      const apply = (
        order: string[],
        childOrder: Record<string, string[]>,
        settlesUnder: string | null,
      ) => {
        pendingPlacementRef.current =
          fromParentId === toParentId ? null : { boardId, parentId: settlesUnder };
        boardOrderRef.current = order;
        childBoardOrderRef.current = childOrder;
        setBoardOrder(order);
        setChildBoardOrder(childOrder);
        debouncedSave(order, pinned, childOrder);
      };

      apply(nextOrder, nextChildOrder, toParentId);
      return () => apply(previousOrder, previousChildOrder, fromParentId);
    },
    [apiBoards, boardIndex, debouncedSave],
  );

  /** Toggle pin for a parent board */
  const togglePin = useCallback(
    (boardId: string) => {
      const isPinned = pinnedBoardIds.includes(boardId);

      if (isPinned) {
        // Unpin: remove from pinned lists, add back to boardOrder at the end
        const newPinned = pinnedBoardIds.filter((id) => id !== boardId);
        setPinnedBoardIds(newPinned);

        const newDisplayOrder = pinnedDisplayOrderRef.current.filter((id) => id !== boardId);
        setPinnedDisplayOrder(newDisplayOrder);

        setBoardOrder((prev) => {
          // Add the unpinned board at the end of the unpinned order (only if not already present)
          const newOrder = prev.includes(boardId) ? prev : [...prev, boardId];
          debouncedSave(newOrder, newPinned, childBoardOrderRef.current, undefined, newDisplayOrder);
          return newOrder;
        });
      } else {
        // Pin: remove from boardOrder, add to pinned and display order
        const newPinned = [...pinnedBoardIds, boardId];
        setPinnedBoardIds(newPinned);

        const newDisplayOrder = [...pinnedDisplayOrderRef.current, boardId];
        setPinnedDisplayOrder(newDisplayOrder);

        setBoardOrder((prev) => {
          const newOrder = prev.filter((id) => id !== boardId);
          debouncedSave(newOrder, newPinned, childBoardOrderRef.current, undefined, newDisplayOrder);
          return newOrder;
        });
      }
    },
    [pinnedBoardIds, debouncedSave],
  );

  /** Toggle pin for a child board */
  const toggleChildPin = useCallback(
    (parentId: string, childBoardId: string) => {
      setPinnedChildBoardIds((prev) => {
        const currentPinned = prev[parentId] ?? [];
        const isPinned = currentPinned.includes(childBoardId);

        let newPinned: string[];
        if (isPinned) {
          newPinned = currentPinned.filter((id) => id !== childBoardId);
        } else {
          newPinned = [...currentPinned, childBoardId];
        }

        const updatedPinnedChildren = { ...prev };
        if (newPinned.length > 0) {
          updatedPinnedChildren[parentId] = newPinned;
        } else {
          delete updatedPinnedChildren[parentId];
        }

        if (isPinned) {
          // Unpin: remove from display order, add back to childBoardOrder at end
          const newDisplayOrder = pinnedDisplayOrderRef.current.filter((id) => id !== childBoardId);
          setPinnedDisplayOrder(newDisplayOrder);

          setChildBoardOrder((prevOrder) => {
            const currentOrder = prevOrder[parentId] ?? [];
            // Add the unpinned child at the end (only if not already present)
            const newOrder = currentOrder.includes(childBoardId)
              ? currentOrder
              : [...currentOrder, childBoardId];
            const updatedOrder = { ...prevOrder, [parentId]: newOrder };
            debouncedSave(boardOrderRef.current, pinnedBoardIdsRef.current, updatedOrder, updatedPinnedChildren, newDisplayOrder);
            return updatedOrder;
          });
        } else {
          // Pin: remove from childBoardOrder, add to display order
          const newDisplayOrder = [...pinnedDisplayOrderRef.current, childBoardId];
          setPinnedDisplayOrder(newDisplayOrder);

          setChildBoardOrder((prevOrder) => {
            const currentOrder = prevOrder[parentId] ?? [];
            // Remove the pinned child from unpinned order
            const newOrder = currentOrder.filter((id) => id !== childBoardId);
            const updatedOrder = { ...prevOrder, [parentId]: newOrder };
            debouncedSave(boardOrderRef.current, pinnedBoardIdsRef.current, updatedOrder, updatedPinnedChildren, newDisplayOrder);
            return updatedOrder;
          });
        }

        return updatedPinnedChildren;
      });
    },
    [debouncedSave],
  );

  const isChildPinned = useCallback(
    (parentId: string, childBoardId: string): boolean => {
      return (pinnedChildBoardIds[parentId] ?? []).includes(childBoardId);
    },
    [pinnedChildBoardIds],
  );

  // ── Computed: all pinned boards as flat list ──────────────────────
  const allPinnedBoards = useMemo((): PinnedBoardItem[] => {
    if (!isInitialized || apiBoards.length === 0) return [];

    // Build a set of board IDs that have children by scanning the full tree.
    // This lets us determine hasChildren for boards at any nesting level,
    // even when the child entry itself doesn't carry a childBoards array.
    const parentIds = new Set<string>();
    function collectParentIds(boards: BoardListWithChildrenResponse[]) {
      for (const b of boards) {
        if (b.childBoards && b.childBoards.length > 0) {
          parentIds.add(b.id);
          collectParentIds(b.childBoards as BoardListWithChildrenResponse[]);
        }
      }
    }
    collectParentIds(apiBoards);

    // Build a map of all pinned item IDs to their PinnedBoardItem
    const pinnedItemMap = new Map<string, PinnedBoardItem>();

    // Add pinned parent boards
    for (const id of pinnedIds) {
      const board = boardIndex.get(id);
      if (board) {
        pinnedItemMap.set(id, {
          id: board.id,
          name: board.name,
          slug: board.slug,
          prefix: board.prefix,
          color: board.color,
          icon: board.icon,
          hasChildren: (board.childBoards?.length ?? 0) > 0,
        });
      }
    }

    // Add pinned child boards
    for (const [parentId, childIds] of Object.entries(pinnedChildBoardIds)) {
      const parent = boardIndex.get(parentId);
      if (!parent?.childBoards) continue;
      for (const childId of childIds) {
        const child = parent.childBoards.find((c) => c.id === childId);
        if (child) {
          pinnedItemMap.set(childId, {
            id: child.id,
            name: child.name,
            slug: child.slug,
            prefix: child.prefix,
            color: child.color,
            icon: child.icon,
            parentBoardId: parentId,
            hasChildren: parentIds.has(childId),
          });
        }
      }
    }

    if (pinnedItemMap.size === 0) return [];

    // Use pinnedDisplayOrder if available, fall back to pinned parents then children
    if (pinnedDisplayOrder.length > 0) {
      const validOrder = pinnedDisplayOrder.filter((id) => pinnedItemMap.has(id));
      const orderedSet = new Set(validOrder);
      // Append any pinned items not in the display order (newly pinned)
      const remaining = [...pinnedItemMap.keys()].filter((id) => !orderedSet.has(id));
      return [...validOrder, ...remaining]
        .map((id) => pinnedItemMap.get(id))
        .filter((item): item is PinnedBoardItem => item !== undefined);
    }

    // Default order: pinned parents first (in boardOrder), then pinned children
    return [...pinnedItemMap.values()];
  }, [isInitialized, apiBoards, boardIndex, pinnedIds, pinnedChildBoardIds, pinnedDisplayOrder]);

  // ── Computed: unpinned root boards ────────────────────────────────
  const unpinnedRootBoards = useMemo((): BoardListWithChildrenResponse[] => {
    return orderedBoards.filter((b) => !pinnedIdSet.has(b.id));
  }, [orderedBoards, pinnedIdSet]);

  // ── Get unpinned children for a parent (excludes pinned children) ─
  const getUnpinnedChildren = useCallback(
    (parentId: string, apiChildren: BoardListResponse[]): BoardListResponse[] => {
      const pinned = pinnedChildBoardIds[parentId] ?? [];
      const pinnedSet = new Set(pinned);
      const savedOrder = childBoardOrder[parentId];

      if (!savedOrder || savedOrder.length === 0) {
        return apiChildren.filter((c) => !pinnedSet.has(c.id));
      }

      // reconcileChildBoardOrder already excludes pinned children
      const { orderedChildren } = reconcileChildBoardOrder(apiChildren, savedOrder, pinned);
      return orderedChildren;
    },
    [childBoardOrder, pinnedChildBoardIds],
  );

  // ── Reorder pinned boards (within pinned section) ─────────────────
  const reorderPinnedBoards = useCallback(
    (activeId: string, overId: string) => {
      if (activeId === overId) return;

      setPinnedDisplayOrder((prev) => {
        // Build valid order from current pinned items
        const allPinnedIds = allPinnedBoards.map((b) => b.id);
        let currentOrder: string[];
        if (prev.length > 0) {
          const validOrder = prev.filter((id) => allPinnedIds.includes(id));
          const remaining = allPinnedIds.filter((id) => !validOrder.includes(id));
          currentOrder = [...validOrder, ...remaining];
        } else {
          currentOrder = allPinnedIds;
        }

        const oldIndex = currentOrder.indexOf(activeId);
        const newIndex = currentOrder.indexOf(overId);
        if (oldIndex === -1 || newIndex === -1) return prev;

        const newOrder = arrayMove(currentOrder, oldIndex, newIndex);
        debouncedSave(
          boardOrderRef.current,
          pinnedBoardIdsRef.current,
          childBoardOrderRef.current,
          pinnedChildBoardIdsRef.current,
          newOrder,
        );
        return newOrder;
      });
    },
    [allPinnedBoards, debouncedSave],
  );

  return {
    orderedBoards,
    pinnedBoardIds: pinnedIdSet,
    pinnedChildBoardIds,
    allPinnedBoards,
    unpinnedRootBoards,
    isReady: isInitialized && isMeFetched,
    isSaving: updateMeMutation.isPending,
    reorderBoards,
    reorderChildBoards,
    reorderPinnedBoards,
    placeBoard,
    getUnpinnedChildren,
    togglePin,
    toggleChildPin,
    isChildPinned,
  };
}
