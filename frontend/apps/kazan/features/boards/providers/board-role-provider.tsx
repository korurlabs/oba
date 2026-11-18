"use client";

import { createContext, useContext } from "react";
import { useBoardRole } from "../hooks/use-board-role";
import type { BoardMemberRole } from "../schemas";

interface BoardRoleContextValue {
  currentUserRole: BoardMemberRole | undefined;
  isOwner: boolean;
  canManage: boolean;
  canEdit: boolean;
  isLoading: boolean;
}

const BoardRoleContext = createContext<BoardRoleContextValue | null>(null);

export function BoardRoleProvider({
  boardId,
  children,
}: {
  boardId: string;
  children: React.ReactNode;
}) {
  const role = useBoardRole(boardId);
  return (
    <BoardRoleContext.Provider value={role}>
      {children}
    </BoardRoleContext.Provider>
  );
}

export function useBoardRoleContext(): BoardRoleContextValue {
  const ctx = useContext(BoardRoleContext);
  if (!ctx) {
    throw new Error("useBoardRoleContext must be used inside BoardRoleProvider");
  }
  return ctx;
}

/**
 * Returns the BoardRoleContext value if inside a BoardRoleProvider,
 * or `null` when rendered outside the provider (e.g. all-tickets page).
 */
export function useOptionalBoardRoleContext(): BoardRoleContextValue | null {
  return useContext(BoardRoleContext);
}
