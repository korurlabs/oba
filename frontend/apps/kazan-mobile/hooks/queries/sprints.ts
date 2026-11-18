import {
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import { useCallback, useMemo } from "react";

import { apiErrorToastMessage } from "@/components/auth/apiErrorToastModel";
import {
  boardPath,
  hasJoinableSprint,
  manageableBoardIds,
  parentSprintOptions,
  partialJoinDescription,
  type JoinParentSprintError,
  type ParentSprintOption,
  type PartialJoinDescription,
} from "@/components/sprints/joinParentSprintModel";
import {
  linkRefusalMessage,
  type SprintBoardsMessage,
  type SprintBulkLinkBody,
  type SprintUnlinkBody,
} from "@/components/sprints/sprintBoardsModel";
import { toast } from "@/components/ui/toast";
import {
  boardKeys,
  fetchBoardMembers,
  fetchSprints,
  useBoardTree,
  useInvalidateBoards,
} from "@/hooks/queries/boards";
import type { TicketRef } from "@/hooks/queries/ticketDetail";
import { invalidateTicketViews, ticketKeys } from "@/hooks/queries/tickets";
import { useMe } from "@/hooks/queries/users";
import { translate } from "@/i18n";
import { api, isValidationError, readData, toApiError, type ApiError } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import { type BoardMember, type BoardTreeItem, type Sprint } from "@/lib/schemas/boards";
import {
  sprintChildBoardListSchema,
  sprintDetailSchema,
  sprintStartPreviewSchema,
  ticketMoveSourceSchema,
  type SprintChildBoard,
  type SprintCompleteBody,
  type SprintCreateBody,
  type SprintDetail,
  type SprintSeriesBody,
  type SprintStartPreview,
  type SprintUpdateBody,
  type TicketMoveSource,
} from "@/lib/schemas/sprints";

/**
 * Query keys of sprint reads. They live under the board keys, so every write
 * that refreshes a board's sprint list refreshes these too.
 */
export const sprintKeys = {
  detail: (orgId: string, boardId: string, sprintId: string) =>
    [...boardKeys.all(orgId), "sprint", boardId, sprintId] as const,
  childBoards: (orgId: string, boardId: string) =>
    [...boardKeys.all(orgId), "sprint-child-boards", boardId] as const,
  startPreview: (orgId: string, boardId: string, sprintId: string) =>
    [...boardKeys.all(orgId), "sprint-start-preview", boardId, sprintId] as const,
  /** Under the ticket keys, so every ticket write marks it stale with the detail. */
  moveSource: (orgId: string, boardId: string, ticketId: string) =>
    [...ticketKeys.all(orgId), "move-source", boardId, ticketId] as const,
};

/**
 * Marks every board, ticket and Home read stale after a sprint write. A sprint
 * can be shared by several boards, and a create, start or complete moves
 * tickets between sprints and backlogs, so no narrower set is safe.
 */
function invalidateSprintViews(queryClient: QueryClient, orgId: string | null): Promise<void> {
  if (!orgId) return Promise.resolve();
  // The ticket views include Home.
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: boardKeys.all(orgId) }),
    invalidateTicketViews(queryClient, orgId),
  ]).then(() => undefined);
}

/** `GET /kazan/boards/{boardId}/sprints/{sprintId}`: the sprint with its series. */
export function useSprintDetail(boardId: string | undefined, sprintId: string | undefined) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<SprintDetail>({
    queryKey: sprintKeys.detail(orgId ?? "", boardId ?? "", sprintId ?? ""),
    queryFn: async () => {
      const response = await api.get(
        API_ROUTES.sprints.byId(boardId as string, sprintId as string)
      );
      return readData(sprintDetailSchema, response.data);
    },
    enabled: !!orgId && !!boardId && !!sprintId,
  });
}

/** `GET /kazan/boards/{boardId}/sprints/child-boards`: the boards a new sprint can also go to. */
export function useSprintChildBoards(boardId: string | undefined) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<SprintChildBoard[]>({
    queryKey: sprintKeys.childBoards(orgId ?? "", boardId ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.sprints.childBoards(boardId as string));
      return readData(sprintChildBoardListSchema, response.data);
    },
    enabled: !!orgId && !!boardId,
  });
}

