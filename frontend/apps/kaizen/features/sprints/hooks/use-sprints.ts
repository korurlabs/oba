"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { isApiError } from "@oba/shared/api/axios";
import {
  listSprints,
  listSprintChildBoards,
  getSprintById,
  createSprint,
  updateSprint,
  updateSprintSeries,
  startSprint,
  getSprintStartPreview,
  completeSprint,
  linkSprint,
  bulkLinkSprint,
  unlinkSprint,
  deleteSprint,
} from "../api";
import type {
  SprintCreateRequest,
  SprintUpdateRequest,
  SprintSeriesRequest,
  SprintBulkLinkRequest,
  SprintUnlinkRequest,
  SprintCompleteRequest,
  SprintStartRequest,
} from "../schemas";
import type { TicketRelocationAction } from "@/features/tickets/schemas";
import { invalidateRelatedQueries } from "@/hooks/invalidate-related-queries";
import { SPRINT_KEYS } from "./query-keys";

/**
 * Query keys live in the boundary-safe `./query-keys` module (no "use client")
 * so server components can prefetch under the exact same keys without the keys
 * becoming client-reference proxies. Re-exported here for client consumers.
 */
export { SPRINT_KEYS };

const KEYS = SPRINT_KEYS;

/**
 * The 422 for a sprint name or series name the series rules refuse: a name
 * sent for a sprint of an enabled series, a series name for any other
 * sprint, or a series name that is blank or over 240 characters.
 */
export const SPRINT_SERIES_NAME_INVALID_CODE = "KAI_VAL_060";

/**
 * The 409 for a create, series switch-on or link that would put a board into a
 * second enabled sprint series. `errorValues` name the board and the series
 * that holds it; a request that lost the race for the board names only the
 * board, because its aborted transaction cannot read the winning series.
 */
export const SPRINT_SERIES_BOARD_TAKEN_CODE = "KAI_STA_018";

/**
 * The toast text for a failed sprint write: the translated series naming
 * rule for {@link SPRINT_SERIES_NAME_INVALID_CODE}, the translated board
 * conflict for {@link SPRINT_SERIES_BOARD_TAKEN_CODE} with the names it
 * carries, else the server message.
 */
export function sprintErrorMessage(
  error: Error,
  t: (key: string, values?: Record<string, string>) => string,
): string {
  if (isApiError(error, SPRINT_SERIES_NAME_INVALID_CODE)) {
    return t("sprints.messages.seriesNameInvalid");
  }
  if (isApiError(error, SPRINT_SERIES_BOARD_TAKEN_CODE)) {
    const { boardName, seriesName } = error.errorValues;
    if (typeof boardName === "string" && typeof seriesName === "string") {
      return t("sprints.messages.boardInOtherSeries", { board: boardName, series: seriesName });
    }
    if (typeof boardName === "string") {
      return t("sprints.messages.boardInOtherSeriesUnnamed", { board: boardName });
    }
  }
  return error.message;
}

/**
 * The 422 for a complete with `moveToNextSprint` on a sprint that has no next
 * round to move into: no enabled series, or no live board in its series.
 */
export const SPRINT_NEXT_SPRINT_UNAVAILABLE_CODE = "KAI_VAL_061";

export function useSprints(boardId: string, options?: { enabled?: boolean }) {
  const enabled = options?.enabled ?? true;
  return useQuery({
    queryKey: KEYS.list(boardId),
    queryFn: () => listSprints(boardId),
    enabled: !!boardId && enabled,
  });
}

/**
 * Child boards offered in the create sprint dialog. Only board admins may
 * read them, so callers enable the query only while that dialog is open.
 */
export function useSprintChildBoards(
  boardId: string,
  options?: { enabled?: boolean },
) {
  const enabled = options?.enabled ?? true;
  return useQuery({
    queryKey: KEYS.childBoards(boardId),
    queryFn: () => listSprintChildBoards(boardId),
    enabled: !!boardId && enabled,
  });
}

export function useSprint(
  boardId: string,
  sprintId: string,
  options?: { enabled?: boolean },
) {
  const enabled = options?.enabled ?? true;
  return useQuery({
    queryKey: KEYS.detail(boardId, sprintId),
    queryFn: () => getSprintById(boardId, sprintId),
    enabled: !!boardId && !!sprintId && enabled,
  });
}

export function useCreateSprint() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({ boardId, data }: { boardId: string; data: SprintCreateRequest }) =>
      createSprint(boardId, data),
    onSuccess: () => {
      toast.success(t("sprints.messages.created"));
      invalidateRelatedQueries(queryClient);
    },
    onError: (error: Error) => {
      toast.error(sprintErrorMessage(error, t));
    },
  });
}

export function useUpdateSprint() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({
      boardId,
      sprintId,
      data,
    }: {
      boardId: string;
      sprintId: string;
      data: SprintUpdateRequest;
    }) => updateSprint(boardId, sprintId, data),
    onSuccess: () => {
      toast.success(t("sprints.messages.updated"));
      invalidateRelatedQueries(queryClient);
    },
    onError: (error: Error) => {
      toast.error(sprintErrorMessage(error, t));
    },
  });
}

/**
 * Saves the settings of a sprint's series, starting a series named after the
 * sprint when it has none and repeat is switched on.
 *
 * `dates` is sent first, through the sprint update, when the sprint's dates
 * are not yet at 00:00 UTC: the server refuses to switch automation on for
 * such dates, and the General tab only edits them as UTC days once
 * automation is on. Both requests are one user action, so they share one
 * toast.
 */
