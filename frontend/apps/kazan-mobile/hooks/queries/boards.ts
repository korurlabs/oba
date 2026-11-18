import {
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
  type Query,
} from "@tanstack/react-query";
import { useCallback } from "react";

import { useMe } from "@/hooks/queries/users";
import { boardIdBySlug } from "@/components/boards/boardForms";
import {
  boardTicketParams,
  countByBoard,
  type BoardFilters,
  type Quarter,
  type TicketScope,
} from "@/components/boards/boardModel";
import {
  isDeletedBoardQuery,
  type DeleteBoardParams,
} from "@/components/boards/settings/deleteBoardModel";
import { toast } from "@/components/ui/toast";
import { homeKeys } from "@/hooks/queries/home";
import { OPEN_TICKET_STATUSES } from "@/hooks/queries/ticketParams";
import { invalidateTicketViews, ticketKeys } from "@/hooks/queries/tickets";
import { translate } from "@/i18n";
import { api, isValidationError, readData, readPage, toApiError } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import {
  backlogSchema,
  boardDetailSchema,
  boardListItemSchema,
  boardMemberCandidateListSchema,
  boardMemberListSchema,
  boardOptionListSchema,
  boardTicketListSchema,
  boardTreeItemSchema,
  milestoneListSchema,
  roadmapSchema,
  sprintListSchema,
  type Backlog,
  type BoardDetail,
  type BoardListItem,
  type BoardMember,
  type BoardMemberCandidate,
  type BoardOwnedOption,
  type BoardTicket,
  type BoardTreeItem,
  type Milestone,
  type Roadmap,
  type Sprint,
} from "@/lib/schemas/boards";
import { ticketListSchema, type TicketListItem } from "@/lib/schemas/tickets";

/** The largest page the API serves. */
export const BOARD_PAGE_SIZE = 50;

/** Pages of the board tree read at most; 25 pages of 50 top-level boards is far above any organization. */
const MAX_BOARD_PAGES = 25;

/** How often the P1 banner rereads open incidents while the Boards list is on screen. */
const P1_REFETCH_MS = 60_000;

/** Query keys of boards, scoped by organization. */
export const boardKeys = {
  all: (orgId: string) => ["boards", orgId] as const,
  list: (orgId: string) => [...boardKeys.all(orgId), "list"] as const,
  tree: (orgId: string) => [...boardKeys.all(orgId), "tree"] as const,
  detail: (orgId: string, boardId: string) => [...boardKeys.all(orgId), "detail", boardId] as const,
  bySlug: (orgId: string, slug: string) => [...boardKeys.all(orgId), "by-slug", slug] as const,
  sprints: (orgId: string, boardId: string) =>
    [...boardKeys.all(orgId), "sprints", boardId] as const,
  backlog: (orgId: string, boardId: string) =>
    [...boardKeys.all(orgId), "backlog", boardId] as const,
  members: (orgId: string, boardId: string) =>
    [...boardKeys.all(orgId), "members", boardId] as const,
  /** Under the board's members key, so a write that refreshes the members refreshes it too. */
  memberCandidates: (orgId: string, boardId: string) =>
    [...boardKeys.members(orgId, boardId), "candidates"] as const,
  labels: (orgId: string, boardId: string) => [...boardKeys.all(orgId), "labels", boardId] as const,
  projects: (orgId: string, boardId: string) =>
    [...boardKeys.all(orgId), "projects", boardId] as const,
  milestones: (orgId: string, boardId: string) =>
    [...boardKeys.all(orgId), "milestones", boardId] as const,
  roadmap: (orgId: string, boardId: string, from: string, to: string) =>
    [...boardKeys.all(orgId), "roadmap", boardId, from, to] as const,
};

/**
 * Keys of the board ticket reads. They live under the ticket keys, so any
 * ticket write that invalidates the organization's tickets refreshes them too.
 */
