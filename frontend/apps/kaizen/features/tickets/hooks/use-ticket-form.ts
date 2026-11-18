/**
 * useTicketForm Hook
 *
 * Encapsulates all state management, data fetching, computed values,
 * mutations, form setup, and handlers for the TicketForm component.
 */

"use client";

import { useState, useEffect, useLayoutEffect, useMemo, useCallback, useRef } from "react";
import { parseISO } from "date-fns";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import type { TicketUser } from "@/types/ticket";
import type { TicketListResponse, TicketDetailResponse, TicketFormValues } from "@/features/tickets/schemas";
import { ticketFormSchema, TICKET_BACKLOG_SPRINT_VALUE, TICKET_NO_MILESTONE_VALUE } from "../schemas";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  useTicket,
  useTickets,
  useCreateTicket,
  useDeleteTicket,
  useMoveTicket,
  useBulkMoveTickets,
  useUpdateTicket,
  useUpdateTicketOrder,
  resyncTicketAction,
} from "./use-tickets";
import { useBoardMembers } from "@/features/boards/hooks/use-board-members";
import { hasKaizenAccess } from "@/lib/kaizen-member-access";
import { useBoardViewerIds } from "@/features/boards/hooks/use-board-role";
import { useRelatedBoards, useCachedBoard, useBoards } from "@/features/boards/hooks/use-boards";
import type { BoardListWithChildrenResponse } from "@/features/boards/schemas";
import { MissionRank, TicketType, TicketPriority, TicketStatus } from "@/types/enums";
import { useBacklog } from "@/features/backlog/hooks/use-backlog";
import type { SprintListResponse } from "@/features/sprints/schemas";
import { devError } from "@oba/shared/helpers/dev-log";
import { isMissionRankRejected } from "../lib/ticket-errors";
import { requiresIncidentClose, useIncidentCloseGuard } from "./use-incident-close-guard";
import type {
  IncidentCloseDialogProps,
  IncidentCloseValues,
} from "../components/incident-close-dialog";
import {
  usePendingImages,
  commitPendingImages,
  reconcilePendingImages,
  collectOrphanAttachmentIds,
  deleteAttachments,
  StorageEntityType,
  ATTACHMENT_KEYS,
  useUploadAttachments,
  checkAttachmentExists,
} from "@oba/shared/features/attachments";
import { useQueryClient } from "@tanstack/react-query";

// ── Interfaces ────────────────────────────────────────────────────────

/**
 * A board member the assignee and reviewer pickers offer. `kaizenAccess` is
 * false for a member who cannot open Kaizen in the organization: the server
 * refuses to add them to a ticket, so the pickers show them disabled.
 */
export type TicketPickerUser = TicketUser & { kaizenAccess: boolean };

export interface UseTicketFormProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  boardId: string;
  ticketId?: string | null;
  parentTicketId?: string | null;
  initialStatus?: TicketStatus;
  sprintId?: string;
  backlogId?: string;
  onTicketCreated?: () => void;
  onTicketUpdated?: () => void;
  availableTickets?: TicketListResponse[];
  /**
   * Navigate the form to a different ticket (e.g. clicking a sub-ticket
   * row). Wired to a route push so the URL stays the single source of
   * truth for which ticket is shown — navigating parent → sub-ticket adds
   * a history entry, and browser back returns to the parent.
   *
   * Without it, sub-ticket navigation falls back to form-internal state,
   * which the URL cannot override.
   */
  onNavigateToTicket?: (ticketId: string, boardId?: string) => void;
}

// ── Placement and status rules (shared with TicketFormFields) ─────────

/**
 * A ticket's sprint or backlog as the Placement field spells it: the sprint
 * id, `TICKET_BACKLOG_SPRINT_VALUE`, or undefined when it has neither.
 */
export function toPlacementValue(
  placed: Pick<TicketDetailResponse, "sprint" | "backlog"> | null | undefined,
): string | undefined {
  if (placed?.sprint?.id) return placed.sprint.id;
  return placed?.backlog?.id ? TICKET_BACKLOG_SPRINT_VALUE : undefined;
}

/**
 * The Placement a ticket takes when the form moves it to another board. A
 * sprint is linked to boards, and the move only accepts a sprint linked to
 * the target board: a sprint shared with that board is kept, otherwise an
 * open sprint of the same name stands in for it, and anything else lands in
 * the target board's backlog. The user can still pick another sprint there.
 */
export function resolvePlacementOnBoard(
  currentSprint: { id: string; name: string } | null | undefined,
  targetBoardSprints: SprintListResponse[],
): string {
  if (!currentSprint) return TICKET_BACKLOG_SPRINT_VALUE;
  const openSprints = targetBoardSprints.filter((sprint) => sprint.status !== "COMPLETED");
  const shared = openSprints.find((sprint) => sprint.id === currentSprint.id);
  if (shared) return shared.id;
  const sameName = openSprints.find(
    (sprint) => sprint.name.trim().toLowerCase() === currentSprint.name.trim().toLowerCase(),
  );
  return sameName?.id ?? TICKET_BACKLOG_SPRINT_VALUE;
}

export interface UnparentPlacement {
  /**
   * The Placement field value the ticket takes when it is detached from its
   * parent. Undefined while the parent loads, or for a ticket with no parent.
   */
  placement: string | undefined;
  /** True while the parent's detail is loading. */
  isPending: boolean;
  /** Set when the parent's detail failed to load; `placement` then falls back to the ticket's own. */
  error: Error | null;
}

/**
 * Where an edited sub-ticket ends up when the form detaches it from its
 * parent.
 *
 * A sub-ticket is listed nested under its parent, so the parent's placement
 * is where the user sees it, and the backend gives an unparented ticket the
 * placement of its top-level ancestor. The sub-ticket's own sprint and
 * backlog cannot stand in for it: a move or a sprint completion relocates a
 * parent without its sub-tickets, so they drift. The form offers only
 * top-level tickets as parents, so the direct parent is that ancestor. A
 * parent with neither placement leaves the ticket's own, as the backend does.
 *
 * The parent is read through the same detail query the form uses, so the
 * form hook and the fields component share one request. It lives on the
 * ticket's board, the only board the backend accepts a parent from.
 */
export function useUnparentPlacement(ticket: TicketDetailResponse | null): UnparentPlacement {
  return useParentPlacement(ticket, ticket?.parentTicketId ?? undefined, toPlacementValue(ticket));
}

/**
 * The placement of the parent picked in the Parent field when it is not the
 * ticket's current parent, which is where the backend puts the ticket on a
 * save that attaches it. Empty while no new parent is picked, and while the
 * parent loads. A parent that failed to load, or has no placement, gives no
 * placement: the save then leaves placing the ticket to the backend.
 */
export function useReparentPlacement(
  ticket: TicketDetailResponse | null,
  parentId: string | undefined,
): UnparentPlacement {
  const isNewParent =
    !!ticket && !!parentId && parentId !== "none" && parentId !== (ticket.parentTicketId ?? undefined);
  return useParentPlacement(ticket, isNewParent ? parentId : undefined, undefined);
}

/**
 * Reads a parent's placement through the same detail query the form uses, so
 * every caller shares one request. The parent lives on the ticket's board,
 * the only board the backend accepts a parent from.
 */
function useParentPlacement(
  ticket: TicketDetailResponse | null,
  parentId: string | undefined,
  fallback: string | undefined,
): UnparentPlacement {
  const { data, isLoading, error } = useTicket(ticket?.board?.id ?? "", parentId ?? "");
  if (!parentId) return { placement: undefined, isPending: false, error: null };
  const parent = data?.data;
  if (parent) {
    return {
      placement: toPlacementValue(parent) ?? fallback,
      isPending: false,
      error: null,
    };
  }
  return {
    placement: isLoading ? undefined : fallback,
    isPending: isLoading,
    error,
  };
}

/**
 * True when the edited ticket's Placement is not where the parent in the
 * Parent field is, so the save leaves the ticket without a parent.
 *
 * The backend always places a sub-ticket with its top-level parent: a
 * sub-ticket moved anywhere else becomes a standalone ticket there. The form
 * says so under the Placement field, and the save sends the ticket as
 * standalone instead of attaching it. For the current parent the Placement
 * field is compared with the ticket's own placement first, because an
 * untouched field sends no move, even when the ticket's own placement has
 * drifted from the parent's. False while the parent's placement is unknown.
 */
export function resolveDetachesFromParent({
  ticket,
  sprintId,
  parentId,
  unparentPlacement,
  reparentPlacement,
}: {
  ticket: Pick<TicketDetailResponse, "parentTicketId" | "sprint" | "backlog"> | null;
  sprintId: string | undefined;
  parentId: string | undefined;
  unparentPlacement: string | undefined;
  reparentPlacement: string | undefined;
}): boolean {
  if (!ticket || !sprintId || !parentId || parentId === "none") return false;
  const isCurrentParent = parentId === (ticket.parentTicketId ?? undefined);
  const parentPlacement = isCurrentParent ? unparentPlacement : reparentPlacement;
  if (parentPlacement === undefined || sprintId === parentPlacement) return false;
  return !isCurrentParent || sprintId !== toPlacementValue(ticket);
}

/**
 * The statuses a save can keep for the ticket where the form places it, or
 * undefined when every status is kept.
 *
 * The backend resets a top-level ticket outside a sprint to TODO on every
 * create, update and move, so a top-level backlog ticket can only be TODO. A
 * sub-ticket is exempt: its status is the completion checkbox behind the
 * parent's percentage. A sub-ticket the save detaches becomes top-level, so
 * in a backlog, whether its parent's or one it is moved to, it is TODO as
 * well; detached into a sprint it keeps any status. That includes a ticket
 * whose Placement is away from the parent in the Parent field (see
 * `resolveDetachesFromParent`).
 */
export function resolveAllowedStatuses({
  ticket,
  sprintId,
  parentId,
  unparentPlacement,
  detachesFromParent,
}: {
  ticket: Pick<TicketDetailResponse, "status" | "parentTicketId"> | null;
  sprintId: string | undefined;
  parentId: string | undefined;
  unparentPlacement: string | undefined;
  detachesFromParent: boolean;
}): TicketStatus[] | undefined {
  const keepsAParent = !!parentId && parentId !== "none" && !detachesFromParent;
  if (keepsAParent || sprintId !== TICKET_BACKLOG_SPRINT_VALUE) return undefined;
  // Until the parent's placement is known, the Placement field still holds
  // the ticket's own, possibly stale, placement, so coercing would discard a
  // status the save may keep in a sprint. The save itself waits for it.
  if (ticket?.parentTicketId && unparentPlacement === undefined) return undefined;
  return [TicketStatus.TODO];
}

