"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { indexBoardTree } from "@/features/boards/lib/board-tree";
import type { BoardListWithChildrenResponse } from "@/features/boards/schemas";

const EXPANDED_BOARDS_STORAGE_KEY = "kazan-sidebar-expanded-boards";

const NO_IDS: ReadonlySet<string> = new Set<string>();

function readStoredIds(): string[] {
  try {
    const stored = window.localStorage.getItem(EXPANDED_BOARDS_STORAGE_KEY);
    if (stored === null) return [];
    const parsed: unknown = JSON.parse(stored);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((id): id is string => typeof id === "string");
    // eslint-disable-next-line no-empty -- reason: expanded branches are a per-viewer convenience; the tree falls back to collapsed and keeps working
  } catch {}
  return [];
}

function writeStoredIds(ids: string[]): void {
  try {
    window.localStorage.setItem(EXPANDED_BOARDS_STORAGE_KEY, JSON.stringify(ids));
    // eslint-disable-next-line no-empty -- reason: expanded branches are a per-viewer convenience; the tree falls back to collapsed and keeps working
  } catch {}
}

/**
 * Which branches of the sidebar board tree are open, remembered in this
 * browser only. Storage is read after mount, because the server render has
 * no access to it, and ids opened before that read are kept. Ids of boards
 * that left the tree are dropped when the set is written, but only once the
 * tree has loaded, so a still-empty board list never wipes the stored set.
 */
export function useSidebarExpandedBoards(boards: BoardListWithChildrenResponse[]): {
  expandedIds: ReadonlySet<string>;
  isExpanded: (id: string) => boolean;
  toggle: (id: string) => void;
  expandMany: (ids: string[]) => void;
} {
  const [expandedIds, setExpandedIds] = useState<ReadonlySet<string>>(NO_IDS);
  const [isHydrated, setIsHydrated] = useState(false);

  const boardIndex = useMemo(() => indexBoardTree(boards), [boards]);

  useEffect(() => {
    const storedIds = readStoredIds();
    setExpandedIds((prev) => (storedIds.length === 0 ? prev : new Set([...prev, ...storedIds])));
    setIsHydrated(true);
  }, []);

  useEffect(() => {
    if (!isHydrated) return;
    const ids = [...expandedIds];
    writeStoredIds(boardIndex.size === 0 ? ids : ids.filter((id) => boardIndex.has(id)));
  }, [expandedIds, boardIndex, isHydrated]);

  const isExpanded = useCallback((id: string) => expandedIds.has(id), [expandedIds]);

  const toggle = useCallback((id: string) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const expandMany = useCallback((ids: string[]) => {
    setExpandedIds((prev) => {
      if (ids.every((id) => prev.has(id))) return prev;
      return new Set([...prev, ...ids]);
    });
  }, []);

  return { expandedIds, isExpanded, toggle, expandMany };
}
