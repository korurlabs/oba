import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo } from "react";

import { apiErrorToastMessage } from "@/components/auth/apiErrorToastModel";
import { toast } from "@/components/ui/toast";
import {
  boardKeys,
  fetchBoardMembers,
  useInvalidateBoards,
  type BoardUpdateBody,
} from "@/hooks/queries/boards";
import { ticketKeys } from "@/hooks/queries/tickets";
import { useMe } from "@/hooks/queries/users";
import { translate, type TranslationKey } from "@/i18n";
import { api, readData, toApiError } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  milestoneDetailSchema,
  type AssignableBoardRole,
  type BoardMember,
  type BoardOptionBody,
  type MilestoneCreateBody,
  type MilestoneDetail,
  type MilestoneUpdateBody,
} from "@/lib/schemas/boards";

/*
 * The reads and writes of Board settings: labels, projects, milestones, child
 * boards and members. Each sends the request Kaizen web sends for the same
 * action. Every write settles by refreshing the organization's board reads
 * (and Home); label, project and milestone writes also refresh the tickets,
 * whose cards and filters show them.
 *
 * Failures reach the user through the app's error layer, which toasts the
 * server's message, as the web hooks do. Label and project writes are the
 * exception: the web toasts a fixed message for each of them, so they are sent
 * with `_silentErrors` and their hooks toast that message. Removing a child
 * board is sent silently too, because the web names the board above the
 * server's message. The toast lives in
 * the hook, not the screen, so it still shows when the screen closed while the
 * request was pending.
 */

/** Query keys of Board settings reads. The milestone lives under the board keys, so board writes refresh it. */
export const boardSettingsKeys = {
  milestone: (orgId: string, boardId: string, milestoneId: string) =>
    [...boardKeys.all(orgId), "milestone", boardId, milestoneId] as const,
};

/** Refreshes the board reads, Home and, for writes that tickets show, every ticket read. */
function useInvalidateSettings(refreshTickets: boolean): () => Promise<void> {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const invalidateBoards = useInvalidateBoards();
  return () =>
    Promise.all([
      invalidateBoards(),
      refreshTickets && orgId
        ? queryClient.invalidateQueries({ queryKey: ticketKeys.all(orgId) })
        : undefined,
    ]).then(() => undefined);
}

// ── Reads ────────────────────────────────────────────────────────

/** `GET /kaizen/boards/{boardId}/milestones/{milestoneId}`: the milestone with its description. */
export function useMilestone(boardId: string | undefined, milestoneId: string | undefined) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<MilestoneDetail>({
    queryKey: boardSettingsKeys.milestone(orgId ?? "", boardId ?? "", milestoneId ?? ""),
    queryFn: async () => {
      const response = await api.get(
        API_ROUTES.boards.milestone(boardId as string, milestoneId as string)
      );
      return readData(milestoneDetailSchema, response.data);
    },
    enabled: !!orgId && !!boardId && !!milestoneId,
  });
}

/**
 * The data of each member list query. Defined once, so `useQueries` keeps the
 * combined array while no list changes and the role map stays the same object.
 */
function memberListsOf(results: { data?: BoardMember[] }[]): (BoardMember[] | undefined)[] {
  return results.map((result) => result.data);
}

/**
 * The reader's role on each of `boardIds`, from each board's member list, as
 * the web reads it for the child board and Add existing checks. A board the
 * reader is not a member of, or whose list has not loaded, is missing from the
 * map. The member lists share the cache of `useBoardMembers`.
 */
export function useBoardRoles(boardIds: readonly string[]): ReadonlyMap<string, string> {
  const orgId: string | null = useActiveOrganizationId();
  const meId: string | undefined = useMe(true).data?.id;
  const memberLists: (BoardMember[] | undefined)[] = useQueries({
    queries: boardIds.map((boardId) => ({
      queryKey: boardKeys.members(orgId ?? "", boardId),
      queryFn: () => fetchBoardMembers(boardId),
      enabled: !!orgId,
    })),
    combine: memberListsOf,
  });
  return useMemo(() => {
    const roles: Map<string, string> = new Map();
    if (!meId) return roles;
    boardIds.forEach((boardId, index) => {
      const role: string | undefined = memberLists[index]?.find(
        (member) => member.userId === meId
      )?.role;
      if (role) roles.set(boardId, role);
    });
    return roles;
  }, [boardIds, memberLists, meId]);
}

// ── Labels and projects ──────────────────────────────────────────

/** The two option lists of a board; their requests differ only in the path. */
export type BoardOptionKind = "labels" | "projects";