export function useTicketForm({
  open,
  onOpenChange,
  boardId,
  ticketId: externalTicketId,
  parentTicketId: externalParentTicketId,
  initialStatus,
  sprintId,
  backlogId,
  onTicketCreated,
  onTicketUpdated,
  availableTickets: externalAvailableTickets,
  onNavigateToTicket,
}: UseTicketFormProps) {
  const t = useTranslations();
  const [isSaving, setIsSaving] = useState(false);
  const queryClient = useQueryClient();

  // A form creates at most one ticket. `isSaving` disables the submit button,
  // but only after a re-render, so a fast double click or a repeated Enter
  // can start a second save first. The ref is set and read synchronously.
  const submitInFlightRef = useRef(false);
  // Set once the create request succeeded, and held until the form starts a
  // new create (a reopen, or the sub-ticket create inside the form). Between
  // the success and the close the form still holds the same values, so a
  // second submit would create the ticket again. A create the server
  // rejected never sets it, so that create can be retried.
  const createCommittedRef = useRef(false);
  const [isCreateCommitted, setIsCreateCommitted] = useState(false);
  // The quick-add of a sub-ticket guards itself the same way.
  const subTicketCreateInFlightRef = useRef(false);

  // Drops the cached attachment list for a ticket after this hook has
  // mutated that collection through the raw API functions
  // (`deleteAttachments` in the orphan GC, `commitPendingImages` on
  // upload) rather than through `useUploadAttachments` /
  // `useDeleteAttachment`, which do their own invalidation.
  //
  // `invalidateQueries` alone is NOT enough here: it only MARKS the
  // query stale, and the sheet unmounts it on close, so a reopen would
  // still be served from cache. `removeQueries({ type: "inactive" })`
  // is the half that actually forces a refetch on the next mount --
  // same pairing as hooks/invalidate-related-queries.ts. The
  // `refetchOnMount: "always"` on `useAttachments` covers this too;
  // removing the entry additionally avoids a flash of the stale row
  // while that refetch is in flight.
  const resetAttachmentCache = useCallback(
    (ticketIdToReset: string) => {
      const queryKey = ATTACHMENT_KEYS.list(
        StorageEntityType.TICKET,
        ticketIdToReset,
      );
      void queryClient.invalidateQueries({ queryKey });
      queryClient.removeQueries({ queryKey, type: "inactive" });
    },
    [queryClient],
  );

  // ── Pending image attachments (deferred upload) ───────────────────
  // Owns the in-flight Map<localId, PendingImage> while the user is
  // editing. Drop / paste / picker events register entries via
  // `addPendingImage`; `handleFormSubmit` uploads and rewrites the
  // description on submit, then clears the map. `clearPendingImages`
  // also revokes every blob: URL it owns.
  const {
    pendingImages,
    addPendingImage,
    removePendingImageBySrc,
    clearPendingImages,
    markUploaded: markPendingImageUploaded,
    hasPending,
  } = usePendingImages();

  // Snapshot of the description as it was when the form opened (or as
  // it was after the last successful save). Used by the orphan GC step
  // in the update path to diff prev vs next attachment ids and DELETE
  // anything the user removed during this edit session.
  const prevDescriptionRef = useRef<string>("");

  // ── Staged attachments (create mode) ──────────────────────────────
  // The upload endpoint needs a real entity UUID, which does not exist
  // until the ticket is created. So in create mode the picker only STAGES
  // files here; `handleFormSubmit` uploads them straight after the ticket
  // exists. One step for the user, two calls underneath.
  const [pendingAttachments, setPendingAttachments] = useState<File[]>([]);
  // This instance uploads only the files staged for a create, and that save
  // reports an upload failure in its own single result toast.
  const uploadAttachmentsMutation = useUploadAttachments({ suppressErrorToast: () => true });

  // ── Internal navigation state ─────────────────────────────────────
  const [internalTicketId, setInternalTicketId] = useState<string | null>(null);
  const [internalParentId, setInternalParentId] = useState<string | null>(null);
  const [internalCreateMode, setInternalCreateMode] = useState(false);

  // Resolve the active ticket ID: internal navigation overrides external prop
  const activeTicketId = internalTicketId ?? (externalTicketId ?? null);
  // Determine mode: create if no ticketId, or if internal create mode is active
  const mode: "create" | "edit" = internalCreateMode || !activeTicketId ? "create" : "edit";
  // Resolve parent ID for create mode
  const activeParentId = internalCreateMode
    ? internalParentId
    : mode === "create"
      ? (externalParentTicketId ?? null)
      : null;

  // Reset internal state when form opens/closes or external ticketId changes
  useEffect(() => {
    if (open) {
      setInternalTicketId(null);
      setInternalParentId(null);
      setInternalCreateMode(false);
      setPendingAttachments([]);
      createCommittedRef.current = false;
      setIsCreateCommitted(false);
      // Drop any pending images carried over from a previous open. The
      // hook revokes their blob: URLs as part of clearing so we don't
      // leak ArrayBuffers between sessions. Stable callback reference
      // (useCallback) keeps the deps array honest.
      clearPendingImages();
    }
  }, [open, externalTicketId, clearPendingImages]);

  // ── Board selector state ──────────────────────────────────────────
  // Create mode picks the board the ticket is created on; edit mode moves
  // the ticket to the picked board when it is saved.
  const [selectedBoardId, setSelectedBoardId] = useState(boardId);

  // When boardId is provided, fetch related boards for the board selector.
  // When boardId is empty (e.g., all-tickets page with no tickets), fetch
  // the full board list so the user can choose which board to create a ticket in.
  const hasBoardContext = !!boardId;

  const { data: relatedBoardsResponse, isLoading: isRelatedBoardsLoading } =
    useRelatedBoards(boardId);
  const { data: currentBoard } = useCachedBoard(boardId);

  // Always use the full hierarchical board tree (cached by the sidebar).
  // This ensures proper parent→child nesting regardless of which board the user is on.
  const boardTreeParams = { includeChildren: true, amount: 100 };
  const { data: boardTreeResponse, isLoading: isBoardTreeLoading } = useBoards(boardTreeParams);

  /** Hierarchical board list for SearchSelect / useBoardSelect */
  const hierarchicalBoards = useMemo((): BoardListWithChildrenResponse[] => {
    const fullTree = (boardTreeResponse?.data ?? []) as BoardListWithChildrenResponse[];

    // A ticket may move to any board of the organization the user can
    // contribute to, as in the bulk move dialog, not only to related boards.
    if (mode === "edit") return fullTree;

    // When no board context, use full hierarchical board list
    if (!hasBoardContext) {
      return fullTree;
    }

    // With board context: filter the full tree to only include boards related to
    // the current board, while preserving proper parent→child hierarchy.
    // Build a set of related board IDs (from the flat related boards API + current board).
    const relatedIds = new Set<string>();
    if (boardId) relatedIds.add(boardId);
    for (const b of relatedBoardsResponse?.data ?? []) {
      relatedIds.add(b.id);
    }

    // Filter the tree: keep a board if it or any of its descendants are in relatedIds.
    function filterTree(boards: BoardListWithChildrenResponse[]): BoardListWithChildrenResponse[] {
      const result: BoardListWithChildrenResponse[] = [];
      for (const board of boards) {
        const filteredChildren = board.childBoards?.length
          ? filterTree(board.childBoards as BoardListWithChildrenResponse[])
          : [];
        const isSelfRelated = relatedIds.has(board.id);
        const hasRelatedChildren = filteredChildren.length > 0;

        if (isSelfRelated || hasRelatedChildren) {
          result.push({
            ...board,
            childBoards: filteredChildren,
          });
        }
      }
      return result;
    }

    return filterTree(fullTree);
  }, [relatedBoardsResponse?.data, boardId, mode, hasBoardContext, boardTreeResponse?.data]);

  /** Board IDs where the user is a VIEWER (disabled in SearchSelect / useBoardSelect) */
  const allBoardIdsInTree = useMemo(() => {
    const ids: string[] = [];
    const collect = (boards: BoardListWithChildrenResponse[]) => {
      for (const board of boards) {
        ids.push(board.id);
        if (board.childBoards?.length) {
          collect(board.childBoards as BoardListWithChildrenResponse[]);
        }
      }
    };
    collect(hierarchicalBoards);
    return ids;
  }, [hierarchicalBoards]);

  const viewerBoardIds = useBoardViewerIds(allBoardIdsInTree);

  // ── Board backlog id ──────────────────────────────────────────────
  // Needed in BOTH modes, for the SAME reason: the Sprint field offers
  // "Backlog" (no sprint), and the backend has no "null sprint" -- backlog
  // placement is an explicit backlog id. Edit moves through `POST .../move`;
  // create passes `backlogId` on the POST. Since the create form now carries
  // the Sprint field too (it used to be fixed by the route params), the
  // selected board's backlog has to be resolvable in create as well.
  const { data: backlogResponse, isLoading: isBacklogLoading } = useBacklog(selectedBoardId);
  const boardBacklogId = backlogResponse?.data?.id ?? undefined;
  // The `backlogId` prop is the backlog of the board the form was OPENED
  // from, so it is only a valid stand-in while that is still the selected
  // board. After a board switch the fetched id is the only correct one --
  // placing a ticket in another board's backlog is rejected by
  // `ensureBacklogExistsByBoard`.
  const resolvedBacklogId =
    boardBacklogId ?? (selectedBoardId === boardId ? backlogId : undefined);
  // The submit gate itself is `isBacklogPending`, computed further down --
  // it needs the watched placement field, which only exists once the form
  // does.

  // ── Ticket list & board resolver ──────────────────────────────────

  const parentTicketParams = useMemo(() => {
    if (externalAvailableTickets) return undefined;
    if (!sprintId && !backlogId) return undefined;
    return {
      ...(sprintId ? { sprintId } : {}),
      ...(backlogId ? { backlogId } : {}),
      includeAllBoards: true,
      sortBy: "displayOrder",
      sortDirection: "asc",
    };
  }, [sprintId, backlogId, externalAvailableTickets]);

  const { data: ticketsResponse } = useTickets(boardId, parentTicketParams);
  const allTickets = useMemo(
    () => externalAvailableTickets ?? (ticketsResponse?.data ?? []),
    [externalAvailableTickets, ticketsResponse?.data],
  );

  // Track the most recently loaded ticket detail so `findTicketBoardId`
  // can prefer the authoritative `board.id` from the detail response over
  // the (potentially board-filtered) `allTickets` list. Without this, a
  // sub-ticket that lives on a different board than the page's `boardId`
  // and is missing from `allTickets` would fall back to the page's
  // boardId — causing the wrong `useTicket(boardId, ...)` query.
  const loadedTicketRef = useRef<TicketDetailResponse | null>(null);

  // Helper to find a ticket's board ID. Order of preference:
  //   1. The currently-loaded ticket detail (authoritative — comes from
  //      the `useTicket` response we already have in cache).
  //   2. Sub-ticket of the loaded ticket (also from the detail response).
  //   3. The page's `allTickets` list (best-effort scan).
  //   4. Fall back to the page's `boardId`.
  const findTicketBoardId = useCallback(
    (ticketId: string): string => {
      const loaded = loadedTicketRef.current;
      if (loaded) {
        if (loaded.id === ticketId && loaded.board?.id) return loaded.board.id;
        for (const sub of loaded.subTickets ?? []) {
          if (sub.id === ticketId) {
            return sub.board?.id ?? loaded.board?.id ?? boardId;
          }
        }
      }
      for (const t of allTickets) {
        if (t.id === ticketId) return t.board?.id ?? boardId;
        for (const sub of t.subTickets ?? []) {
          if (sub.id === ticketId) return sub.board?.id ?? t.board?.id ?? boardId;
        }
      }
      return boardId;
    },
    [allTickets, boardId],
  );

  // ── Internal data fetching ────────────────────────────────────────

  const {
    data: ticketDetailResponse,
    isLoading: isTicketDetailLoading,
    isFetching: isTicketDetailFetching,
    isError: isTicketDetailError,
    error: ticketDetailError,
  } = useTicket(
    activeTicketId ? findTicketBoardId(activeTicketId) : boardId,
    activeTicketId ?? "",
  );
  const ticket: TicketDetailResponse | null =
    mode === "edit" ? (ticketDetailResponse?.data ?? null) : null;
  // Mirror the loaded ticket into the ref so `findTicketBoardId` can
  // read it on subsequent calls. Done synchronously here so it's
  // available for the next render's `useTicket` boardId resolution.
  loadedTicketRef.current = ticket;

  // ── Edit-mode load failure ────────────────────────────────────────
  // When the detail fetch errors (404 / 403 / unreachable) for the
  // currently-targeted ticket, close the form, surface the failure as a
  // toast, and let the page clear any deep-link `?id=` param. Without
  // this, a deep link to an inaccessible ticket would silently render an
  // empty edit form instead of bouncing the user out.
  //
  // Fires at most once per `activeTicketId` so React Query's natural
  // re-renders during the close transition don't loop.
  const reportedErrorIdRef = useRef<string | null>(null);
  useEffect(() => {
    // Reset the dedup ref when the form closes or the targeted ticket
    // changes — a re-open against a different (or the same) ticket
    // should be allowed to re-toast and re-close on a fresh failure.
    if (!open || mode !== "edit" || !activeTicketId) {
      if (!open || !activeTicketId) reportedErrorIdRef.current = null;
      return;
    }
    if (!isTicketDetailError) return;
    if (reportedErrorIdRef.current === activeTicketId) return;
    reportedErrorIdRef.current = activeTicketId;

    devError("Ticket detail load failed:", ticketDetailError);
    toast.error(t("kaizen.tickets.messages.ticketLoadFailed"));
    onOpenChange(false);
  }, [
    open,
    mode,
    activeTicketId,
    isTicketDetailError,
    ticketDetailError,
    onOpenChange,
    t,
  ]);

  const ticketBoardId = ticket?.board?.id;

  // Reset selectedBoardId when dialog opens
  useEffect(() => {
    setSelectedBoardId(ticketBoardId ?? boardId);
  }, [boardId, open, ticketBoardId]);

  // Fetch board members (deferred until form is open).
  // When no board context exists, use the selected board for member fetching.
  const membersBoardId = boardId || selectedBoardId;
  const { data: boardMembersResponse } = useBoardMembers(membersBoardId, {
    enabled: open && !!membersBoardId,
  });
  const availableUsers = useMemo(
    (): TicketPickerUser[] =>
      (boardMembersResponse?.data ?? []).map((m) => ({
        id: m.userId,
        userId: m.userId,
        email: m.email,
        firstName: m.firstName,
        lastName: m.lastName,
        name: `${m.firstName} ${m.lastName}`,
        kaizenAccess: hasKaizenAccess(m),
      })),
    [boardMembersResponse?.data],
  );

  // Compute available parent tickets
  const availableParentTickets = useMemo(() => {
    return allTickets.filter((pt) => {
      if (mode === "edit" && pt.id === activeTicketId) return false;
      const isCurrentParent = mode === "edit" && pt.id === ticket?.parentTicketId;
      const isActiveParent = mode === "create" && pt.id === activeParentId;
      return isCurrentParent || isActiveParent || !pt.parentTicketId;
    });
  }, [allTickets, mode, activeTicketId, ticket?.parentTicketId, activeParentId]);

  // ── Current placement (sprint vs backlog) ─────────────────────────
  // The sprint field defaults to where the ticket actually is, read straight
  // off the detail response. `TicketDetailResponse` carries `sprint` and
  // `backlog` for every ticket -- including sub-tickets, which never appear
  // in the cross-board list and so cannot be resolved any other way.
  //
  // A well-formed ticket has exactly one of the two set (the backend enforces
  // the XOR via VAL_TICKET_PLACEMENT). If neither is set the value stays
  // undefined: the select shows its placeholder and `handleFormSubmit` leaves
  // placement untouched. Guessing "backlog" here would silently move sprint
  // tickets out of their sprint on the next save.
  const currentPlacement = useMemo((): string | undefined => {
    if (mode !== "edit" || !ticket) return undefined;
    return toPlacementValue(ticket);
  }, [mode, ticket]);

  // Where the ticket lands if this save detaches it from its parent.
  const unparent = useUnparentPlacement(ticket);

  // ── Mutations ─────────────────────────────────────────────────────
  const createTicketMutation = useCreateTicket();
  // The same mutation the sprint/backlog tables use for their row-level
  // "Move to Sprint" / "Move to Backlog" action, so the optimistic list
  // update and its rollback are identical wherever the user moves a ticket
  // from. The save runs it batched, so the save's own toast and resync
  // replace the mutation's.
  const moveTicketMutation = useMoveTicket();
  // Only the bulk move can take a ticket to another board.
  const bulkMoveTicketsMutation = useBulkMoveTickets();
  const deleteTicketMutation = useDeleteTicket({
    onBeforeInvalidate: () => onOpenChange(false),
  });
  const updateTicketMutation = useUpdateTicket();
  // The same mutation the backlog and sprint tables use for a row drag, so a
  // sub-ticket drag gets the same optimistic reorder, rollback and resync.
  const updateTicketOrderMutation = useUpdateTicketOrder();

  // ── Form ──────────────────────────────────────────────────────────
  // Build the form values from the loaded ticket (edit mode) or empty
  // defaults (create mode). Recomputed when ticket / mode / activeParent
  // changes. We deliberately do NOT include `open` here — opens are
  // handled by the explicit reset effect below, which covers the
  // "reopen with deep-equal defaults" case that RHF's `values` prop
  // can't (it bails out on deep equality, leaving stale user input).
  //
  // Includes EVERY field declared in `ticketFormSchema` so that
  // `form.reset(...)` covers the full form -- omitting a field here
  // causes RHF to retain the previous value, which produces the
  // create-after-create stale-data bug.
  const formValues = useMemo<TicketFormValues>(() => {
    if (ticket && mode === "edit") {
      return {
        title: ticket.title ?? "",
        description: ticket.description ?? "",
        type: ticket.type,
        priority: ticket.priority,
        status: ticket.status,
        assigneeIds: ticket.assignees?.map((a) => a.userId) ?? [],
        reviewerIds: ticket.reviewers?.map((r) => r.userId) ?? [],
        labelIds: ticket.labels?.map((l) => l.id) ?? [],
        // Clamped because the field is still validated (0-100) when it is
        // hidden on a ticket with sub-tickets, and the backend does not bound
        // the stored value -- an out-of-range one would block every save with
        // no visible message.
        percentage: Math.min(100, Math.max(0, ticket.percentage ?? 0)),
        weight: ticket.weight ?? 1,
        // A ticket from a cache older than the required rank may have none.
        missionRank: ticket.missionRank ?? MissionRank.M,
        dueDate: ticket.dueDate ? parseISO(ticket.dueDate) : null,
        projectId: ticket.project?.id ?? undefined,
        milestoneId: ticket.milestone?.id ?? TICKET_NO_MILESTONE_VALUE,
        parentId: ticket.parentTicketId ?? "none",
        sprintId: currentPlacement,
      };
    }
    return {
      title: "",
      description: "",
      type: TicketType.FEATURE,
      priority: TicketPriority.MEDIUM,
      status: initialStatus ?? TicketStatus.TODO,
      assigneeIds: [],
      reviewerIds: [],
      labelIds: [],
      percentage: 0,
      weight: 1,
      missionRank: MissionRank.M,
      dueDate: null,
      projectId: undefined,
      milestoneId: TICKET_NO_MILESTONE_VALUE,
      parentId: activeParentId ?? "none",
      // Create-mode placement is a real field now, so it opens on the
      // placement the user created FROM: the sprint page's sprint, or the
      // backlog for the backlog page and the all-tickets page (neither of
      // which carries a `t_sprint`). The user can change it before saving.
      sprintId: sprintId ?? TICKET_BACKLOG_SPRINT_VALUE,
    };
  }, [ticket, mode, initialStatus, activeParentId, currentPlacement, sprintId]);

  // Note: we intentionally do NOT pass `values: formValues` to useForm.
  // RHF's `values` prop is gated by an internal deep-equality check
  // against the last applied values -- when the user dirties a create
  // form (e.g. types a title) and then submits + reopens, the new
  // `formValues` is deep-equal to what was last applied (both are the
  // empty defaults), so RHF skips the reset and the user's previous
  // input lingers. We drive every reset through the explicit effect
  // below so resets are unconditional on the boundaries we care about.
  const form = useForm<TicketFormValues>({
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    resolver: zodResolver(ticketFormSchema) as any,
    defaultValues: formValues,
    resetOptions: { keepDirtyValues: false, keepErrors: false },
  });

  // ── Form-sync gate ───────────────────────────────────────────────
  // The skeleton in `<TicketFormFields>` is gated on whether the form
  // has been synced to the active target. Without this gate, on the
  // first render where ticket data arrives, the field Controllers
  // would mount synchronously while RHF's reset is still queued in a
  // useEffect — producing a one-frame flash of stale/placeholder
  // values. Tracking the synced target lets us hold the skeleton until
  // the reset has actually run for the current mode/ticket.
  //
  // The reset effect below is the single source of truth for both
  // "reset RHF state" and "mark target as synced".
  //
  // Sentinels:
  //   "__create__" — create mode, synced (synchronous; no async load)
  //   ticket.id    — edit mode synced for that ticket
  //   null         — edit mode awaiting detail load
  type SyncTarget = string | null;
  const expectedSyncTarget: SyncTarget =
    mode === "create" ? "__create__" : (ticket?.id ?? null);
  const [formSyncedTarget, setFormSyncedTarget] = useState<SyncTarget>(
    () => (mode === "create" ? "__create__" : null),
  );

  // ── Explicit form reset (replaces RHF's `values` prop) ───────────
  // RHF's `values` prop bails out via deep-equality against the last
  // applied values, which means after the user dirties a create form
  // and submits, the new (deep-equal-to-defaults) `formValues` is
  // skipped and the user's previous input lingers on reopen. We drive
  // every reset through this effect instead so resets are
  // unconditional on the boundaries we care about:
  //
  //   1. The form opens (close→open transition, tracked by
  //      `wasOpenRef`). Guarantees a fresh reset even when
  //      `formValues` is deep-equal to last time (the
  //      create-after-create stale-data fix).
  //   2. `expectedSyncTarget` changes while open (covers detail load
  //      arrival, internal sub-ticket navigation, and create↔edit
  //      transitions).
  //
  // `formValues` is included in the dep array so the effect observes
  // its changes, but the reset is gated by the
  // `formSyncedTarget !== expectedSyncTarget` guard -- a `formValues`
  // reference change alone does NOT trigger a reset when the target
  // hasn't changed. This is intentional: it preserves the user's
  // in-progress input across background refetches that don't change
  // the active ticket.
  //
  // Marks `formSyncedTarget` after each reset so the skeleton bridges
  // the async gap on edit-mode open without flashing placeholder
  // values. `wasOpenRef` distinguishes "still open, deps changed"
  // from a fresh "close→open" cycle.
  //
  // Runs as a layout effect so RHF state is updated and a follow-up
  // render is committed BEFORE the browser paints the field tree --
  // otherwise users would see a one-frame flash of the previous
  // session's RHF state (e.g. the just-typed title persisting after
  // submit + reopen, the original create-after-create stale-data
  // bug). useEffect alone runs after paint, which is too late.
  const wasOpenRef = useRef(false);
  // The Parent value the Placement follow effect below last acted on, and the
  // Placement value it last wrote. A reset loads fresh values, so it forgets
  // both.
  const placementFollowRef = useRef<{ parentId: string; placement: string | undefined } | null>(
    null,
  );
  useLayoutEffect(() => {
    if (!open) {
      wasOpenRef.current = false;
      return;
    }
    const isOpenTransition = !wasOpenRef.current;
    wasOpenRef.current = true;

    if (isOpenTransition || formSyncedTarget !== expectedSyncTarget) {
      form.reset(formValues);
      placementFollowRef.current = null;
      // Snapshot the description we just loaded so the orphan-GC step
      // in the update path can diff prev vs next attachment ids. Reset
      // happens for both create (empty string) and edit (loaded ticket
      // description) so the ref always reflects the form's starting
      // point.
      prevDescriptionRef.current = ticket?.description ?? "";
      if (formSyncedTarget !== expectedSyncTarget) {
        setFormSyncedTarget(expectedSyncTarget);
      }
    }
  }, [open, expectedSyncTarget, formValues, form, formSyncedTarget, ticket?.description]);

  // True while:
  //   - the edit-mode detail query is in flight or has just failed,
  //   - the cache is still showing a different (previous) ticket
  //     while React Query swaps to the new query,
  //   - mode/ticket has changed but the form hasn't yet been synced to
  //     the new target (covers both deep-link load and the edit→create
  //     sub-ticket transition).
  // Drives the skeleton render in `<TicketFormFields>` so users never
  // see a one-frame flash of stale or placeholder values during any
  // transition.
  const isStalePlaceholder =
    mode === "edit" &&
    !!ticket &&
    !!activeTicketId &&
    ticket.id !== activeTicketId;
  const isFormUnsynced = formSyncedTarget !== expectedSyncTarget;
  const isTicketLoading =
    isFormUnsynced ||
    (mode === "edit" &&
      (!ticket ||
        isStalePlaceholder ||
        isTicketDetailLoading ||
        isTicketDetailError));

  const subTickets = ticket?.subTickets ?? [];

  // ── Incident close ────────────────────────────────────────────────
  // The server refuses to set an incident to Done until it carries an RCA and
  // lessons learned. The form has no fields for them, so a save that closes
  // an incident asks for both in a dialog and sends them in its own request.
  const [isIncidentClosePromptOpen, setIncidentClosePromptOpen] = useState(false);
  // The texts last confirmed in this form, offered again if that save failed
  // and the user saves once more, so nothing typed is lost. Keyed by the
  // ticket, because the form can switch to a sub-ticket or a new ticket.
  const [incidentCloseDraft, setIncidentCloseDraft] = useState<{
    key: string;
    values: IncidentCloseValues;
  }>();
  const incidentCloseResolveRef = useRef<((values: IncidentCloseValues | null) => void) | null>(
    null,
  );

  const settleIncidentClosePrompt = useCallback((values: IncidentCloseValues | null) => {
    const resolve = incidentCloseResolveRef.current;
    incidentCloseResolveRef.current = null;
    setIncidentClosePromptOpen(false);
    resolve?.(values);
  }, []);

  // A form that unmounts with the prompt open ends the waiting save as a
  // cancel, so its `finally` releases the submit lock.
  useEffect(() => () => incidentCloseResolveRef.current?.(null), []);

  /** Resolves to the confirmed texts, or null when the user cancelled. */
  const askIncidentCloseTexts = () =>
    new Promise<IncidentCloseValues | null>((resolve) => {
      incidentCloseResolveRef.current = resolve;
      setIncidentClosePromptOpen(true);
    });

  /**
   * True when this save sets an incident to Done and the stored ticket lacks
   * either text. When both are stored the server accepts the close as is.
   */
  const needsIncidentCloseTexts = (
    type: TicketType,
    currentStatus: TicketStatus | undefined,
    newStatus: TicketStatus | undefined,
    stored: { rca?: string | null; lessonsLearned?: string | null } | null,
  ): boolean =>
    requiresIncidentClose(type, currentStatus, newStatus) &&
    (!stored?.rca?.trim() || !stored?.lessonsLearned?.trim());

  // Ticking an incident sub-ticket done stops at the same dialog.
  const {
    guardStatusChange: guardSubTicketStatusChange,
    dialogProps: subTicketIncidentCloseDialogProps,
  } = useIncidentCloseGuard();

  // ── Pending-image reconciliation ──────────────────────────────────
  // `hasPending` gates the dialog's expand button, so a stale entry blocks
  // expanding for the rest of the session behind a toast about images that
  // are no longer there. The trash-button NodeView calls
  // `removePendingImageBySrc`, but a keyboard delete or an undo removes the
  // node without telling us, so reconcile against the description too.
  //
  // Only a PRESENT -> ABSENT transition evicts. A freshly dropped image is
  // registered a beat before its node reaches the RHF field value, so
  // testing "not currently in the description" alone would evict images the
  // moment they were added.
  const watchedDescription = form.watch("description");
  const lastReconciledDescriptionRef = useRef<string>("");
  useEffect(() => {
    const next = watchedDescription ?? "";
    const previous = lastReconciledDescriptionRef.current;
    lastReconciledDescriptionRef.current = next;
    if (!hasPending || previous === next) return;
    for (const image of pendingImages.values()) {
      if (image.blobUrl && previous.includes(image.blobUrl) && !next.includes(image.blobUrl)) {
        removePendingImageBySrc(image.blobUrl);
      }
    }
  }, [watchedDescription, hasPending, pendingImages, removePendingImageBySrc]);

  // Reset priority when type changes to avoid invalid combinations
  const watchedType = form.watch("type");
  useEffect(() => {
    const currentPriority = form.getValues("priority");
    const validValues =
      watchedType === TicketType.INCIDENT
        ? ["P1", "P2", "P3"]
        : ["LOWEST", "LOW", "MEDIUM", "HIGH", "HIGHEST"];
    if (!validValues.includes(currentPriority)) {
      form.setValue(
        "priority",
        watchedType === TicketType.INCIDENT ? TicketPriority.P2 : TicketPriority.MEDIUM,
      );
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [watchedType]);

  const watchedSprintId = form.watch("sprintId");
  const watchedParentId = form.watch("parentId");
  // The loaded ticket has a parent and the Parent field now says "none": the
  // save sends `clearParent`.
  const isUnparenting =
    mode === "edit" &&
    !!ticket?.parentTicketId &&
    !(watchedParentId && watchedParentId !== "none");

  // The Parent field names a ticket other than the current parent: the save
  // attaches the ticket to it, unless the Placement is away from it.
  const isReparenting =
    mode === "edit" &&
    !!ticket &&
    !!watchedParentId &&
    watchedParentId !== "none" &&
    watchedParentId !== (ticket.parentTicketId ?? undefined);
  const reparent = useReparentPlacement(ticket, watchedParentId);

  // The Placement field shows where the Parent field puts the ticket: its own
  // placement under its current parent; its parent's placement when the form
  // detaches it, because the backend gives an unparented ticket its parent's
  // placement (see `useUnparentPlacement`) rather than its own columns, which
  // may have drifted; and the new parent's placement when one is picked.
  //
  // Picking a new parent always moves the field there, because a sub-ticket
  // is placed with its parent. Any other Parent change moves the field only
  // while it still holds the value the form gave it, so a placement the user
  // picked is kept. A Placement the user moves away from the parent makes the
  // save detach the ticket (see `resolveDetachesFromParent`), which the hint
  // under the field says.
  //
  // The Parent value is read back from the form: the reset effect above may
  // have replaced it in this same commit, after `watchedParentId` was read.
  useEffect(() => {
    if (mode !== "edit" || !ticket) return;
    const parentId = form.getValues("parentId") || "none";
    const currentParentId = ticket.parentTicketId ?? "none";
    const isNewParent = parentId !== currentParentId && parentId !== "none";
    // `reparent` describes the rendered Parent value; wait for the render
    // that carries this one.
    if (isNewParent && parentId !== watchedParentId) return;
    const target =
      parentId === currentParentId
        ? currentPlacement
        : isNewParent
          ? reparent.placement
          : unparent.placement;
    // The parent's placement is still loading; the save waits for it too.
    if (target === undefined) return;
    const last = placementFollowRef.current ?? {
      parentId: currentParentId,
      placement: currentPlacement,
    };
    const parentChanged = last.parentId !== parentId;
    if (!parentChanged && last.placement === target) return;
    const value = form.getValues("sprintId");
    const follows = value === last.placement || (parentChanged && isNewParent);
    placementFollowRef.current = { parentId, placement: follows ? target : last.placement };
    if (follows && value !== target) {
      form.setValue("sprintId", target, { shouldDirty: true, shouldValidate: true });
    }
  }, [mode, ticket, watchedParentId, currentPlacement, unparent.placement, reparent.placement, form]);

  // Without the parent's placement the form falls back to the ticket's own,
  // which may not be where the user saw it, so say that the parent could not
  // be read. A new parent that cannot be read leaves the field as it is, and
  // the save leaves placing the ticket to the backend.
  const parentPlacementError = isUnparenting
    ? unparent.error
    : isReparenting
      ? reparent.error
      : null;
  useEffect(() => {
    if (!parentPlacementError) return;
    devError("Parent ticket load failed:", parentPlacementError);
    toast.error(t("kaizen.tickets.messages.ticketLoadFailed"));
  }, [parentPlacementError, t]);

  // A status the backend would overwrite (see `resolveAllowedStatuses`) is
  // replaced the moment the form reaches that state, so the Status field
  // shows what will actually be saved. `TicketFormFields` narrows the Status
  // options with the same rule, so this cannot fight the user's next choice.
  // It reads the values back from the form because the Placement effect
  // above may have changed the placement in this same commit.
  useEffect(() => {
    const sprintIdValue = form.getValues("sprintId");
    const parentIdValue = form.getValues("parentId");
    const allowedStatuses = resolveAllowedStatuses({
      ticket,
      sprintId: sprintIdValue,
      parentId: parentIdValue,
      unparentPlacement: unparent.placement,
      detachesFromParent: resolveDetachesFromParent({
        ticket,
        sprintId: sprintIdValue,
        parentId: parentIdValue,
        unparentPlacement: unparent.placement,
        reparentPlacement: reparent.placement,
      }),
    });
    const status = form.getValues("status");
    if (!allowedStatuses || (status !== undefined && allowedStatuses.includes(status))) return;
    form.setValue("status", allowedStatuses[0], {
      shouldDirty: true,
      shouldValidate: true,
    });
  }, [watchedSprintId, watchedParentId, ticket, unparent.placement, reparent.placement, form]);

  // ── Submit gate while the board's backlog id is in flight ─────────
  // Create needs the id unconditionally: submitting without it would POST a
  // ticket with no placement at all, which the backend rejects with
  // VAL_TICKET_PLACEMENT.
  //
  // Edit needs it only for a save that MOVES the ticket into the backlog.
  // `runPlacementMove` reads a missing `boardBacklogId` as "this board has no
  // backlog" and aborts with the `backlogUnavailable` toast -- true once the
  // query has settled, a lie while it is still loading. Gate that one case
  // instead, so the toast keeps its meaning and every other edit (a title
  // typo on a sprint ticket) stays saveable during the load.
  //
  // A save that detaches a sub-ticket also waits for the parent's placement,
  // which decides whether the Placement field holds a move at all. A save
  // that attaches the ticket to a new parent waits for that parent's, which
  // decides whether the ticket is attached or left standalone.
  const isBacklogPending =
    (isBacklogLoading &&
      (mode === "create" ||
        (watchedSprintId === TICKET_BACKLOG_SPRINT_VALUE &&
          watchedSprintId !== currentPlacement))) ||
    (isUnparenting && unparent.isPending) ||
    (isReparenting && reparent.isPending);

  // Create mode: the Sprint field is seeded from the `t_sprint` route param,
  // which names a sprint on the board the form was OPENED from. Switching the
  // Board select leaves that id selected while it is absent from the new
  // board's options, so the field renders blank -- but submit still sends it,
  // and `createTicket` would quietly adopt the other board's sprint through
  // `sprintService.linkSprintToBoardIfNeeded`. Blank field, cross-board link:
  // exactly the silent mismatch the select appears to be warning about.
  //
  // So reset the placement to the backlog whenever the user actually CHANGES
  // board. The ref makes the first run a no-op, which is what keeps the
  // legitimate `t_sprint` seed alive through mount and through the open
  // transition (where `setSelectedBoardId` re-sets the same id).
  const lastSprintResetBoardRef = useRef<string | null>(null);
  useEffect(() => {
    if (mode !== "create") {
      lastSprintResetBoardRef.current = null;
      return;
    }
    const previousBoardId = lastSprintResetBoardRef.current;
    lastSprintResetBoardRef.current = selectedBoardId;
    if (previousBoardId === null || previousBoardId === selectedBoardId) return;
    form.setValue("sprintId", TICKET_BACKLOG_SPRINT_VALUE, {
      shouldDirty: true,
      shouldValidate: true,
    });
  }, [mode, selectedBoardId, form]);

  // ── Edit mode: moving the ticket to another board ─────────────────
  // The save moves the ticket when the Board field names another board than
  // its own. Picking another board first opens the move dialog, where the
  // user chooses the ticket's sprint or backlog on that board and sees what
  // else the move changes; the choice only lands in the form on confirm.
  const isBoardChange =
    mode === "edit" && !!ticketBoardId && !!selectedBoardId && selectedBoardId !== ticketBoardId;
  const [pendingBoardId, setPendingBoardId] = useState<string | null>(null);
  // Chosen in the move dialog: leave the sub-tickets on the ticket's own
  // board without a parent instead of taking them along.
  const [detachSubTicketsOnMove, setDetachSubTicketsOnMove] = useState(false);

  const requestBoardChange = useCallback(
    (nextBoardId: string) => {
      if (mode !== "edit" || !ticket || !ticketBoardId) {
        setSelectedBoardId(nextBoardId);
        return;
      }
      if (nextBoardId === selectedBoardId) return;
      if (nextBoardId === ticketBoardId) {
        // Back on the ticket's own board: nothing moves, so the fields show
        // where the ticket really is again.
        setSelectedBoardId(nextBoardId);
        setDetachSubTicketsOnMove(false);
        form.setValue("sprintId", currentPlacement, { shouldDirty: true, shouldValidate: true });
        form.setValue("projectId", ticket.project?.id ?? undefined, { shouldDirty: true });
        return;
      }
      setPendingBoardId(nextBoardId);
    },
    [mode, ticket, ticketBoardId, selectedBoardId, currentPlacement, form],
  );

  const confirmBoardChange = useCallback(
    (placement: string, detachSubTickets: boolean) => {
      if (!pendingBoardId) return;
      setSelectedBoardId(pendingBoardId);
      setPendingBoardId(null);
      setDetachSubTicketsOnMove(detachSubTickets);
      form.setValue("sprintId", placement, { shouldDirty: true, shouldValidate: true });
      // A project belongs to one board, and the move clears it on the server.
      form.setValue("projectId", undefined, { shouldDirty: true });
    },
    [pendingBoardId, form],
  );

  const cancelBoardChange = useCallback(() => setPendingBoardId(null), []);

  // A pending choice belongs to the ticket it was made for.
  useEffect(() => {
    setPendingBoardId(null);
    setDetachSubTicketsOnMove(false);
  }, [open, activeTicketId]);

  // ── Handlers ──────────────────────────────────────────────────────

  // Resolve effective board ID
  const effectiveBoardId =
    mode === "edit"
      ? (ticketBoardId ?? (ticket?.parentTicketId ? findTicketBoardId(ticket.parentTicketId) : boardId))
      : (selectedBoardId || boardId);

  const handleFormSubmit = async (data: TicketFormValues) => {
    // A second submit while a save runs, or after this form's create
    // succeeded, is the same user action arriving twice, so it is dropped.
    // The first save reports the result.
    if (submitInFlightRef.current) return;
    if (mode === "create" && createCommittedRef.current) return;
    submitInFlightRef.current = true;
    setIsSaving(true);
    // A save is one user action, however many requests it takes, so its
    // ticket mutations run batched: none of them toasts on success or
    // resyncs. The save shows one result toast itself, and resyncs once in
    // the `finally` below if it sent any ticket request.
    let sentTicketRequest = false;
    try {
      if (mode === "create") {
        // ── Attachment pre-flight ──────────────────────────────────
        // The server rejects duplicate content for this entity type across
        // the whole organization, not just this ticket. Because the ticket
        // is created BEFORE its attachments upload, discovering that after
        // the fact leaves a saved ticket whose files failed and are gone.
        // So check first and refuse to create — nothing is half-done.
        //
        // Fails OPEN on transport/status errors (same as the kaizen CLI):
        // "could not determine" must not block a legitimate create. Only a
        // definite `exists: true` aborts.
        if (pendingAttachments.length > 0) {
          const duplicates: string[] = [];
          for (const file of pendingAttachments) {
            try {
              if (await checkAttachmentExists(StorageEntityType.TICKET, file)) {
                duplicates.push(file.name);
              }
            } catch (preflightError) {
              devError(
                `Attachment duplicate pre-check failed for ${file.name}; continuing:`,
                preflightError,
              );
            }
          }
          if (duplicates.length > 0) {
            toast.error(
              t("attachments.messages.duplicateContent", {
                names: duplicates.join(", "),
              }),
            );
            return;
          }
        }

        const parentId =
          data.parentId && data.parentId !== "none" ? data.parentId : activeParentId;
        const targetBoardId = selectedBoardId || boardId;

        // Placement (sprintId vs backlogId) is mutually exclusive on the
        // backend (`VAL_TICKET_PLACEMENT`). A sub-ticket omits BOTH and lets
        // the backend derive placement from the parent: forwarding either
        // field risks `parent-in-sprint + request-has-backlog` -> both ids
        // non-null after the backend's parent override -> validation rejects.
        const isSubTicket = !!parentId;
        // The Sprint field is the source of truth in create mode as well now.
        // The `sprintId` / `backlogId` props only seeded its default value
        // (see `formValues`); what the user left the field at is what places
        // the ticket.
        const wantsBacklog =
          !data.sprintId || data.sprintId === TICKET_BACKLOG_SPRINT_VALUE;
        const placementSprintId =
          isSubTicket || wantsBacklog ? undefined : data.sprintId;
        const placementBacklogId =
          isSubTicket || !wantsBacklog ? undefined : resolvedBacklogId;

        // The backend demands EXACTLY one placement on create
        // (`ensureTicketHasCorrectPlacement`); only a sub-ticket is excused,
        // because it inherits the parent's. If the board's backlog id never
        // resolved there is nothing to place the ticket in, so say so rather
        // than POST a request that is certain to be rejected.
        if (!isSubTicket && !placementSprintId && !placementBacklogId) {
          toast.error(t("kaizen.tickets.messages.backlogUnavailable"));
          return;
        }

        // A top-level backlog ticket is always TODO server-side
        // (TicketStatusNormalizer), so never submit a status it would
        // overwrite. The coercion effect above has already moved the field;
        // this is the same second half of the contract the edit path has.
        const submittedStatus =
          wantsBacklog && !isSubTicket ? TicketStatus.TODO : data.status;

        let incidentClose: IncidentCloseValues | undefined;
        if (needsIncidentCloseTexts(data.type, undefined, submittedStatus, null)) {
          const values = await askIncidentCloseTexts();
          if (!values) return;
          incidentClose = values;
        }

        // 1. Create the ticket. Description still contains blob: URLs
        //    when there are pending images -- they're rewritten in step
        //    2 below. Sending blob: URLs through the POST is acceptable
        //    because they are valid strings and pass schema validation;
        //    the PUT that follows replaces them before any user can
        //    read the field back.
        //
        //    Every ticket request of this save is batched: the save shows
        //    one result toast and resyncs once, in the `finally` below.
        sentTicketRequest = true;
        const createResponse = await createTicketMutation.mutateAsync({
          boardId: targetBoardId,
          data: {
            title: data.title,
            description: data.description,
            type: data.type,
            priority: data.priority,
            status: submittedStatus,
            rca: incidentClose?.rca,
            lessonsLearned: incidentClose?.lessonsLearned,
            weight: data.weight,
            missionRank: data.missionRank,
            dueDate: data.dueDate ? data.dueDate.toISOString() : null,
            sprintId: placementSprintId,
            backlogId: placementBacklogId,
            assigneeIds: data.assigneeIds ?? [],
            reviewerIds: data.reviewerIds ?? [],
            labelIds: data.labelIds ?? [],
            parentTicketId: parentId ?? (data.parentId === "none" ? undefined : data.parentId),
            projectId: data.projectId,
            milestoneId:
              data.milestoneId && data.milestoneId !== TICKET_NO_MILESTONE_VALUE
                ? data.milestoneId
                : undefined,
          },
          batched: true,
        });

        const newTicketId = createResponse?.data?.id;
        // From here on the ticket exists, so this form must not create it
        // again, whatever the follow-up steps below do.
        createCommittedRef.current = true;
        setIsCreateCommitted(true);

        // A follow-up step resolves to null when it succeeded, or to the
        // reason it failed. It shows no toast itself: the save reports every
        // failed step in its one result toast below.
        type FollowUpFailure = string | null;
        const describeError = (error: unknown, fallback: string): string =>
          error instanceof Error && error.message ? error.message : fallback;

        // 2. Upload the pasted images and PUT the description with their
        //    Azure URLs.
        const commitCreatedImages = async (createdId: string): Promise<FollowUpFailure> => {
          let committedDescription: string;
          try {
            // Reconcile first: drop any pending entries whose blob URL
            // is no longer in the description (covers keyboard delete,
            // undo, and any other path that bypasses the trash button).
            const reconciled = reconcilePendingImages(
              data.description ?? "",
              pendingImages,
            );
            const result = await commitPendingImages({
              entityType: StorageEntityType.TICKET,
              entityId: createdId,
              description: data.description ?? "",
              pendingImages: reconciled,
              markUploaded: markPendingImageUploaded,
            });
            committedDescription = result.description;
          } catch (uploadError) {
            devError("Description image upload failed:", uploadError);
            return describeError(uploadError, t("attachments.messages.imageUploadError"));
          }
          if (committedDescription.length > 10000) {
            devError("Description too long after image upload:", committedDescription.length);
            return t("ticket.markdown.descriptionTooLong");
          }
          try {
            await updateTicketMutation.mutateAsync({
              boardId: targetBoardId,
              ticketId: createdId,
              data: { description: committedDescription },
              batched: true,
              silentError: true,
            });
          } catch (updateError) {
            devError("Description update after image upload failed:", updateError);
            return describeError(updateError, t("attachments.messages.imageUploadError"));
          }
          return null;
        };

        // 3. Upload the files staged by the create form. They could not be
        //    uploaded earlier: the endpoint needs a real entity UUID, which
        //    only exists now. This is the second half of the "single step"
        //    the user sees. The form's upload mutation shows no error toast.
        const uploadStagedAttachments = async (createdId: string): Promise<FollowUpFailure> => {
          try {
            await uploadAttachmentsMutation.mutateAsync({
              entityType: StorageEntityType.TICKET,
              entityId: createdId,
              files: pendingAttachments,
              silentSuccess: true,
            });
            return null;
          } catch (attachmentError) {
            devError("Staged attachment upload failed:", attachmentError);
            return describeError(attachmentError, t("attachments.messages.uploadError"));
          }
        };

        // Both steps need only the new ticket's id, so they run side by side.
        // A failure in either does NOT keep the form open: the ticket already
        // exists, so pressing Create again would make a duplicate. The result
        // toast names what is missing, and the user adds it from the ticket
        // itself -- never a silent drop.
        const [imageFailure, attachmentFailure] = await Promise.all([
          newTicketId && hasPending ? commitCreatedImages(newTicketId) : Promise.resolve(null),
          newTicketId && pendingAttachments.length > 0
            ? uploadStagedAttachments(newTicketId)
            : Promise.resolve(null),
        ]);
        // Both uploads may have added attachments, the image upload through
        // the raw API function, which leaves the list cache untouched.
        if (newTicketId && (hasPending || pendingAttachments.length > 0)) {
          resetAttachmentCache(newTicketId);
        }

        setPendingAttachments([]);
        clearPendingImages();

        // One result toast for the save. When a follow-up step failed, it is
        // a warning that the ticket exists without what that step adds, with
        // the reasons as its description.
        const failureDetails = [imageFailure, attachmentFailure].filter(
          (detail): detail is string => detail !== null,
        );
        if (failureDetails.length === 0) {
          toast.success(t("kaizen.tickets.messages.created"));
        } else {
          const title =
            imageFailure !== null && attachmentFailure !== null
              ? t("kaizen.tickets.messages.createdImagesAndAttachmentsFailed")
              : imageFailure !== null
                ? t("kaizen.tickets.messages.createdImagesFailed")
                : t("kaizen.tickets.messages.createdAttachmentsFailed");
          toast.warning(title, { description: failureDetails.join(" ") });
        }

        // If we were in internal create mode (sub-ticket creation), go back to edit mode
        if (internalCreateMode && internalParentId) {
          setInternalCreateMode(false);
          setInternalTicketId(internalParentId);
          setInternalParentId(null);
        } else {
          onOpenChange(false);
        }
        onTicketCreated?.();
      } else {
        // Edit mode
        if (!activeTicketId) return;
        // A sub-ticket is always placed with its parent, so a Placement away
        // from the parent in the Parent field leaves the ticket standalone
        // there, as the hint under the field says. The save then treats the
        // Parent field as "none": it never attaches the ticket to a new parent
        // only for the move to detach it again.
        const detachesFromParent = resolveDetachesFromParent({
          ticket,
          sprintId: data.sprintId,
          parentId: data.parentId,
          unparentPlacement: unparent.placement,
          reparentPlacement: reparent.placement,
        });
        const parentTicketId =
          data.parentId && data.parentId !== "none" && !detachesFromParent
            ? data.parentId
            : undefined;

        // The PUT carries `parentTicketId` ONLY when the parent actually
        // changed. `TicketServiceImpl.updateTicket` re-derives placement from
        // that field whenever it is non-null -- the ticket takes its new
        // parent's sprint or backlog -- so a save that merely repeats the
        // current parent would drag the ticket back onto the parent's
        // placement. Omitting the field leaves the parent as it is.
        //
        // A re-parent places the ticket with its new parent on the server, and
        // the Placement field already holds that placement (it follows a new
        // parent), so a re-parent sends no move.
        //
        // Clearing a parent ("none") cannot be said with `parentTicketId`,
        // because an omitted field means "unchanged", so it maps to
        // `clearParent` instead. It is sent only when the ticket has a parent
        // to clear. The backend then gives the ticket its parent's placement,
        // and resets its status to TODO when that placement is a backlog.
        const submittedParentId =
          parentTicketId === (ticket?.parentTicketId ?? undefined)
            ? undefined
            : parentTicketId;
        const clearParent = !parentTicketId && !!ticket?.parentTicketId ? true : undefined;

        // The milestone is sent only when it changed: re-sending the current
        // one would have the server re-check that it may still be attached,
        // which fails for a CLOSED or no longer related milestone the user
        // did not touch. "None" maps to `clearMilestone`, because an omitted
        // `milestoneId` means "unchanged".
        const currentMilestoneId = ticket?.milestone?.id;
        const wantsNoMilestone = data.milestoneId === TICKET_NO_MILESTONE_VALUE;
        const submittedMilestoneId =
          data.milestoneId && !wantsNoMilestone && data.milestoneId !== currentMilestoneId
            ? data.milestoneId
            : undefined;
        const clearMilestone = wantsNoMilestone && !!currentMilestoneId ? true : undefined;

        // A top-level ticket sitting in the backlog is always TODO on the
        // backend, so never submit a status it would overwrite. The form
        // already shows TODO by this point (see the coercion effect above);
        // this is the second half of that contract, for the case where the
        // value was changed programmatically between render and submit.
        // Sub-tickets keep their status -- the backend does not reset theirs.
        // A ticket this save detaches is top-level afterwards, so the rule
        // applies to it.
        const wantsBacklog = data.sprintId === TICKET_BACKLOG_SPRINT_VALUE;
        const isSubTicketSubmit = !!parentTicketId;
        const isUnparentSubmit = !!clearParent;
        const submittedStatus =
          wantsBacklog && !isSubTicketSubmit ? TicketStatus.TODO : data.status;

        // Asked before any image upload, so a cancel leaves nothing changed.
        let incidentClose: IncidentCloseValues | undefined;
        if (needsIncidentCloseTexts(data.type, ticket?.status, submittedStatus, ticket)) {
          const values = await askIncidentCloseTexts();
          if (!values) return;
          incidentClose = values;
        }

        // 1. Upload any pending images first so the PATCH carries the
        //    resolved description (real Azure URLs + data-attachment-id
        //    on the <img> nodes). On upload failure, surface the toast
        //    and bail BEFORE touching the ticket -- the user can retry,
        //    and the next attempt will skip already-uploaded images via
        //    the PendingImage.uploadedAttachmentId short-circuit added
        //    in commitPendingImages.
        let resolvedDescription = data.description ?? "";
        if (hasPending) {
          try {
            // Reconcile first: see create-mode comment above.
            const reconciled = reconcilePendingImages(
              resolvedDescription,
              pendingImages,
            );
            const result = await commitPendingImages({
              entityType: StorageEntityType.TICKET,
              entityId: activeTicketId,
              description: resolvedDescription,
              pendingImages: reconciled,
              markUploaded: markPendingImageUploaded,
            });
            resolvedDescription = result.description;
            if (resolvedDescription.length > 10000) {
              toast.error(t("ticket.markdown.descriptionTooLong"));
              return;
            }
          } catch (uploadError) {
            const message =
              uploadError instanceof Error
                ? uploadError.message
                : t("attachments.messages.imageUploadError");
            toast.error(message);
            return;
          }
        }

        // ── Placement move ─────────────────────────────────────────
        // The update endpoint cannot relocate a ticket (TicketUpdateRequest
        // carries no sprintId/backlogId), so a placement change goes through
        // the same `POST .../move` the table's row action uses -- which also
        // means the same optimistic list update as the list behind the dialog.
        //
        // A detached ticket lands on its parent's placement, so that is what
        // the field is compared with when this save detaches it. A re-parent
        // is placed by the update itself.
        const placementBaseline = isUnparentSubmit ? unparent.placement : currentPlacement;
        // A move to another board places the ticket itself, see below.
        const placementChanged =
          !isBoardChange &&
          !submittedParentId &&
          !!data.sprintId &&
          data.sprintId !== placementBaseline;
        const movesAfterUpdate = isSubTicketSubmit || isUnparentSubmit;
        // Detaching and moving in one save cannot write a new status with the
        // update: the ticket is still on its parent's placement then, and in a
        // backlog the backend refuses any explicit status other than TODO. The
        // update sends no status instead, so the ticket keeps its own (reset
        // to TODO in a backlog), and the status is written after the move.
        const defersStatus = isUnparentSubmit && placementChanged && !!ticket;

        /**
         * Fires the move and resolves to the moved ticket's status. Resolves
         * to null when the move could not even be ATTEMPTED, after its own
         * error toast, and the caller then stops the save. A move the server
         * REJECTS throws instead, and `useMoveTicket.onError` is what the user
         * sees.
         */
        const runPlacementMove = async (): Promise<{ status: TicketStatus | undefined } | null> => {
          if (wantsBacklog && !boardBacklogId) {
            // The board's backlog id never resolved, so there is nothing to
            // move INTO. Say so instead of dropping the move on the floor.
            toast.error(t("kaizen.tickets.messages.backlogUnavailable"));
            return null;
          }
          sentTicketRequest = true;
          const moveResponse = await moveTicketMutation.mutateAsync({
            boardId: effectiveBoardId,
            ticketId: activeTicketId,
            data: wantsBacklog
              ? { backlogId: boardBacklogId }
              : { sprintId: data.sprintId },
            batched: true,
          });
          return { status: moveResponse?.data?.status };
        };

        // Move BEFORE the field PATCH for a TOP-LEVEL ticket. Both orderings
        // are forced, in opposite directions, and getting this wrong loses
        // data silently in both:
        //
        //   Top-level, leaving the backlog: `TicketStatusNormalizer` resets
        //   the status of any top-level ticket saved without a sprint. PATCH
        //   first and the status lands while the ticket is STILL backlog
        //   placed, so the normalizer rewrites it to TODO and the later move
        //   carries that TODO into the sprint -- the status the form accepted
        //   is thrown away, and the backend only logs it. Moving first puts
        //   the ticket where the status is legal before the status is written.
        //
        //   Top-level, bound FOR the backlog: `submittedStatus` is coerced to
        //   TODO for this direction. PATCH first and a move that never runs
        //   leaves that TODO written onto a ticket still sitting in its
        //   sprint, where the normalizer does not apply -- a DONE ticket is
        //   silently downgraded by a save that visibly failed. Moving first
        //   means an unavailable backlog aborts the save before any status is
        //   persisted.
        //
        // A sub-ticket, and a ticket this save detaches, are the opposite case
        // and keep the old order: see the step 3 comment below.
        if (placementChanged && !movesAfterUpdate) {
          if (!(await runPlacementMove())) return;
        }

        // A move to another board runs before the field update for the same
        // reason: it places the ticket in the target board's sprint or
        // backlog, so the status is written where it is legal. It gives the
        // ticket a new key, takes every sub-ticket along unless the user chose
        // to leave them on the old board without a parent, clears the project
        // and drops a milestone the target board does not relate to. The
        // update then goes to the target board, which now owns the ticket.
        let milestoneClearedKeys: string[] = [];
        if (isBoardChange) {
          sentTicketRequest = true;
          const moveToBacklog = wantsBacklog || !data.sprintId;
          const boardMoveResponse = await bulkMoveTicketsMutation.mutateAsync({
            boardId: effectiveBoardId,
            data: {
              ticketIds: [activeTicketId],
              action: moveToBacklog ? "MOVE_TO_BACKLOG" : "MOVE_TO_SPRINT",
              targetSprintId: moveToBacklog ? undefined : data.sprintId,
              targetBoardId: selectedBoardId,
              detachSubTickets: detachSubTicketsOnMove || undefined,
            },
            batched: true,
          });
          milestoneClearedKeys = boardMoveResponse?.data?.milestoneClearedTicketKeys ?? [];
        }
        const savedBoardId = isBoardChange ? selectedBoardId : effectiveBoardId;

        // 2. PATCH the ticket with the resolved description. A rejection
        //    throws to the outer catch; `useUpdateTicket.onError` toasts it.
        sentTicketRequest = true;
        const updateResponse = await updateTicketMutation.mutateAsync({
          boardId: savedBoardId,
          ticketId: activeTicketId,
          data: {
            title: data.title,
            description: resolvedDescription,
            type: data.type,
            priority: data.priority,
            status: defersStatus ? undefined : submittedStatus,
            // Saved with this update even when the status follows after the
            // move, so the incident already holds them when it is closed.
            rca: incidentClose?.rca,
            lessonsLearned: incidentClose?.lessonsLearned,
            percentage: data.percentage,
            weight: data.weight,
            missionRank: data.missionRank,
            dueDate: data.dueDate ? data.dueDate.toISOString() : null,
            assigneeIds: data.assigneeIds ?? [],
            reviewerIds: data.reviewerIds ?? [],
            labelIds: data.labelIds ?? [],
            parentTicketId: submittedParentId,
            clearParent,
            projectId: data.projectId,
            milestoneId: submittedMilestoneId,
            clearMilestone,
          },
          batched: true,
        });
        // The new description is saved, so a retry of this save must not
        // treat the attachments it removed as orphans a second time.
        const previousDescription = prevDescriptionRef.current;
        prevDescriptionRef.current = resolvedDescription;

        // 3. Placement move for a SUB-ticket that keeps its parent, run AFTER
        //    the field update. Such a move only puts the ticket back where its
        //    parent is (any other placement detaches it, see
        //    `detachesFromParent`), and the backend keeps it parented there.
        //    There is no status to protect in this direction:
        //    `TicketStatusNormalizer` exempts sub-tickets, so `submittedStatus`
        //    is never coerced for one.
        //
        //    A ticket this save detaches must move afterwards: on
        //    `clearParent` the backend puts it on its parent's placement,
        //    which would undo a move made first. Its status, held back from the
        //    update, is written once it is in a sprint, unless the move already
        //    left it there. A move into the backlog resets it to TODO, the only
        //    status the form allows there.
        //
        //    Resolves to false after the failure's own toast: the
        //    `backlogUnavailable` toast in `runPlacementMove`, or the mutation's
        //    `onError`. The rest of the edit stays saved.
        const runMoveAfterUpdate = async (): Promise<boolean> => {
          try {
            const moved = await runPlacementMove();
            if (!moved) return false;
            if (defersStatus && !wantsBacklog && moved.status !== data.status) {
              await updateTicketMutation.mutateAsync({
                boardId: effectiveBoardId,
                ticketId: activeTicketId,
                data: { status: data.status },
                batched: true,
              });
            }
            return true;
          } catch (moveError) {
            // `useMoveTicket.onError` or `useUpdateTicket.onError` has
            // already shown the error toast.
            devError("Placement move after ticket update failed:", moveError);
            return false;
          }
        };

        // 4. Best-effort orphan GC: any data-attachment-id present in the
        //    previous description but absent from the resolved one is an
        //    attachment the user removed during this edit. We DELETE those to
        //    keep storage clean. A failure is logged and toasted, and does
        //    not keep the form open, because the ticket update has already
        //    succeeded by this point. It needs only the saved description,
        //    so it runs beside the move.
        const orphanIds = collectOrphanAttachmentIds(previousDescription, resolvedDescription);
        const collectOrphans = async (): Promise<boolean> => {
          if (orphanIds.size === 0) return true;
          const gc = await deleteAttachments(orphanIds);
          if (gc.failed.length === 0) return true;
          devError("Orphan attachment GC failed:", gc.failed);
          toast.error(t("attachments.messages.orphanGcFailed"));
          return false;
        };

        const [moveSucceeded, gcSucceeded] = await Promise.all([
          placementChanged && movesAfterUpdate ? runMoveAfterUpdate() : Promise.resolve(true),
          collectOrphans(),
        ]);

        // Both the GC deletes above and any commitPendingImages uploads
        // in this save bypassed the attachment mutation hooks, so the
        // cached list for this ticket is now wrong in both directions
        // (deleted blobs still present, new images missing).
        if (hasPending || orphanIds.size > 0) {
          resetAttachmentCache(activeTicketId);
        }

        // A failed move keeps the form open, with its pending images, so the
        // user can save the placement again.
        if (!moveSucceeded) return;

        clearPendingImages();
        // One result toast for the save: the success toast only when every
        // step succeeded; otherwise the failed step has shown its own.
        if (gcSucceeded) {
          const movedKey = updateResponse?.data?.key;
          if (!isBoardChange || !movedKey) {
            toast.success(t("kaizen.tickets.messages.updated"));
          } else if (milestoneClearedKeys.length > 0) {
            toast.warning(t("kaizen.tickets.messages.movedToBoard", { key: movedKey }), {
              description: t("kaizen.tickets.messages.milestoneCleared", {
                count: milestoneClearedKeys.length,
                keys: milestoneClearedKeys.join(", "),
              }),
            });
          } else {
            toast.success(t("kaizen.tickets.messages.movedToBoard", { key: movedKey }));
          }
        }

        onOpenChange(false);
        onTicketUpdated?.();
      }
    } catch (error) {
      // Only a rejected ticket mutation reaches this catch, and its hook's
      // `onError` has already shown the error toast; a create refused for its
      // missing rank also marks the field here, so the user sees what to fix.
      devError("Failed to save ticket:", error);
      if (mode === "create" && isMissionRankRejected(error)) {
        form.setError("missionRank", { message: "ticket.fields.missionRankRequired" }, { shouldFocus: true });
      }
    } finally {
      if (sentTicketRequest) resyncTicketAction(queryClient);
      submitInFlightRef.current = false;
      setIsSaving(false);
    }
  };

  /**
   * Creates a sub-ticket from the quick-add row. Resolves to true when it was
   * created. A second call while one is in flight is a repeated Enter on the
   * same title, so it resolves to false without a request. A rejection
   * reaches the caller after `useCreateTicket.onError` has shown its toast,
   * and the caller keeps the title so the user can retry.
   */
  const handleCreateSubTicket = async (title: string): Promise<boolean> => {
    if (!ticket?.id || subTicketCreateInFlightRef.current) return false;
    subTicketCreateInFlightRef.current = true;
    try {
      // Sub-tickets inherit placement from the parent server-side.
      // Forwarding sprintId/backlogId here could collide with the
      // backend's parent-derived placement and trip
      // `VAL_TICKET_PLACEMENT`.
      await createTicketMutation.mutateAsync({
        boardId: effectiveBoardId,
        data: {
          title,
          type: TicketType.FEATURE,
          priority: TicketPriority.MEDIUM,
          status: TicketStatus.TODO,
          missionRank: MissionRank.M,
          parentTicketId: ticket.id,
        },
      });
      return true;
    } finally {
      subTicketCreateInFlightRef.current = false;
    }
  };

  const handleDeleteSubTicket = async (subTicketId: string) => {
    await deleteTicketMutation.mutateAsync({ boardId: effectiveBoardId, ticketId: subTicketId });
  };

  const handleDeleteTicket = async () => {
    if (!ticket?.id) return;
    await deleteTicketMutation.mutateAsync({ boardId: effectiveBoardId, ticketId: ticket.id });
  };

  const handleUpdateSubTicketTitle = async (subTicketId: string, title: string) => {
    await updateTicketMutation.mutateAsync({
      boardId: effectiveBoardId,
      ticketId: subTicketId,
      data: { title },
    });
  };

  const handleToggleSubTicketComplete = async (subTicketId: string, completed: boolean) => {
    const subTicket = subTickets.find((candidate) => candidate.id === subTicketId);
    if (!subTicket) return;
    await guardSubTicketStatusChange(
      subTicket,
      completed ? TicketStatus.DONE : TicketStatus.TODO,
      effectiveBoardId,
    );
  };

  const incidentCloseDraftKey = mode === "edit" && activeTicketId ? activeTicketId : "new";
  const incidentCloseDialogProps: IncidentCloseDialogProps = {
    open: isIncidentClosePromptOpen,
    onOpenChange: (nextOpen) => {
      if (!nextOpen) settleIncidentClosePrompt(null);
    },
    boardId: effectiveBoardId,
    ticketId: mode === "edit" && activeTicketId ? activeTicketId : undefined,
    defaultValues:
      incidentCloseDraft?.key === incidentCloseDraftKey ? incidentCloseDraft.values : undefined,
    onConfirm: (values) => {
      setIncidentCloseDraft({ key: incidentCloseDraftKey, values });
      settleIncidentClosePrompt(values);
    },
  };

  // The optimistic reorder mirrors the server's `displayOrder` shift, so a
  // second drop would already send a correct `order`. The sub-ticket list
  // still allows one reorder at a time and waits for the server order to be
  // back in the cache, so a drop never builds on an order a failed request
  // rolls back. The list disables its handles for as long as this is true.
  const isSubTicketReorderLocked = updateTicketOrderMutation.isPending || isTicketDetailFetching;

  const handleReorderSubTicket = (activeId: string, overId: string) => {
    if (activeId === overId) return;
    const over = subTickets.find((subTicket) => subTicket.id === overId);
    if (!over) return;
    // Reached only by a drag that was already under way when the lock began.
    if (isSubTicketReorderLocked) {
      toast.error(t("ticket.subTickets.reorderBusy"));
      return;
    }
    // `displayOrder` is one sequence per board, so the order of a sibling on
    // another board says nothing about a position on the dragged ticket's board.
    const activeBoardId = findTicketBoardId(activeId);
    if (findTicketBoardId(overId) !== activeBoardId) {
      toast.error(t("ticket.subTickets.reorderCrossBoard"));
      return;
    }
    // Failure handling lives in the mutation: it rolls the cache back and toasts.
    updateTicketOrderMutation.mutate({
      boardId: activeBoardId,
      ticketId: activeId,
      targetTicketId: overId,
      data: { order: over.displayOrder },
    });
  };

  const handleRequestCreateSubTicket = useCallback(() => {
    if (!ticket?.id) return;
    // A new create in this form, so the previous one no longer blocks it.
    createCommittedRef.current = false;
    setIsCreateCommitted(false);
    setInternalParentId(ticket.id);
    setInternalCreateMode(true);
  }, [ticket?.id]);

  const handleOpenSubTicket = useCallback(
    (subTicket: TicketListResponse) => {
      // Preferred path: route navigation through the page's dialog/URL so
      // `?id=` stays the single source of truth and browser history works.
      if (onNavigateToTicket) {
        onNavigateToTicket(subTicket.id, subTicket.board?.id);
        return;
      }
      // Fallback for callers that don't wire URL-driven navigation: keep
      // the legacy form-internal navigation.
      setInternalTicketId(subTicket.id);
      setInternalCreateMode(false);
      setInternalParentId(null);
    },
    [onNavigateToTicket],
  );

  const handleFileUpload = async (
    file: File,
  ): Promise<{ url: string; isImage: boolean; filename: string }> => {
    const uploadData = new FormData();
    uploadData.append("file", file);
    const response = await fetch("/api/upload", {
      method: "POST",
      body: uploadData,
    });
    if (!response.ok) throw new Error("Upload failed");
    const data = await response.json();
    return {
      url: data.url,
      isImage: data.isImage,
      filename: data.filename ?? file.name,
    };
  };

  return {
    // State
    mode,
    /**
     * True while a save runs, and in create mode from the moment the ticket
     * exists until the form closes, so Create cannot be pressed again.
     */
    isSaving: isSaving || (mode === "create" && isCreateCommitted),
    ticket,
    isTicketLoading,
    subTickets,
    activeTicketId,
    /**
     * Parent ticket for create mode. Non-null when the user hit "+ Add" on
     * the sub-ticket list, which flips the form into an INTERNAL create mode
     * the url does not reflect (`ticketId` still points at the parent).
     * Callers that build urls from form state must branch on this.
     */
    activeParentId,
    /** Files staged by the create form, uploaded once the ticket exists. */
    pendingAttachments,
    setPendingAttachments,
    /**
     * True while the description holds images that have been dropped/pasted
     * but not yet uploaded. Their `blob:` URLs and File handles are bound to
     * THIS document, so any hard navigation destroys them.
     */
    hasPendingImages: hasPending,

    // Board
    selectedBoardId,
    setSelectedBoardId,
    hierarchicalBoards,
    viewerBoardIds,
    currentBoard: currentBoard ?? null,
    isRelatedBoardsLoading: hasBoardContext ? (isRelatedBoardsLoading || isBoardTreeLoading) : isBoardTreeLoading,

    // Users & parent tickets
    availableUsers,
    availableParentTickets,

    // Form
    form,

    // Mutations state
    isCreatingSubTicket: createTicketMutation.isPending,
    isDeleting: deleteTicketMutation.isPending,
    /** True while a sub-ticket reorder is saving or the ticket detail refetches. */
    isSubTicketReorderLocked,

    // Incident close dialogs: the save's prompt and the sub-ticket toggle's
    incidentCloseDialogProps,
    subTicketIncidentCloseDialogProps,

    // Handlers
    handleFormSubmit,
    handleDeleteTicket,
    handleCreateSubTicket,
    handleDeleteSubTicket,
    handleUpdateSubTicketTitle,
    handleToggleSubTicketComplete,
    handleReorderSubTicket,
    handleRequestCreateSubTicket,
    handleOpenSubTicket,
    handleFileUpload,
    /**
     * Registers a dropped/pasted/picked image with the pending-images
     * map so it can be uploaded when the user submits the form. Forward
     * this to `<MarkdownEditor onImageDrop={...}>` from the rendering
     * component. Returns the generated localId (currently unused by the
     * editor, future-proofed for correlation).
     */
    addPendingImage,
    /**
     * Forwarded to `<MarkdownEditor onImageDelete={...}>` so the trash-
     * button NodeView can eagerly drop the matching pending-images
     * entry (and revoke its blob URL) when the user removes an unsaved
     * image. Saved (Azure-URL) images are no-ops here -- they're cleaned
     * up by the orphan-GC step on save.
     */
    removePendingImageBySrc,
    isBacklogPending,
    /** Board change in edit mode: the move dialog's state and its outcomes. */
    boardMove: {
      requestBoardChange,
      pendingBoardId,
      confirmBoardChange,
      cancelBoardChange,
    },
  };
}