/**
 * `GET /kazan/boards/{boardId}/tickets/{ticketId}` read for Move to sprint:
 * the ticket's sprint and the sprint of each sub-ticket, so the sheet can
 * say where each sub-ticket sits now.
 */
export function useTicketMoveSource(ref: TicketRef | undefined) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<TicketMoveSource>({
    queryKey: sprintKeys.moveSource(orgId ?? "", ref?.boardId ?? "", ref?.ticketId ?? ""),
    queryFn: async () => {
      const target: TicketRef = ref as TicketRef;
      const response = await api.get(API_ROUTES.tickets.byId(target.boardId, target.ticketId));
      return readData(ticketMoveSourceSchema, response.data);
    },
    enabled: !!orgId && !!ref,
  });
}

/**
 * Reads `GET .../{sprintId}/start-preview` fresh for a start: the active
 * sprints the start would close. Read at the tap, because another admin may
 * have started or completed a sprint since the board loaded.
 */
export function useFetchStartPreview(
  boardId: string
): (sprintId: string) => Promise<SprintStartPreview> {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useCallback(
    (sprintId: string) =>
      queryClient.fetchQuery({
        queryKey: sprintKeys.startPreview(orgId ?? "", boardId, sprintId),
        queryFn: async () => {
          const response = await api.get(API_ROUTES.sprints.startPreview(boardId, sprintId));
          return readData(sprintStartPreviewSchema, response.data);
        },
        staleTime: 0,
      }),
    [boardId, orgId, queryClient]
  );
}

/**
 * `POST /kazan/boards/{boardId}/sprints`. A validation failure is not
 * toasted: the sheet shows it under the field. Every other failure is toasted
 * by the app's error layer.
 */
export function useCreateSprint(boardId: string) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<SprintDetail, Error, SprintCreateBody>({
    mutationFn: async (body) => {
      const response = await api.post(API_ROUTES.sprints.list(boardId), body);
      return readData(sprintDetailSchema, response.data);
    },
    onSettled: () => invalidateSprintViews(queryClient, orgId),
  });
}

/** `PUT /kazan/boards/{boardId}/sprints/{sprintId}`. */
export function useUpdateSprint(boardId: string, sprintId: string) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<SprintDetail, Error, SprintUpdateBody>({
    mutationFn: async (body) => {
      const response = await api.put(API_ROUTES.sprints.byId(boardId, sprintId), body);
      return readData(sprintDetailSchema, response.data);
    },
    onSettled: () => invalidateSprintViews(queryClient, orgId),
  });
}

/** A series change, with the sprint dates it needs first when automation is switched on. */
export interface SprintSeriesChange {
  series: SprintSeriesBody;
  /** The sprint's dates moved to 00:00 UTC, the only time an automated sprint may have. */
  dates?: { startDate: string; endDate: string };
}

/**
 * `PUT .../{sprintId}/series`, after `PUT .../{sprintId}` with the dates when
 * the change brings them: the server refuses automation for a sprint whose
 * dates are not at 00:00 UTC.
 */
export function useUpdateSprintSeries(boardId: string, sprintId: string) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<SprintDetail, Error, SprintSeriesChange>({
    mutationFn: async ({ series, dates }) => {
      if (dates) await api.put(API_ROUTES.sprints.byId(boardId, sprintId), dates);
      const response = await api.put(API_ROUTES.sprints.series(boardId, sprintId), series);
      return readData(sprintDetailSchema, response.data);
    },
    onSettled: () => invalidateSprintViews(queryClient, orgId),
  });
}

/** `POST .../{sprintId}/start`; `takeOver` closes the active sprints in the way and moves their tickets in. */
export function useStartSprint(boardId: string) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<SprintDetail, Error, { sprintId: string; takeOver: boolean }>({
    mutationFn: async ({ sprintId, takeOver }) => {
      const response = await api.post(
        API_ROUTES.sprints.start(boardId, sprintId),
        takeOver ? { takeOver: true } : {}
      );
      return readData(sprintDetailSchema, response.data);
    },
    onSettled: () => invalidateSprintViews(queryClient, orgId),
  });
}