export const boardTicketKeys = {
  board: (orgId: string, boardId: string) => [...ticketKeys.all(orgId), "board", boardId] as const,
  list: (orgId: string, boardId: string, params: Record<string, string>) =>
    [...boardTicketKeys.board(orgId, boardId), params] as const,
  openByBoard: (orgId: string) => [...ticketKeys.all(orgId), "open-by-board"] as const,
  openP1: (orgId: string) => [...ticketKeys.all(orgId), "open-p1"] as const,
};

/** The first page of the user's boards, by name, with the total count. */
export interface BoardList {
  boards: BoardListItem[];
  total: number;
}

/** `GET /kazan/boards`: the boards of the active organization the user is a member of. */
export function useBoards() {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<BoardList>({
    queryKey: boardKeys.list(orgId ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.boards.list, {
        params: { page: 1, amount: BOARD_PAGE_SIZE },
      });
      const page = readPage(boardListItemSchema, response.data);
      const boards: BoardListItem[] = [...page.items].sort((a, b) => a.name.localeCompare(b.name));
      return { boards, total: page.pagination?.totalElements ?? page.items.length };
    },
    enabled: !!orgId,
  });
}

/**
 * `GET /kazan/boards?includeChildren=true`, every page: the top-level boards
 * the user is a member of, each with the child boards the user is a member of.
 */
export function useBoardTree() {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<BoardTreeItem[]>({
    queryKey: boardKeys.tree(orgId ?? ""),
    queryFn: async () => {
      const boards: BoardTreeItem[] = [];
      for (let page = 1; page <= MAX_BOARD_PAGES; page += 1) {
        const response = await api.get(API_ROUTES.boards.list, {
          params: { page, amount: BOARD_PAGE_SIZE, includeChildren: true },
        });
        const result = readPage(boardTreeItemSchema, response.data);
        boards.push(...result.items);
        const total: number = result.pagination?.totalElements ?? boards.length;
        if (result.items.length === 0 || boards.length >= total) break;
      }
      return boards;
    },
    enabled: !!orgId,
  });
}

/** Tries of a board read; a refusal or a missing board is final and is not tried again. */
const BOARD_READ_TRIES = 3;

/** True when a failed board read is worth another try. */
export function shouldRetryBoardRead(failureCount: number, error: unknown): boolean {
  const kind: string = toApiError(error).kind;
  if (kind === "forbidden" || kind === "not-found") return false;
  return failureCount < BOARD_READ_TRIES;
}

/**
 * `GET /kazan/boards/{boardId}`. A 403 or 404 fails at once, so the screen
 * says "no access" without waiting for retries.
 */
export function useBoard(boardId: string | undefined) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<BoardListItem>({
    queryKey: boardKeys.detail(orgId ?? "", boardId ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.boards.byId(boardId as string));
      return readData(boardListItemSchema, response.data);
    },
    enabled: !!orgId && !!boardId,
    retry: shouldRetryBoardRead,
  });
}

/** How long a slug read stays fresh; a slug changes only when a board is renamed. */
const BOARD_SLUG_STALE_MS = 5 * 60_000;

/**
 * Resolves a board slug to its id, for links that carry slugs (Home's sprint
 * and deadline rows). The board tree answers when it is cached; otherwise
 * `GET /kazan/boards/by-slug/{slug}` does. A failed read rejects, and the
 * caller surfaces it.
 */
export function useResolveBoardSlug(): (slug: string) => Promise<string> {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useCallback(
    async (slug: string): Promise<string> => {
      if (!orgId) throw new Error("No active organization");
      const tree: BoardTreeItem[] | undefined = queryClient.getQueryData(boardKeys.tree(orgId));
      const cached: string | null = tree ? boardIdBySlug(tree, slug) : null;
      if (cached) return cached;
      const board: BoardListItem = await queryClient.fetchQuery({
        queryKey: boardKeys.bySlug(orgId, slug),
        queryFn: async () => {
          const response = await api.get(API_ROUTES.boards.bySlug(slug));
          return readData(boardListItemSchema, response.data);
        },
        staleTime: BOARD_SLUG_STALE_MS,
      });
      return board.id;
    },
    [orgId, queryClient]
  );
}