export function useUpdateSprintSeries() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: async ({
      boardId,
      sprintId,
      data,
      dates,
    }: {
      boardId: string;
      sprintId: string;
      data: SprintSeriesRequest;
      dates?: Pick<SprintUpdateRequest, "startDate" | "endDate">;
    }) => {
      if (dates) {
        await updateSprint(boardId, sprintId, dates);
      }
      return updateSprintSeries(boardId, sprintId, data);
    },
    onSuccess: () => {
      toast.success(t("sprints.messages.seriesUpdated"));
      // The whole sprint key space, not one board's detail and list: a
      // shared sprint is cached under every linked board's id, and all of
      // them now show the new settings. Switching repeat on can rename the
      // series' sprints, so lists everywhere are refreshed too.
      queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: (error: Error) => {
      toast.error(sprintErrorMessage(error, t));
    },
  });
}

/**
 * The active sprints a start of `sprintId` would close. Only board admins may
 * read it, so callers enable it only for a planned sprint the user manages;
 * it is then ready before the Start button is pressed.
 */
export function useSprintStartPreview(
  boardId: string,
  sprintId: string,
  options?: { enabled?: boolean },
) {
  const enabled = options?.enabled ?? true;
  return useQuery({
    queryKey: KEYS.startPreview(boardId, sprintId),
    queryFn: () => getSprintStartPreview(boardId, sprintId),
    enabled: !!boardId && !!sprintId && enabled,
  });
}

/**
 * The 409 a plain start gets when a board it is linked to already runs an
 * active sprint, which a takeover would have to close.
 */
export const SPRINT_ACTIVE_SPRINT_EXISTS_CODE = "KAI_STA_004";

/**
 * Starts a sprint, with `data.takeOver` closing the active sprints that block
 * it. A plain start refused with {@link SPRINT_ACTIVE_SPRINT_EXISTS_CODE} is not
 * toasted here: the caller re-reads the start preview and offers the takeover
 * dialog, and toasts the error itself when there is nothing to take over. A takeover changes sprints and tickets on every board linked to them,
 * so all sprint, ticket and backlog queries are invalidated, not one board's.
 */
export function useStartSprint() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({
      boardId,
      sprintId,
      data,
    }: {
      boardId: string;
      sprintId: string;
      data?: SprintStartRequest;
    }) => startSprint(boardId, sprintId, data),
    onSuccess: () => {
      toast.success(t("sprints.messages.started"));
      invalidateRelatedQueries(queryClient);
    },
    onError: (error: Error, variables) => {
      const blockedPlainStart =
        !variables.data?.takeOver && isApiError(error, SPRINT_ACTIVE_SPRINT_EXISTS_CODE);
      if (blockedPlainStart) return;
      toast.error(error.message);
    },
  });
}

export function useCompleteSprint() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({
      boardId,
      sprintId,
      data,
    }: {
      boardId: string;
      sprintId: string;
      data: SprintCompleteRequest;
    }) => completeSprint(boardId, sprintId, data),
    onSuccess: () => {
      toast.success(t("sprints.messages.completed"));
      invalidateRelatedQueries(queryClient);
    },
    onError: (error: Error) => {
      if (!isApiError(error, SPRINT_NEXT_SPRINT_UNAVAILABLE_CODE)) {
        toast.error(error.message);
        return;
      }
      toast.error(t("sprints.messages.nextSprintUnavailable"));
      // The series changed since the dialog loaded it (switched off, or its
      // boards left); reload it so the dialog stops offering the next sprint.
      queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
  });
}

export function useLinkSprint() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({ boardId, sprintId }: { boardId: string; sprintId: string }) =>
      linkSprint(boardId, sprintId),
    onSuccess: () => {
      toast.success(t("sprints.messages.linked"));
      invalidateRelatedQueries(queryClient);
    },
    onError: (error: Error) => {
      toast.error(sprintErrorMessage(error, t));
    },
  });
}

export function useBulkLinkSprint() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({
      boardId,
      sprintId,
      data,
    }: {
      boardId: string;
      sprintId: string;
      data: SprintBulkLinkRequest;
    }) => bulkLinkSprint(boardId, sprintId, data),
    onSuccess: () => {
      toast.success(t("sprints.messages.bulkLinked"));
      invalidateRelatedQueries(queryClient);
    },
    onError: (error: Error) => {
      toast.error(sprintErrorMessage(error, t));
    },
  });
}

export function useUnlinkSprint() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({
      boardId,
      sprintId,
      data,
    }: {
      boardId: string;
      sprintId: string;
      data: SprintUnlinkRequest;
    }) => unlinkSprint(boardId, sprintId, data),
    onSuccess: () => {
      toast.success(t("sprints.messages.unlinked"));
      invalidateRelatedQueries(queryClient);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useDeleteSprint(options?: {
  /** Runs after cache removal but before query invalidation.
   *  Use this to close sheets/dialogs so their detail queries unmount
   *  before `invalidateRelatedQueries` triggers refetches. */
  onBeforeInvalidate?: () => void;
}) {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({
      boardId,
      sprintId,
      ticketAction,
      targetSprintId,
    }: {
      boardId: string;
      sprintId: string;
      ticketAction: TicketRelocationAction;
      targetSprintId?: string;
    }) => deleteSprint(boardId, sprintId, { ticketAction, targetSprintId }),
    onSuccess: async (_, variables) => {
      toast.success(t("sprints.messages.deleted"));
      const detailKey = KEYS.detail(variables.boardId, variables.sprintId);
      queryClient.removeQueries({ queryKey: detailKey });
      options?.onBeforeInvalidate?.();
      await invalidateRelatedQueries(queryClient, { excludeKeys: [detailKey] });
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}