/** `POST .../{sprintId}/complete`, with what happens to the unfinished tickets. */
export function useCompleteSprint(boardId: string, sprintId: string) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<SprintDetail, Error, SprintCompleteBody>({
    mutationFn: async (body) => {
      const response = await api.post(API_ROUTES.sprints.complete(boardId, sprintId), body);
      return readData(sprintDetailSchema, response.data);
    },
    onSettled: () => invalidateSprintViews(queryClient, orgId),
  });
}

/**
 * `POST /kazan/boards/{boardId}/sprints/{sprintId}/bulk-link`: links the
 * picked boards to the sprint, in pick order. A board already in another
 * repeating series is refused in the app's words with the names the refusal
 * carries, as on the web; every other refusal is toasted as the app's error
 * layer words it, and a validation refusal with the server's message.
 */
export function useBulkLinkSprint(boardId: string, sprintId: string) {
  const invalidate = useInvalidateBoards();
  return useMutation<SprintDetail, Error, SprintBulkLinkBody>({
    mutationFn: async (body) => {
      const url: string = API_ROUTES.sprints.bulkLink(boardId, sprintId);
      // Silent: the refusal is toasted below, where the series conflict gets its own wording.
      const response = await api.post(url, body, { _silentErrors: true });
      return readData(sprintDetailSchema, response.data);
    },
    onSuccess: () => {
      toast.success(translate("sprintBoardsLinked"));
    },
    onError: (error: Error) => {
      const refusal: ApiError = toApiError(error);
      console.warn("[sprints] bulk link failed", refusal.code ?? refusal.kind, refusal.message);
      const worded: SprintBoardsMessage | null = linkRefusalMessage(refusal);
      const message: string | null = worded
        ? translate(worded.key, worded.values)
        : isValidationError(refusal)
          ? refusal.message
          : apiErrorToastMessage({ error: refusal, method: "post", url: undefined }, translate);
      if (message !== null) toast.error(message);
    },
    onSettled: () => invalidate(),
  });
}

/**
 * `POST /kazan/boards/{unlinkedBoardId}/sprints/{sprintId}/unlink`: takes the
 * sprint off a board, after moving or deleting that board's tickets of the
 * sprint. The app's error layer toasts a refusal with the server's message;
 * a validation refusal, which that layer leaves to forms, is toasted here.
 */
export function useUnlinkSprint(sprintId: string) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<void, Error, { unlinkedBoardId: string; body: SprintUnlinkBody }>({
    mutationFn: async ({ unlinkedBoardId, body }) => {
      await api.post(API_ROUTES.sprints.unlink(unlinkedBoardId, sprintId), body);
    },
    onSuccess: () => {
      toast.success(translate("sprintBoardsUnlinked"));
    },
    onError: (error: Error) => {
      if (isValidationError(error)) toast.error(error.message);
    },
    // Moved or deleted tickets change every board view, the ticket screens and Home.
    onSettled: () => invalidateSprintViews(queryClient, orgId),
  });
}

// ── Join a parent board's sprint ─────────────────────────────────

/** The boards above a board with their active sprints, and the state of the reads behind them. */
export interface ParentSprintOptionsState {
  /** The ancestors, nearest first. */
  parentOptions: ParentSprintOption[];
  /** True while the board tree or an ancestor's sprints are still being read. */
  isLoading: boolean;
  /** True when some ancestor runs a sprint the user may link down to this board. */
  hasJoinableSprint: boolean;
  /** The first failed read of the tree, the sprints or the members; null when none failed. */
  error: Error | null;
  /** Reads the failed ones again. */
  refetch: () => void;
}

/**
 * The ancestors of a board, nearest first, each with its active sprint, for
 * the sheet that lets a board join a parent board's sprint. Reads the board
 * tree, each ancestor's sprints and, because the board list carries no role,
 * the members of every board on the path. The reads share the board views'
 * cache, so boards already seen cost no extra request.
 */
