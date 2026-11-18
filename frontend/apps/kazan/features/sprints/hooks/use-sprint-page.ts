"use client";

import { useState, useMemo, useCallback, useEffect } from "react";
import { SprintStatus, type SprintCompleteRequest } from "../schemas";
import { useParams, useSearchParams } from "next/navigation";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import type { ViewMode } from "@/components/layout/view-toggle";
import { MissionRank, TicketStatus, TicketType, TicketPriority } from "@/types/enums";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { useBaselineTicketFilters } from "@/lib/hooks/use-ticket-filters";
import {
  useSprints,
  useCreateSprint,
  useStartSprint,
  useSprintStartPreview,
  useCompleteSprint,
  useUpdateSprint,
  useDeleteSprint,
  SPRINT_ACTIVE_SPRINT_EXISTS_CODE,
} from "./use-sprints";
import { isApiError } from "@oba/shared/api/axios";
import { toast } from "sonner";
import type { SprintUpdateRequest } from "../schemas";
import type { CreateSprintData } from "../components/create-sprint-dialog";
import type { TicketRelocationAction } from "@/features/tickets/schemas";
import {
  useCachedBoard,
  useCachedChildBoards,
  useCachedDescendantBoards,
} from "@/features/boards/hooks/use-boards";
import type { BoardFilterOption } from "@/features/tickets/components/ticket-filter-panel";
import {
  useTickets,
  useCreateTicket,
  useMoveTicket,
  useDeleteTicket,
  useBulkMoveTickets,
} from "@/features/tickets/hooks/use-tickets";
import {
  useIncidentCloseGuard,
  type IncidentCloseGuardTicket,
} from "@/features/tickets/hooks/use-incident-close-guard";
import {
  useBulkDeleteTickets,
  type BulkDeleteTicketTarget,
} from "@/features/tickets/hooks/use-bulk-delete-tickets";
import type { TicketBulkMoveRequest } from "@/features/tickets/schemas";
import type { SubTicketMoveChoice } from "@/features/tickets/components/sub-ticket-choice";
import { useIncludeAllBoards } from "@/components/ui/include-all-boards-toggle";
import { useBoardPreferences } from "@/hooks/use-board-preferences";
import { useBoardRoleContext } from "@/features/boards/providers/board-role-provider";
import { ticketKeyOrId, useTicketDialogMachine } from "@/hooks/use-ticket-dialog-machine";
import { useLegacyTicketDeepLink } from "@/hooks/use-ticket-deep-link";
import { useCopyTicketLink } from "@/hooks/use-copy-ticket-link";
import { mapChildBoards } from "@/lib/utils/map-child-boards";
import { getDaysRemaining } from "@/features/sprints/lib/sprint-dates";
import { getBacklog } from "@/features/backlog/api";
import { BOARD_TAB, boardPath, sprintPath } from "@/features/boards/lib/board-route";
import { defaultSprint } from "@/features/sprints/lib/sprint-selection";

// ── Hook ────────────────────────────────────────────────────────────

export type SprintPageState = ReturnType<typeof useSprintPage>;