const OPTION_PATHS = {
  labels: { list: API_ROUTES.boards.labels, item: API_ROUTES.boards.label },
  projects: { list: API_ROUTES.boards.projects, item: API_ROUTES.boards.project },
} as const;

/** The web's fixed failure message of each label and project write. */
const OPTION_ERROR_TEXTS: Record<
  BoardOptionKind,
  { create: TranslationKey; update: TranslationKey; delete: TranslationKey }
> = {
  labels: {
    create: "boardLabelsCreateError",
    update: "boardLabelsUpdateError",
    delete: "boardLabelsDeleteError",
  },
  projects: {
    create: "boardProjectsCreateError",
    update: "boardProjectsUpdateError",
    delete: "boardProjectsDeleteError",
  },
};

/** An existing label or project, addressed under the board that owns it. */
export interface BoardOptionTarget {
  ownerBoardId: string;
  optionId: string;
}

/**
 * `POST /kaizen/boards/{boardId}/labels` or `/projects` with `{ name, color }`.
 * Sent silently, so a failure shows only the web's fixed message.
 */
export function useCreateBoardOption(kind: BoardOptionKind, boardId: string) {
  const invalidate = useInvalidateSettings(true);
  return useMutation<void, Error, BoardOptionBody>({
    mutationFn: async (body) => {
      await api.post(OPTION_PATHS[kind].list(boardId), body, { _silentErrors: true });
    },
    onError: (error) => {
      console.warn(`[boardSettings] ${kind} create failed`, error.message);
      toast.error(translate(OPTION_ERROR_TEXTS[kind].create));
    },
    onSettled: () => invalidate(),
  });
}

/**
 * `PUT .../labels/{id}` or `.../projects/{id}` under the owning board, which
 * may be a related board, as the web sends it. Sent silently, so a failure
 * shows only the web's fixed message.
 */
export function useUpdateBoardOption(kind: BoardOptionKind) {
  const invalidate = useInvalidateSettings(true);
  return useMutation<void, Error, BoardOptionTarget & { body: BoardOptionBody }>({
    mutationFn: async ({ ownerBoardId, optionId, body }) => {
      await api.put(OPTION_PATHS[kind].item(ownerBoardId, optionId), body, {
        _silentErrors: true,
      });
    },
    onError: (error) => {
      console.warn(`[boardSettings] ${kind} update failed`, error.message);
      toast.error(translate(OPTION_ERROR_TEXTS[kind].update));
    },
    onSettled: () => invalidate(),
  });
}

/**
 * `DELETE .../labels/{id}` or `.../projects/{id}` under the owning board.
 * Sent silently, so a failure shows only the web's fixed message.
 */
export function useDeleteBoardOption(kind: BoardOptionKind) {
  const invalidate = useInvalidateSettings(true);
  return useMutation<void, Error, BoardOptionTarget>({
    mutationFn: async ({ ownerBoardId, optionId }) => {
      await api.delete(OPTION_PATHS[kind].item(ownerBoardId, optionId), {
        _silentErrors: true,
      });
    },
    onError: (error) => {
      console.warn(`[boardSettings] ${kind} delete failed`, error.message);
      toast.error(translate(OPTION_ERROR_TEXTS[kind].delete));
    },
    onSettled: () => invalidate(),
  });
}

// ── Milestones ───────────────────────────────────────────────────

/** An existing milestone, addressed under its board. */
export interface MilestoneTarget {
  boardId: string;
  milestoneId: string;
}

/** `POST /kaizen/boards/{boardId}/milestones`. */
export function useCreateMilestone(boardId: string) {
  const invalidate = useInvalidateSettings(true);
  return useMutation<void, Error, MilestoneCreateBody>({
    mutationFn: async (body) => {
      await api.post(API_ROUTES.boards.milestones(boardId), body);
    },
    onSettled: () => invalidate(),
  });
}

/** `PUT /kaizen/boards/{boardId}/milestones/{milestoneId}`; build the body with `milestoneUpdateBody`. */
export function useUpdateMilestone() {
  const invalidate = useInvalidateSettings(true);
  return useMutation<void, Error, MilestoneTarget & { body: MilestoneUpdateBody }>({
    mutationFn: async ({ boardId, milestoneId, body }) => {
      await api.put(API_ROUTES.boards.milestone(boardId, milestoneId), body);
    },
    onSettled: () => invalidate(),
  });
}

/** `POST .../milestones/{milestoneId}/close`. */
export function useCloseMilestone() {
  const invalidate = useInvalidateSettings(true);
  return useMutation<void, Error, MilestoneTarget>({
    mutationFn: async ({ boardId, milestoneId }) => {
      await api.post(API_ROUTES.boards.milestoneClose(boardId, milestoneId));
    },
    onSettled: () => invalidate(),
  });
}