export function useParentSprintOptions(boardId: string | undefined): ParentSprintOptionsState {
  const orgId: string | null = useActiveOrganizationId();
  const tree = useBoardTree();
  const me = useMe(true);

  // Root first; the current board is the last entry.
  const path: BoardTreeItem[] = useMemo(
    () => (tree.data && boardId ? boardPath(tree.data, boardId) : []),
    [tree.data, boardId]
  );
  const ancestors: BoardTreeItem[] = path.slice(0, -1);

  const sprintQueries = useQueries({
    queries: ancestors.map((ancestor) => ({
      queryKey: boardKeys.sprints(orgId ?? "", ancestor.id),
      queryFn: () => fetchSprints(ancestor.id),
      enabled: !!orgId,
    })),
  });
  const memberQueries = useQueries({
    queries: path.map((board) => ({
      queryKey: boardKeys.members(orgId ?? "", board.id),
      queryFn: () => fetchBoardMembers(board.id),
      enabled: !!orgId,
    })),
  });

  const sprintsByBoard: Record<string, Sprint[]> = {};
  ancestors.forEach((ancestor, index) => {
    const data: Sprint[] | undefined = sprintQueries[index]?.data;
    if (data) sprintsByBoard[ancestor.id] = data;
  });
  const membersByBoard: Record<string, BoardMember[]> = {};
  path.forEach((board, index) => {
    const data: BoardMember[] | undefined = memberQueries[index]?.data;
    if (data) membersByBoard[board.id] = data;
  });

  const manageable: Set<string> = manageableBoardIds(
    path.map((board) => board.id),
    membersByBoard,
    me.data?.id ?? null
  );
  const parentOptions: ParentSprintOption[] = parentSprintOptions(path, sprintsByBoard, manageable);
  const failed = [tree, me, ...sprintQueries, ...memberQueries].filter((query) => query.isError);

  return {
    parentOptions,
    isLoading: tree.isLoading || sprintQueries.some((query) => query.isLoading),
    hasJoinableSprint: hasJoinableSprint(parentOptions),
    error: failed[0]?.error ?? null,
    refetch: () => {
      for (const query of failed) void query.refetch();
    },
  };
}

/**
 * `POST /kazan/boards/{boardId}/sprints/{sprintId}/link` for every board of
 * the option's `linkPath`, top-down, so each link is to a child of a board
 * that already has the sprint. The links are separate requests: when one is
 * refused, the boards linked before it keep the sprint, and the error toast
 * names them. A board already in another repeating series is refused in the
 * app's words, as on the web; every other refusal is toasted as the app's
 * error layer words it, and a validation refusal with the server's message.
 */
export function useJoinParentSprint() {
  const invalidate = useInvalidateBoards();
  return useMutation<void, JoinParentSprintError, { sprintId: string; linkPath: BoardTreeItem[] }>({
    mutationFn: async ({ sprintId, linkPath }) => {
      const linked: string[] = [];
      for (const board of linkPath) {
        try {
          // Silent: the refusal is toasted below, with the boards already linked.
          await api.post(API_ROUTES.sprints.link(board.id, sprintId), undefined, {
            _silentErrors: true,
          });
        } catch (error: unknown) {
          const failure: Error = error instanceof Error ? error : new Error(String(error));
          throw Object.assign(failure, { linkedBoardNames: linked });
        }
        linked.push(board.name);
      }
    },
    onSuccess: () => {
      toast.success(translate("sprintJoinedParentSprint"));
    },
    onError: (error: JoinParentSprintError) => {
      const refusal: ApiError = toApiError(error);
      const linked: string[] = error.linkedBoardNames ?? [];
      console.warn(
        "[sprints] join parent sprint failed",
        refusal.code ?? refusal.kind,
        refusal.message,
        linked
      );
      const worded: SprintBoardsMessage | null = linkRefusalMessage(refusal);
      // A refusal the error layer leaves to a form (validation, a field code) has
      // no form here, so it keeps the server's message.
      const layerMessage: string | null = isValidationError(refusal)
        ? null
        : apiErrorToastMessage({ error: refusal, method: "post", url: undefined }, translate);
      const message: string = worded
        ? translate(worded.key, worded.values)
        : (layerMessage ?? refusal.message);
      const partial: PartialJoinDescription | null = partialJoinDescription(linked);
      toast.error(
        message,
        partial ? { description: translate(partial.key, partial.values) } : undefined
      );
    },
    onSettled: () => invalidate(),
  });
}