export function useSprintPage(boardId: string) {
  const router = useRouter();
  const searchParams = useSearchParams();
  // `/board/<board-slug>/<sprint-slug>` names the sprint shown; on
  // `/board/<board-slug>/sprint` (a board without sprints) there is none.
  const { boardSlug, sprintSlug: routeSprintSlug } = useParams<{
    boardSlug: string;
    sprintSlug?: string;
  }>();

  // ── Simple UI state ──────────────────────────────────────────────
  const [viewMode, setViewMode] = useState<ViewMode>("kanban");
  const [includeAllBoards, setIncludeAllBoards] = useIncludeAllBoards(true);

  // ── Dialog state machine ─────────────────────────────────────────
  // Ticket create/edit is a route now; only the sprint dialog is local.
  const { currentUserRole, canManage } = useBoardRoleContext();
  const {
    openCreateTicketDialogIn,
    openCreateSubTicketDialog,
    openEditTicketDialog,
    openCreateSprintDialog,
    closeDialog,
    showCreateSprintDialog,
  } = useTicketDialogMachine({ currentUserRole });

  // Backwards compatibility for links shared as `?id=<TICKET-KEY>`:
  // resolve the key and forward to the real ticket route.
  useLegacyTicketDeepLink();

  const copyTicketLink = useCopyTicketLink();

  // ── Board Preferences ──────────────────────────────────────────────
  const boardPrefs = useBoardPreferences(boardId);

  // ── Filters (URL-synced via nuqs, relative to the saved filters) ──
  // Unknown until the user is loaded; an empty baseline when nothing is saved.
  const filterBaseline = useMemo(
    () => (boardPrefs.isMeFetched ? (boardPrefs.savedFilters ?? {}) : null),
    [boardPrefs.isMeFetched, boardPrefs.savedFilters]
  );
  const {
    filters,
    setFilters,
    clearFilters,
    resetToBaseline,
    hasChanges: hasFilterChanges,
    activeFilterCount,
    isReady: filtersReady,
  } = useBaselineTicketFilters(filterBaseline);

  // Without child boards the Board filter is hidden and not sent, so a board
  // selection left in the URL would count as an active filter the user can
  // neither see nor change; turning child boards off drops it.
  const handleIncludeAllBoardsChange = useCallback(
    (value: boolean) => {
      setIncludeAllBoards(value);
      if (!value) setFilters({ boardIds: null });
    },
    [setIncludeAllBoards, setFilters],
  );

  // ── Data Fetching ────────────────────────────────────────────────
  const { data: sprintsResponse, isLoading: sprintsLoading } = useSprints(boardId);
  const sprints = useMemo(() => sprintsResponse?.data ?? [], [sprintsResponse?.data]);

  const { data: childBoardsData } = useCachedChildBoards(boardId);
  const childBoards = useMemo(() => mapChildBoards(childBoardsData), [childBoardsData]);

  // The Board filter offers the boards this list spans: the current board and
  // every board below it at any depth, and only while child boards are
  // included, because without them the list covers this board alone.
  const { data: descendantBoards } = useCachedDescendantBoards(boardId);
  const { data: currentBoard } = useCachedBoard(boardId);
  const boardOptions = useMemo((): BoardFilterOption[] => {
    if (!includeAllBoards || descendantBoards.length === 0) return [];
    const current: BoardFilterOption[] = currentBoard
      ? [
          {
            id: currentBoard.id,
            name: currentBoard.name,
            prefix: currentBoard.prefix,
            color: currentBoard.color ?? "",
          },
        ]
      : [];
    return [...current, ...descendantBoards];
  }, [includeAllBoards, descendantBoards, currentBoard]);
  const hasChildBoards = childBoards.length > 0;

  // ── Derived Sprint ──────────────────────────────────────────────

  /**
   * Replaces the URL with `path` and keeps the query, so the filters and an
   * open ticket dialog survive a change of sprint.
   */
  const replaceKeepingQuery = useCallback(
    (path: string) => {
      const query = searchParams.toString();
      router.replace((query ? `${path}?${query}` : path) as "/home", { scroll: false });
    },
    [router, searchParams]
  );

  // A rename changes the sprint's slug while its page is open. The sprint
  // last shown is remembered by id, so the view keeps it and the URL follows
  // it to the new slug instead of losing the selection.
  const [shownSprintId, setShownSprintId] = useState<string | null>(null);

  const currentSprint = useMemo(() => {
    if (!routeSprintSlug) return defaultSprint(sprints);
    return (
      sprints.find((s) => s.slug === routeSprintSlug) ??
      sprints.find((s) => s.id === shownSprintId) ??
      null
    );
  }, [routeSprintSlug, sprints, shownSprintId]);

  // Keeps the URL on the sprint shown, once the sprint list is known:
  //   - the empty Sprint tab moves to the board's first sprint as soon as
  //     one exists (created here, or listed only by the client);
  //   - a renamed sprint moves to its new slug;
  //   - a sprint that left the board (deleted or unlinked) sends the user to
  //     the Sprint tab, which opens the default sprint or the empty state.
  useEffect(() => {
    if (!sprintsResponse) return;
    if (!currentSprint) {
      if (routeSprintSlug) replaceKeepingQuery(boardPath(boardSlug, BOARD_TAB.sprint));
      return;
    }
    setShownSprintId(currentSprint.id);
    if (currentSprint.slug !== routeSprintSlug) {
      replaceKeepingQuery(sprintPath(boardSlug, currentSprint.slug));
    }
  }, [sprintsResponse, currentSprint, routeSprintSlug, boardSlug, replaceKeepingQuery]);

  /**
   * The sprint selector: opens the chosen sprint's page, filters kept. No
   * sprint means the board's default sprint.
   */
  const selectSprint = useCallback(
    (sprintId: string | null) => {
      const sprint = sprintId ? sprints.find((s) => s.id === sprintId) : defaultSprint(sprints);
      if (!sprint) return;
      replaceKeepingQuery(sprintPath(boardSlug, sprint.slug));
    },
    [sprints, boardSlug, replaceKeepingQuery]
  );

  // Clear row selection when sprint or filters change
  useEffect(() => {
    setRowSelection({});
  }, [currentSprint?.id, filters]);

  // ── Tickets (server-side filtered) ─────────────────────────────────
  const ticketParams = useMemo(() => {
    // Waits for the saved filters, so no request goes out without them.
    if (!filtersReady || !currentSprint?.id) return undefined;
    return {
      sprintId: currentSprint.id,
      includeAllBoards,
      search: filters.search,
      statuses: filters.statuses,
      types: filters.types,
      priorities: filters.priorities,
      assigneeIds: filters.assigneeIds,
      reviewerIds: filters.reviewerIds,
      labelIds: filters.labelIds,
      boardIds: includeAllBoards ? filters.boardIds : null,
      projectId: filters.projectId,
      dueDateStart: filters.dueDateStart,
      dueDateEnd: filters.dueDateEnd,
      sortBy: "displayOrder",
      sortDirection: "asc" as const,
    };
  }, [filtersReady, currentSprint?.id, includeAllBoards, filters]);

  const { data: tickets, isLoading: ticketsLoading } = useTickets(
    boardId,
    ticketParams as Parameters<typeof useTickets>[1]
  );

  // ── Mutations (only those still needed by the page, not by the ticket form) ──
  const createSprintMutation = useCreateSprint();
  const createTicketMutation = useCreateTicket();
  const moveTicketMutation = useMoveTicket();
  const deleteTicketMutation = useDeleteTicket();
  const bulkMoveTicketsMutation = useBulkMoveTickets();
  const bulkDeleteTicketsMutation = useBulkDeleteTickets();

  // ── Row selection state ──────────────────────────────────────────
  const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

  // ── Bulk move dialog state ─────────────────────────────────────
  const [showBulkMoveDialog, setShowBulkMoveDialog] = useState(false);

  // ── Sprint lifecycle mutations ─────────────────────────────────
  const [showCompleteSprintDialog, setShowCompleteSprintDialog] = useState(false);
  const [showStartSprintDialog, setShowStartSprintDialog] = useState(false);
  const startSprintMutation = useStartSprint();
  // Loaded while a planned sprint is shown, so Start knows at once whether
  // other active sprints block it and the takeover dialog is needed.
  const canStartCurrentSprint = canManage && currentSprint?.status === SprintStatus.PLANNED;
  const {
    data: startPreviewResponse,
    isLoading: isStartPreviewLoading,
    refetch: refetchStartPreview,
  } = useSprintStartPreview(
    boardId,
    currentSprint?.id ?? "",
    { enabled: canStartCurrentSprint },
  );
  const startPreview = canStartCurrentSprint ? (startPreviewResponse?.data ?? null) : null;
  const completeSprintMutation = useCompleteSprint();

  // ── Edit/Delete Sprint dialog state ─────────────────────────────
  const [showEditSprintDialog, setShowEditSprintDialog] = useState(false);
  const [showDeleteSprintDialog, setShowDeleteSprintDialog] = useState(false);
  const [editTargetSprintId, setEditTargetSprintId] = useState<string | null>(null);
  const [deleteTargetSprintId, setDeleteTargetSprintId] = useState<string | null>(null);

  const updateSprintMutation = useUpdateSprint();
  const deleteSprintMutation = useDeleteSprint();

  // ── Handlers ─────────────────────────────────────────────────────

  const handleCreateSprint = useCallback(
    async (data: CreateSprintData) => {
      await createSprintMutation.mutateAsync({
        boardId,
        data: {
          name: data.name,
          goal: data.goal,
          startDate: data.startDate.toISOString(),
          endDate: data.endDate.toISOString(),
          series: data.series,
          childBoardIds: data.childBoardIds.length > 0 ? data.childBoardIds : undefined,
          takeOver: data.takeOver,
        },
      });
    },
    [boardId, createSprintMutation]
  );

  // Helper to find a ticket's board ID from the loaded tickets list
  const findTicketBoardId = useCallback(
    (ticketId: string): string => {
      const allTickets = tickets?.data ?? [];
      for (const t of allTickets) {
        if (t.id === ticketId) return t.board?.id ?? boardId;
        for (const sub of t.subTickets ?? []) {
          if (sub.id === ticketId) return sub.board?.id ?? t.board?.id ?? boardId;
        }
      }
      return boardId;
    },
    [tickets?.data, boardId]
  );

  const findTicket = useCallback(
    (ticketId: string): IncidentCloseGuardTicket | undefined => {
      for (const t of tickets?.data ?? []) {
        if (t.id === ticketId) return t;
        const sub = t.subTickets?.find((candidate) => candidate.id === ticketId);
        if (sub) return sub;
      }
      return undefined;
    },
    [tickets?.data]
  );

  // An incident moved into Done stops at the close dialog first.
  const { guardStatusChange, dialogProps: incidentCloseDialogProps } = useIncidentCloseGuard();

  // These handlers are still needed by kanban cards (status change, sub-ticket toggle, quick-add)
  const handleStatusChange = useCallback(
    async (ticketId: string, newStatus: TicketStatus) => {
      const ticket = findTicket(ticketId);
      if (!ticket) return;
      await guardStatusChange(ticket, newStatus, findTicketBoardId(ticketId));
    },
    [findTicket, findTicketBoardId, guardStatusChange]
  );

  const handleSubTicketToggle = useCallback(
    async (subTicketId: string, completed: boolean) => {
      const ticket = findTicket(subTicketId);
      if (!ticket) return;
      await guardStatusChange(
        ticket,
        completed ? TicketStatus.DONE : TicketStatus.TODO,
        findTicketBoardId(subTicketId)
      );
    },
    [findTicket, findTicketBoardId, guardStatusChange]
  );

  const handleQuickAddSubTicket = useCallback(
    async (parentId: string, title: string) => {
      await createTicketMutation.mutateAsync({
        boardId: findTicketBoardId(parentId),
        data: {
          title,
          type: TicketType.FEATURE,
          priority: TicketPriority.MEDIUM,
          status: TicketStatus.TODO,
          missionRank: MissionRank.M,
          parentTicketId: parentId,
          sprintId: currentSprint?.id,
        },
      });
    },
    [findTicketBoardId, createTicketMutation, currentSprint?.id]
  );

  const handleTicketClick = useCallback(
    (ticket: TicketListResponse) => {
      openEditTicketDialog(ticket);
    },
    [openEditTicketDialog]
  );

  const handleRequestCreateSubTicket = useCallback(
    (parentId: string) => {
      openCreateSubTicketDialog(ticketKeyOrId(tickets?.data, parentId));
    },
    [openCreateSubTicketDialog, tickets?.data]
  );

  // New tickets created from this tab land in the sprint currently shown.
  const handleCreateTicket = useCallback(
    (status?: TicketStatus) => {
      openCreateTicketDialogIn({ status, sprintSlug: currentSprint?.slug });
    },
    [openCreateTicketDialogIn, currentSprint?.slug]
  );

  // ── Row action handlers ─────────────────────────────────────────

  const handleMoveToSprint = useCallback(
    async (ticketId: string, sprintId: string, subTickets?: SubTicketMoveChoice) => {
      await moveTicketMutation.mutateAsync({
        boardId: findTicketBoardId(ticketId),
        ticketId,
        data: { sprintId, ...subTickets },
      });
    },
    [findTicketBoardId, moveTicketMutation]
  );

  const handleMoveToBacklog = useCallback(
    async (ticketId: string, subTickets?: SubTicketMoveChoice) => {
      const ticketBoardId = findTicketBoardId(ticketId);
      const backlogResponse = await getBacklog(ticketBoardId);
      const backlogId = backlogResponse?.data?.id;
      if (!backlogId) return;
      await moveTicketMutation.mutateAsync({
        boardId: ticketBoardId,
        ticketId,
        data: { backlogId, ...subTickets },
      });
    },
    [findTicketBoardId, moveTicketMutation]
  );

  const handleDeleteTicket = useCallback(
    async (ticketId: string) => {
      await deleteTicketMutation.mutateAsync({
        boardId: findTicketBoardId(ticketId),
        ticketId,
      });
    },
    [findTicketBoardId, deleteTicketMutation]
  );

  const handleBulkMove = useCallback(
    async (data: TicketBulkMoveRequest) => {
      await bulkMoveTicketsMutation.mutateAsync({
        boardId,
        data,
      });
      setShowBulkMoveDialog(false);
      setRowSelection({});
    },
    [boardId, bulkMoveTicketsMutation]
  );

  const incompleteTicketIds = useMemo(
    () =>
      (tickets?.data ?? [])
        .filter((t) => t.status !== TicketStatus.DONE)
        .map((t) => t.id),
    [tickets?.data]
  );

  // Derive selected ticket IDs from rowSelection state (keys are ticket IDs)
  const selectedTicketIds = useMemo(
    () => Object.keys(rowSelection).filter((id) => rowSelection[id]),
    [rowSelection]
  );

  // Deletes every selected ticket. The backend has no bulk-delete endpoint, so
  // the mutation fans out one request per ticket and reports partial failure
  // itself; the tickets that failed stay selected so they can be retried.
  const handleBulkDelete = useCallback(async () => {
    if (selectedTicketIds.length === 0) return;
    const targets: BulkDeleteTicketTarget[] = selectedTicketIds.map((ticketId) => ({
      boardId: findTicketBoardId(ticketId),
      ticketId,
    }));
    const result = await bulkDeleteTicketsMutation.mutateAsync({ targets });
    setRowSelection(
      Object.fromEntries(result.failed.map((failure) => [failure.target.ticketId, true] as const))
    );
  }, [selectedTicketIds, findTicketBoardId, bulkDeleteTicketsMutation]);

  // ── Sprint lifecycle handlers ────────────────────────────────────
  // A failed or stale preview lets the start go out plain. When the server
  // then refuses it because another active sprint blocks it, the preview is
  // read again: if it now names conflicts the takeover dialog opens, else the
  // refusal is toasted, as useStartSprint leaves that one case to this caller.
  const handleStartSprint = useCallback(async () => {
    if (!currentSprint) return;
    if (startPreview && startPreview.conflicts.length > 0) {
      setShowStartSprintDialog(true);
      return;
    }
    try {
      await startSprintMutation.mutateAsync({
        boardId,
        sprintId: currentSprint.id,
      });
    } catch (error) {
      if (!isApiError(error, SPRINT_ACTIVE_SPRINT_EXISTS_CODE)) throw error;
      const refreshed = await refetchStartPreview();
      if ((refreshed.data?.data?.conflicts.length ?? 0) > 0) {
        setShowStartSprintDialog(true);
        return;
      }
      if (refreshed.error) {
        console.error("Failed to reload the sprint start preview:", refreshed.error);
      }
      toast.error(error.message);
    }
  }, [boardId, currentSprint, startPreview, startSprintMutation, refetchStartPreview]);

  const handleStartSprintWithTakeover = useCallback(async () => {
    if (!currentSprint) return;
    await startSprintMutation.mutateAsync({
      boardId,
      sprintId: currentSprint.id,
      data: { takeOver: true },
    });
  }, [boardId, currentSprint, startSprintMutation]);

  const handleCompleteSprint = useCallback(
    async (data: SprintCompleteRequest) => {
      if (!currentSprint) return;
      await completeSprintMutation.mutateAsync({
        boardId,
        sprintId: currentSprint.id,
        data,
      });
      setShowCompleteSprintDialog(false);
    },
    [boardId, currentSprint, completeSprintMutation]
  );

  // ── Edit/Delete Sprint handlers ────────────────────────────────
  const handleOpenEditSprintDialog = useCallback((sprintId: string) => {
    setEditTargetSprintId(sprintId);
    setShowEditSprintDialog(true);
  }, []);

  const handleEditSprintDialogChange = useCallback((open: boolean) => {
    setShowEditSprintDialog(open);
    if (!open) setEditTargetSprintId(null);
  }, []);

  const handleOpenDeleteSprintDialog = useCallback((sprintId: string) => {
    setDeleteTargetSprintId(sprintId);
    setShowDeleteSprintDialog(true);
  }, []);

  const handleDeleteSprintDialogChange = useCallback((open: boolean) => {
    setShowDeleteSprintDialog(open);
    if (!open) setDeleteTargetSprintId(null);
  }, []);

  const handleUpdateSprint = useCallback(
    async (data: SprintUpdateRequest) => {
      if (!editTargetSprintId) return;
      await updateSprintMutation.mutateAsync({
        boardId,
        sprintId: editTargetSprintId,
        data,
      });
      setShowEditSprintDialog(false);
      setEditTargetSprintId(null);
    },
    [boardId, editTargetSprintId, updateSprintMutation]
  );

  const handleDeleteSprint = useCallback(
    async (ticketAction: TicketRelocationAction, targetSprintId?: string) => {
      if (!deleteTargetSprintId) return;
      await deleteSprintMutation.mutateAsync({
        boardId,
        sprintId: deleteTargetSprintId,
        ticketAction,
        targetSprintId,
      });
      setShowDeleteSprintDialog(false);
      setDeleteTargetSprintId(null);
    },
    [boardId, deleteTargetSprintId, deleteSprintMutation]
  );

  const editTargetSprint = useMemo(
    () => (editTargetSprintId ? sprints.find((s) => s.id === editTargetSprintId) ?? null : null),
    [editTargetSprintId, sprints]
  );

  const deleteTargetSprint = useMemo(
    () => (deleteTargetSprintId ? sprints.find((s) => s.id === deleteTargetSprintId) ?? null : null),
    [deleteTargetSprintId, sprints]
  );

  const handleSetViewMode = useCallback(
    (mode: ViewMode) => {
      setViewMode(mode);
      window.localStorage.setItem("sprint-view-mode", mode);
    },
    [setViewMode]
  );

  useEffect(() => {
    const storedViewMode = window.localStorage.getItem("sprint-view-mode");
    if (storedViewMode) {
      setViewMode(storedViewMode as ViewMode);
    }
  }, [setViewMode]);

  // ── Derived Values ───────────────────────────────────────────────
  const isLoading = sprintsLoading || ticketsLoading || !filtersReady;

  // One clock read per mount rather than one per render: reading Date.now() in
  // the render body is impure and can straddle a day boundary between the
  // server render and hydration.
  const now = useMemo(() => Date.now(), []);
  const daysRemaining = getDaysRemaining(currentSprint?.endDate, now);

  const completedTicketsCount = useMemo(
    () => tickets?.data?.filter((t) => t.status === TicketStatus.DONE).length ?? 0,
    [tickets?.data]
  );

  const incompleteTicketsCount = useMemo(
    () => tickets?.data?.filter((t) => t.status !== TicketStatus.DONE).length ?? 0,
    [tickets?.data]
  );

  return {
    // UI state
    viewMode,
    setViewMode: handleSetViewMode,
    includeAllBoards,
    setIncludeAllBoards: handleIncludeAllBoardsChange,

    // Dialog state
    incidentCloseDialogProps,
    showCreateSprintDialog,
    openCreateSprintDialog,
    openEditTicketDialog,
    closeDialog,

    // Filters
    filters,
    setFilters,
    clearFilters,
    resetToBaseline,
    hasFilterChanges,
    activeFilterCount,

    // Board Preferences
    boardPrefs,

    // Data
    sprints,
    currentSprint,
    tickets,
    ticketsLoading,
    childBoards,
    boardOptions,
    hasChildBoards,
    isLoading,

    // Derived
    daysRemaining,
    completedTicketsCount,
    incompleteTicketsCount,

    // Sprint lifecycle
    showCompleteSprintDialog,
    setShowCompleteSprintDialog,
    handleStartSprint,
    handleCompleteSprint,
    // Start waits for the preview, which decides between a plain start and
    // the takeover dialog.
    isStartingSprintPending: startSprintMutation.isPending || isStartPreviewLoading,
    startPreview,
    showStartSprintDialog,
    setShowStartSprintDialog,
    handleStartSprintWithTakeover,

    // Edit/Delete Sprint
    showEditSprintDialog,
    handleEditSprintDialogChange,
    showDeleteSprintDialog,
    handleDeleteSprintDialogChange,
    editTargetSprint,
    deleteTargetSprint,
    handleOpenEditSprintDialog,
    handleOpenDeleteSprintDialog,
    handleUpdateSprint,
    handleDeleteSprint,

    // Row actions
    handleMoveToSprint,
    handleMoveToBacklog,
    handleDeleteTicket,

    // Row selection
    rowSelection,
    setRowSelection,
    selectedTicketIds,

    // Bulk move
    showBulkMoveDialog,
    setShowBulkMoveDialog,
    handleBulkMove,
    incompleteTicketIds,
    isBulkMovePending: bulkMoveTicketsMutation.isPending,

    // Bulk delete
    handleBulkDelete,
    isBulkDeletePending: bulkDeleteTicketsMutation.isPending,

    // Handlers (only those still needed externally by kanban/list components)
    handleCreateSprint,
    handleStatusChange,
    handleSubTicketToggle,
    handleQuickAddSubTicket,
    handleTicketClick,
    handleCreateTicket,
    handleRequestCreateSubTicket,
    copyTicketLink,
    setSelectedSprintId: selectSprint,
  };
}
