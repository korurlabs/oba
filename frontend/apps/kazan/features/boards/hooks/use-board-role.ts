"use client";

import { useMemo } from "react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { useBoardMembers, useMultipleBoardMembers } from "./use-board-members";
import type { BoardMemberRole } from "../schemas";

const MANAGE_ROLES: BoardMemberRole[] = ["OWNER", "ADMIN"];
const EDIT_ROLES: BoardMemberRole[] = ["OWNER", "ADMIN", "MEMBER"];

/**
 * Returns the current user's role on a specific board by fetching
 * the board's member list and matching against the logged-in user.
 *
 * Also provides:
 * - `canManage` boolean (true for OWNER or ADMIN)
 * - `canEdit` boolean (true for OWNER, ADMIN, or MEMBER — i.e. not VIEWER)
 */
export function useBoardRole(boardId: string) {
  const { data: user } = useGetMe();
  const { data: membersResponse, isLoading } = useBoardMembers(boardId);

  const currentUserRole = useMemo(() => {
    const userId = user?.data?.id;
    if (!userId || !membersResponse?.data) return undefined;
    return membersResponse.data.find((m) => m.userId === userId)?.role;
  }, [user?.data?.id, membersResponse?.data]);

  const isOwner = currentUserRole === "OWNER";
  const canManage = MANAGE_ROLES.includes(currentUserRole as BoardMemberRole);
  const canEdit = EDIT_ROLES.includes(currentUserRole as BoardMemberRole);

  return { currentUserRole, isOwner, canManage, canEdit, isLoading };
}

/**
 * Returns a Set of board IDs where the current user has VIEWER role.
 * Leverages cached board-member data (populated by the sidebar's
 * per-board useBoardRole calls) so no extra network requests are made.
 */
export function useBoardViewerIds(boardIds: string[]) {
  const { data: user } = useGetMe();
  const currentUserId = user?.data?.id;
  const memberQueries = useMultipleBoardMembers(boardIds);

  return useMemo(() => {
    const ids = new Set<string>();
    if (!currentUserId) return ids;
    for (let i = 0; i < boardIds.length; i++) {
      const members = memberQueries[i]?.data?.data;
      if (!members) continue;
      const myRole = members.find((m) => m.userId === currentUserId)?.role;
      if (myRole === "VIEWER") {
        ids.add(boardIds[i]!);
      }
    }
    return ids;
  }, [boardIds, memberQueries, currentUserId]);
}

/**
 * Returns a Map of boardId → role for boards where the current user
 * lacks manage permissions (MEMBER or VIEWER). Boards where the user
 * is not a member at all are NOT included.
 */
export function useNonManageableBoardRoles(boardIds: string[]) {
  const { data: user } = useGetMe();
  const currentUserId = user?.data?.id;
  const memberQueries = useMultipleBoardMembers(boardIds);

  return useMemo(() => {
    const map = new Map<string, BoardMemberRole>();
    if (!currentUserId) return map;
    for (let i = 0; i < boardIds.length; i++) {
      const members = memberQueries[i]?.data?.data;
      if (!members) continue;
      const myRole = members.find((m) => m.userId === currentUserId)?.role;
      if (myRole && !MANAGE_ROLES.includes(myRole)) {
        map.set(boardIds[i]!, myRole);
      }
    }
    return map;
  }, [boardIds, memberQueries, currentUserId]);
}

/**
 * Checks whether the given role has manage permissions (OWNER or ADMIN).
 */
export function isBoardManageRole(role: BoardMemberRole | undefined): boolean {
  return !!role && MANAGE_ROLES.includes(role);
}

/**
 * Returns the ids of the given boards on which the current user is OWNER or
 * ADMIN. Board-member lists are shared cache entries, so boards already seen
 * by the sidebar cost no extra request.
 */
export function useManageableBoardIds(boardIds: string[]) {
  const { data: user } = useGetMe();
  const currentUserId = user?.data?.id;
  const memberQueries = useMultipleBoardMembers(boardIds);

  return useMemo(() => {
    const ids = new Set<string>();
    if (!currentUserId) return ids;
    for (let i = 0; i < boardIds.length; i++) {
      const members = memberQueries[i]?.data?.data;
      const myRole = members?.find((m) => m.userId === currentUserId)?.role;
      if (isBoardManageRole(myRole)) ids.add(boardIds[i]!);
    }
    return ids;
  }, [boardIds, memberQueries, currentUserId]);
}