/** `POST .../milestones/{milestoneId}/reopen`. */
export function useReopenMilestone() {
  const invalidate = useInvalidateSettings(true);
  return useMutation<void, Error, MilestoneTarget>({
    mutationFn: async ({ boardId, milestoneId }) => {
      await api.post(API_ROUTES.boards.milestoneReopen(boardId, milestoneId));
    },
    onSettled: () => invalidate(),
  });
}

/**
 * `DELETE .../milestones/{milestoneId}`. The deleted milestone's detail is
 * dropped from the cache before the refresh, so it is not read again into a
 * "not found".
 */
export function useDeleteMilestone() {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const invalidate = useInvalidateSettings(true);
  return useMutation<void, Error, MilestoneTarget>({
    mutationFn: async ({ boardId, milestoneId }) => {
      await api.delete(API_ROUTES.boards.milestone(boardId, milestoneId));
    },
    onSuccess: (_data, { boardId, milestoneId }) => {
      if (orgId) {
        queryClient.removeQueries({
          queryKey: boardSettingsKeys.milestone(orgId, boardId, milestoneId),
        });
      }
    },
    onSettled: () => invalidate(),
  });
}

// ── Child boards ─────────────────────────────────────────────────

/** `PUT /kaizen/boards/{boardId}/children` with the picked ids, in pick order. */
export function useAddChildBoards(boardId: string) {
  const invalidate = useInvalidateSettings(false);
  return useMutation<void, Error, { childBoardIds: string[] }>({
    mutationFn: async (body) => {
      await api.put(API_ROUTES.boards.children(boardId), body);
    },
    onSettled: () => invalidate(),
  });
}

/**
 * Removes a child from its parent as the web does: `PUT /kaizen/boards/{childId}`
 * with `{ clearParent: true }`. Sent silently, so a failure shows once, as the
 * web's "Could not move {name}" with the server's reason under it.
 */
export function useRemoveChildBoard() {
  const invalidate = useInvalidateSettings(false);
  return useMutation<void, Error, { childBoardId: string; childBoardName: string }>({
    mutationFn: async ({ childBoardId }) => {
      const body: BoardUpdateBody = { clearParent: true };
      await api.put(API_ROUTES.boards.byId(childBoardId), body, { _silentErrors: true });
    },
    onError: (error, { childBoardName }) => {
      console.warn("[boardSettings] remove child failed", error.message);
      const reason: string =
        apiErrorToastMessage(
          { error: toApiError(error), method: "put", url: undefined },
          translate
        ) ?? translate("apiErrorGeneric");
      toast.error(translate("boardChildBoardsRemoveFailed", { name: childBoardName }), {
        description: reason,
      });
    },
    onSettled: () => invalidate(),
  });
}

// ── Members ──────────────────────────────────────────────────────

/**
 * `POST /kaizen/boards/{boardId}/members` with `{ userId, role }`. The success
 * toast is the hook's, as on the web, so it shows even when the sheet closed
 * while the request was pending.
 */
export function useAddBoardMember(boardId: string) {
  const invalidate = useInvalidateSettings(false);
  return useMutation<void, Error, { userId: string; role: AssignableBoardRole }>({
    mutationFn: async (body) => {
      await api.post(API_ROUTES.boards.members(boardId), body);
    },
    onSuccess: () => toast.success(translate("boardMembersAdded")),
    onSettled: () => invalidate(),
  });
}

/** `PUT /kaizen/boards/{boardId}/members/{userId}` with `{ role }`; the hook toasts the success, as on the web. */
export function useUpdateBoardMemberRole(boardId: string) {
  const invalidate = useInvalidateSettings(false);
  return useMutation<void, Error, { userId: string; role: AssignableBoardRole }>({
    mutationFn: async ({ userId, role }) => {
      await api.put(API_ROUTES.boards.member(boardId, userId), { role });
    },
    onSuccess: () => toast.success(translate("boardMembersUpdated")),
    onSettled: () => invalidate(),
  });
}

/**
 * `DELETE /kaizen/boards/{boardId}/members/{userId}`, without a confirmation,
 * as on the web; the hook toasts the success, as the web hook does.
 */
export function useRemoveBoardMember(boardId: string) {
  const invalidate = useInvalidateSettings(false);
  return useMutation<void, Error, { userId: string }>({
    mutationFn: async ({ userId }) => {
      await api.delete(API_ROUTES.boards.member(boardId, userId));
    },
    onSuccess: () => toast.success(translate("boardMembersRemoved")),
    onSettled: () => invalidate(),
  });
}