/** Reads `GET /kazan/boards/{boardId}/sprints`; shared by every query under `boardKeys.sprints`. */
export async function fetchSprints(boardId: string): Promise<Sprint[]> {
  const response = await api.get(API_ROUTES.sprints.list(boardId));
  return readData(sprintListSchema, response.data);
}

/** `GET /kazan/boards/{boardId}/sprints`. */
export function useBoardSprints(boardId: string | undefined) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<Sprint[]>({
    queryKey: boardKeys.sprints(orgId ?? "", boardId ?? ""),
    queryFn: () => fetchSprints(boardId as string),
    enabled: !!orgId && !!boardId,
  });
}

/**
 * The sprints of several boards at once, for the active sprint on each row of
 * the Boards list. They share the cache of the board views. A board whose
 * sprints failed to load is missing from the map; its row shows no sprint.
 */
export function useSprintsOfBoards(boardIds: readonly string[]): Record<string, Sprint[]> {
  const orgId: string | null = useActiveOrganizationId();
  const results = useQueries({
    queries: boardIds.map((boardId) => ({
      queryKey: boardKeys.sprints(orgId ?? "", boardId),
      queryFn: () => fetchSprints(boardId),
      enabled: !!orgId,
    })),
  });
  const map: Record<string, Sprint[]> = {};
  boardIds.forEach((boardId, index) => {
    const data: Sprint[] | undefined = results[index]?.data;
    if (data) map[boardId] = data;
  });
  return map;
}

/** `GET /kazan/boards/{boardId}/backlog`: the backlog id that scopes its ticket list. */
export function useBacklog(boardId: string | undefined) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<Backlog>({
    queryKey: boardKeys.backlog(orgId ?? "", boardId ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.boards.backlog(boardId as string));
      return readData(backlogSchema, response.data);
    },
    enabled: !!orgId && !!boardId,
  });
}

/**
 * `GET /kazan/boards/{boardId}/tickets` of one sprint or the backlog, with
 * the filters and search text. Unpaged: the list is every matching root ticket.
 */
export function useBoardTickets(
  boardId: string | undefined,
  scope: TicketScope | null,
  filters: BoardFilters,
  search: string
) {
  const orgId: string | null = useActiveOrganizationId();
  const params: Record<string, string> | null = scope
    ? boardTicketParams(scope, filters, search)
    : null;
  return useQuery<BoardTicket[]>({
    queryKey: boardTicketKeys.list(orgId ?? "", boardId ?? "", params ?? {}),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.tickets.list(boardId as string), { params });
      return readData(boardTicketListSchema, response.data);
    },
    enabled: !!orgId && !!boardId && !!params,
  });
}

/** Reads `GET /kazan/boards/{boardId}/members`; shared by every query keyed `boardKeys.members`. */
export async function fetchBoardMembers(boardId: string): Promise<BoardMember[]> {
  const response = await api.get(API_ROUTES.boards.members(boardId));
  return readData(boardMemberListSchema, response.data);
}

/** `GET /kazan/boards/{boardId}/members`. */
export function useBoardMembers(boardId: string | undefined) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<BoardMember[]>({
    queryKey: boardKeys.members(orgId ?? "", boardId ?? ""),
    queryFn: () => fetchBoardMembers(boardId as string),
    enabled: !!orgId && !!boardId,
  });
}

/**
 * `GET /kazan/boards/{boardId}/members/candidates`: the active members of the
 * board's organization that Add member offers, each with `kazanAccess`. The
 * server answers only a board ADMIN or OWNER, the roles that reach Add member.
 */
export function useBoardMemberCandidates(boardId: string | undefined) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<BoardMemberCandidate[]>({
    queryKey: boardKeys.memberCandidates(orgId ?? "", boardId ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.boards.memberCandidates(boardId as string));
      return readData(boardMemberCandidateListSchema, response.data);
    },
    enabled: !!orgId && !!boardId,
  });
}

/** The signed-in user's role on a board, with the state of the two reads it comes from. */
export interface MyBoardRoleState {
  /** The role, or null for a non-member and while the reads load. */
  role: string | null;
  /** True while the user or the board's members are still being read. */
  isPending: boolean;
  /**
   * True while a pending read waits for the network or retries a failed
   * attempt, so a caller that cannot wait goes on without the role.
   */
  isStalled: boolean;
  /** The failed read, if one failed. */
  error: Error | null;
  /** Reads the failed ones again. */
  refetch: () => void;
}

/**
 * The signed-in user's role on a board, for a screen that must tell "not a
 * manager" from "not loaded yet" before it shows or hides anything.
 */
export function useMyBoardRoleState(boardId: string | undefined): MyBoardRoleState {
  const me = useMe(true);
  const members = useBoardMembers(boardId);
  const meId: string | undefined = me.data?.id;
  const error: Error | null = me.error ?? members.error ?? null;
  const stalled = (query: { isPending: boolean; fetchStatus: string; failureCount: number }) =>
    query.isPending && (query.fetchStatus === "paused" || query.failureCount > 0);
  const isPending: boolean = error === null && (me.isPending || members.isPending);
  return {
    role: meId ? (members.data?.find((member) => member.userId === meId)?.role ?? null) : null,
    isPending,
    isStalled: isPending && (stalled(me) || stalled(members)),
    error,
    refetch: () => {
      if (me.isError) void me.refetch();
      if (members.isError) void members.refetch();
    },
  };
}

/** The signed-in user's role on a board, or null while it loads or for a non-member. */
export function useMyBoardRole(boardId: string | undefined): string | null {
  return useMyBoardRoleState(boardId).role;
}

function useBoardOptions(
  boardId: string | undefined,
  key: "labels" | "projects",
  path: (id: string) => string
) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<BoardOwnedOption[]>({
    queryKey: boardKeys[key](orgId ?? "", boardId ?? ""),
    queryFn: async () => {
      const response = await api.get(path(boardId as string));
      return readData(boardOptionListSchema, response.data);
    },
    enabled: !!orgId && !!boardId,
  });
}

/** `GET /kazan/boards/{boardId}/labels`. */
export function useBoardLabels(boardId: string | undefined) {
  return useBoardOptions(boardId, "labels", API_ROUTES.boards.labels);
}

/** `GET /kazan/boards/{boardId}/projects`. */
export function useBoardProjects(boardId: string | undefined) {
  return useBoardOptions(boardId, "projects", API_ROUTES.boards.projects);
}

/** `GET /kazan/boards/{boardId}/milestones`. */
export function useBoardMilestones(boardId: string | undefined) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<Milestone[]>({
    queryKey: boardKeys.milestones(orgId ?? "", boardId ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.boards.milestones(boardId as string));
      return readData(milestoneListSchema, response.data);
    },
    enabled: !!orgId && !!boardId,
  });
}

/** `GET /kazan/boards/{boardId}/roadmap` for a quarter, with the child boards as lanes. */
export function useRoadmap(boardId: string | undefined, quarter: Quarter) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<Roadmap>({
    queryKey: boardKeys.roadmap(orgId ?? "", boardId ?? "", quarter.from, quarter.to),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.boards.roadmap(boardId as string), {
        params: { from: quarter.from, to: quarter.to, includeChildBoards: true },
      });
      return readData(roadmapSchema, response.data);
    },
    enabled: !!orgId && !!boardId,
  });
}

/**
 * Open root tickets per board, from one cross-board read, for the open count
 * on each row of the Boards list.
 */
export function useOpenTicketCounts() {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<Record<string, number>>({
    queryKey: boardTicketKeys.openByBoard(orgId ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.tickets.all, {
        params: { statuses: OPEN_TICKET_STATUSES.join(",") },
      });
      return countByBoard(readData(ticketListSchema, response.data));
    },
    enabled: !!orgId,
  });
}

/** Open P1 incidents, newest first, for the banner on the Boards list. */
export function useOpenP1Incidents() {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<TicketListItem[]>({
    queryKey: boardTicketKeys.openP1(orgId ?? ""),
    queryFn: async () => {
      const response = await api.get(API_ROUTES.tickets.all, {
        params: {
          types: "INCIDENT",
          priorities: "P1",
          statuses: OPEN_TICKET_STATUSES.join(","),
          sortBy: "createdAt",
          sortDirection: "desc",
        },
      });
      return readData(ticketListSchema, response.data);
    },
    enabled: !!orgId,
    refetchInterval: P1_REFETCH_MS,
  });
}

// ── Writes ───────────────────────────────────────────────────────

/** The body of `POST /kazan/boards`. */
export interface BoardCreateBody {
  name: string;
  prefix: string;
  color: string;
  parentBoardId?: string;
}

/** The body of `PUT /kazan/boards/{boardId}`; only the fields sent change. */
export interface BoardUpdateBody {
  name?: string;
  parentBoardId?: string;
  clearParent?: boolean;
}

/**
 * Refreshes every board read of the organization, and Home, whose sprint and
 * board pickers list the boards.
 */
export function useInvalidateBoards() {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return () => {
    if (!orgId) return Promise.resolve();
    return Promise.all([
      queryClient.invalidateQueries({ queryKey: boardKeys.all(orgId) }),
      queryClient.invalidateQueries({ queryKey: homeKeys.all(orgId) }),
    ]).then(() => undefined);
  };
}

/**
 * `POST /kazan/boards`. A validation failure is not toasted: the sheet shows
 * it under the field. Every other failure is toasted by the app's error layer.
 */
export function useCreateBoard() {
  const invalidate = useInvalidateBoards();
  return useMutation<BoardDetail, Error, BoardCreateBody>({
    mutationFn: async (body) => {
      const response = await api.post(API_ROUTES.boards.list, body);
      return readData(boardDetailSchema, response.data);
    },
    onSettled: () => invalidate(),
  });
}

/** `PUT /kazan/boards/{boardId}`. */
export function useUpdateBoard(boardId: string) {
  const invalidate = useInvalidateBoards();
  return useMutation<BoardDetail, Error, BoardUpdateBody>({
    mutationFn: async (body) => {
      const response = await api.put(API_ROUTES.boards.byId(boardId), body);
      return readData(boardDetailSchema, response.data);
    },
    onSettled: () => invalidate(),
  });
}

/** What `useDeleteBoard` sends: the web's query and the boards it deletes. */
export interface DeleteBoardVariables {
  params: DeleteBoardParams;
  /** The board and, when its child boards are deleted with it, every descendant. */
  deletedBoardIds: readonly string[];
}

/**
 * `DELETE /kazan/boards/{boardId}` with the web's query: what happens to the
 * board's tickets and child boards. On success it toasts as the web does,
 * drops every read of the deleted boards, so no screen reads them again into
 * a "not found", runs `onBeforeInvalidate` to leave their screens, and then
 * refreshes the boards, the tree, Home and every ticket view. A refusal is
 * toasted with the server's message: by the app's error layer, or here for a
 * field validation failure, which that layer leaves to forms.
 */
export function useDeleteBoard(boardId: string, options?: { onBeforeInvalidate?: () => void }) {
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const invalidate = useInvalidateBoards();
  return useMutation<void, Error, DeleteBoardVariables>({
    mutationFn: async ({ params }) => {
      await api.delete(API_ROUTES.boards.byId(boardId), { params });
    },
    onSuccess: (_, { deletedBoardIds }) => {
      toast.success(translate("deleteBoardDeleted"));
      if (orgId) {
        const deleted: ReadonlySet<string> = new Set(deletedBoardIds);
        const predicate = (query: Query): boolean => isDeletedBoardQuery(query.queryKey, deleted);
        queryClient.removeQueries({ queryKey: boardKeys.all(orgId), predicate });
        queryClient.removeQueries({ queryKey: ticketKeys.all(orgId), predicate });
      }
      options?.onBeforeInvalidate?.();
      return Promise.all([invalidate(), invalidateTicketViews(queryClient, orgId)]).then(
        () => undefined
      );
    },
    onError: (error: Error) => {
      if (isValidationError(error)) toast.error(error.message);
    },
  });
}
