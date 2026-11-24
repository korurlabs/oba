"use client";

import { useState, useMemo, useCallback, useEffect } from "react";
import type { ViewMode } from "@/components/layout/view-toggle";
import { TicketStatus } from "@/types/enums";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { useBaselineTicketFilters } from "@/lib/hooks/use-ticket-filters";
import { useBacklog } from "./use-backlog";
import {
  useCachedBoard,
  useCachedChildBoards,
  useCachedDescendantBoards,
} from "@/features/boards/hooks/use-boards";
import type { BoardFilterOption } from "@/features/tickets/components/ticket-filter-panel";
import { useSprints } from "@/features/sprints/hooks/use-sprints";
import {
  useTickets,
  useDeleteTicket,
  useMoveTicket,
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

// ── Hook ────────────────────────────────────────────────────────────

export type BacklogPageState = ReturnType<typeof useBacklogPage>;

export function useBacklogPage(boardId: string) {
  // ── Simple UI state ──────────────────────────────────────────────
  const [viewMode, setViewMode] = useState<ViewMode>("list");
  const [showAICreator, setShowAICreator] = useState(false);
  const [includeChildBoards, setIncludeChildBoards] = useIncludeAllBoards(true);
  const [sprintsNeeded, setSprintsNeeded] = useState(false);

  // ── Dialog state machine ─────────────────────────────────────────
  // Ticket create/edit is a route now; nothing about it is local state.
  const { currentUserRole } = useBoardRoleContext();
  const {
    openCreateTicketDialogIn,
    openCreateSubTicketDialog,
    openEditTicketDialog,
    closeDialog,
  } = useTicketDialogMachine({ currentUserRole });

  // Backwards compatibility for links shared as `?id=<TICKET-KEY>`:
  // resolve the key and forward to the real ticket route.
  useLegacyTicketDeepLink();

  const copyTicketLink = useCopyTicketLink();

  // ── Row selection state ──────────────────────────────────────────
  const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

  // ── Bulk move dialog state ─────────────────────────────────────
  const [showBulkMoveDialog, setShowBulkMoveDialog] = useState(false);

  // ── Board Preferences ──────────────────────────────────────────────
  const boardPrefs = useBoardPreferences(boardId);

  // ── Filters (URL-synced via nuqs, relative to the saved filters) ──
  // Unknown until the user is loaded; an empty baseline when nothing is saved.
  const filterBaseline = useMemo(
    () => (boardPrefs.isMeFetched ? (boardPrefs.savedFilters ?? {}) : null),
    [boardPrefs.isMeFetched, boardPrefs.savedFilters],
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
      setIncludeChildBoards(value);
      if (!value) setFilters({ boardIds: null });
    },
    [setIncludeChildBoards, setFilters],
  );

  // Clear row selection when filters change
  useEffect(() => {
    setRowSelection({});
  }, [filters]);

  // ── Data Fetching ────────────────────────────────────────────────

  // Fetch backlog to get the backlogId
  const { data: backlogResponse } = useBacklog(boardId);
  const backlogId = backlogResponse?.data?.id ?? null;

  // Fetch child boards from cached board list (no extra API call)
  const { data: childBoardsData } = useCachedChildBoards(boardId);
  const childBoards = useMemo(() => mapChildBoards(childBoardsData), [childBoardsData]);

  // The Board filter offers the boards this list spans: the current board and
  // every board below it at any depth, and only while child boards are
  // included, because without them the list covers this board alone.
  const { data: descendantBoards } = useCachedDescendantBoards(boardId);
  const { data: currentBoard } = useCachedBoard(boardId);
  const boardOptions = useMemo((): BoardFilterOption[] => {
    if (!includeChildBoards || descendantBoards.length === 0) return [];
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
  }, [includeChildBoards, descendantBoards, currentBoard]);

  // Fetch sprints only when needed (deferred until user interacts with "move to sprint")
  const { data: sprintsResponse } = useSprints(boardId, {
    enabled: sprintsNeeded,
  });
  const sprints = useMemo(
    () =>
      (sprintsResponse?.data ?? []).map((s) => ({
        id: s.id,
        name: s.name,
        status: s.status,
      })),
    [sprintsResponse?.data],
  );

  // ── Tickets (server-side filtered) ─────────────────────────────────
  const ticketParams = useMemo(() => {
    // Waits for the saved filters, so no request goes out without them.
    if (!filtersReady || !backlogId) return undefined;
    return {
      backlogId,
      includeAllBoards: includeChildBoards,
      search: filters.search,
      statuses: filters.statuses,
      types: filters.types,
      priorities: filters.priorities,
      assigneeIds: filters.assigneeIds,
      reviewerIds: filters.reviewerIds,
      labelIds: filters.labelIds,
      boardIds: includeChildBoards ? filters.boardIds : null,
      projectId: filters.projectId,
      dueDateStart: filters.dueDateStart,
      dueDateEnd: filters.dueDateEnd,
      sortBy: "displayOrder",
      sortDirection: "asc" as const,
    };
  }, [filtersReady, backlogId, includeChildBoards, filters]);

  const { data: ticketsResponse, isLoading: ticketsLoading } = useTickets(boardId, ticketParams);

  const isLoading = ticketsLoading || !filtersReady;
  const tickets = useMemo(
    (): TicketListResponse[] => ticketsResponse?.data ?? [],
    [ticketsResponse?.data],
  );

  // ── Search ───────────────────────────────────────────────────────
  const handleSearchChange = useCallback(
    (value: string | undefined) => {
      setFilters({ search: value || null });
    },
    [setFilters],
  );

  // ── Helpers ─────────────────────────────────────────────────────

  // Helper to find a ticket's board ID from the loaded tickets list
  const findTicketBoardId = useCallback(
    (ticketId: string): string => {
      for (const t of tickets) {
        if (t.id === ticketId) return t.board?.id ?? boardId;
        for (const sub of t.subTickets ?? []) {
          if (sub.id === ticketId) return sub.board?.id ?? t.board?.id ?? boardId;
        }
      }
      return boardId;
    },
    [tickets, boardId]
  );

  // ── Mutations ────────────────────────────────────────────────────
  const moveToSprintMutation = useMoveTicket();
  const deleteTicketMutation = useDeleteTicket();
  const bulkMoveTicketsMutation = useBulkMoveTickets();
  const bulkDeleteTicketsMutation = useBulkDeleteTickets();

  const handleMoveToSprint = useCallback(
    async (ticketId: string, sprintId: string, subTickets?: SubTicketMoveChoice) => {
      await moveToSprintMutation.mutateAsync({
        boardId: findTicketBoardId(ticketId),
        ticketId,
        data: { sprintId, ...subTickets },
      });
    },
    [findTicketBoardId, moveToSprintMutation],
  );

  const handleDeleteTicket = useCallback(
    async (ticketId: string) => {
      await deleteTicketMutation.mutateAsync({
        boardId: findTicketBoardId(ticketId),
        ticketId,
      });
    },
    [findTicketBoardId, deleteTicketMutation],
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
      tickets
        .filter((t) => t.status !== TicketStatus.DONE)
        .map((t) => t.id),
    [tickets]
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
      Object.fromEntries(result.failed.map((failure) => [failure.target.ticketId, true] as const)),
    );
  }, [selectedTicketIds, findTicketBoardId, bulkDeleteTicketsMutation]);

  // ── Handlers ─────────────────────────────────────────────────────
  const handleTicketClick = useCallback(
    (ticket: TicketListResponse) => {
      // The list can contain tickets from child boards, so route with the
      // ticket's own board rather than the page's.
      openEditTicketDialog(ticket);
    },
    [openEditTicketDialog],
  );

  const handleSprintsNeeded = useCallback(() => {
    setSprintsNeeded(true);
  }, []);

  const handleShowAICreator = useCallback(() => {
    setShowAICreator(true);
  }, []);

  // ── Kanban-specific handlers ──────────────────────────────────────

  const findTicket = useCallback(
    (ticketId: string): IncidentCloseGuardTicket | undefined => {
      for (const t of tickets) {
        if (t.id === ticketId) return t;
        const sub = t.subTickets?.find((candidate) => candidate.id === ticketId);
        if (sub) return sub;
      }
      return undefined;
    },
    [tickets]
  );

  // An incident moved into Done stops at the close dialog first.
  const { guardStatusChange, dialogProps: incidentCloseDialogProps } = useIncidentCloseGuard();

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

  const handleRequestCreateSubTicket = useCallback(
    (parentId: string) => {
      openCreateSubTicketDialog(ticketKeyOrId(tickets, parentId));
    },
    [openCreateSubTicketDialog, tickets]
  );

  // New tickets created from this tab land in the board's backlog.
  const handleCreateTicket = useCallback(
    (status?: TicketStatus) => {
      openCreateTicketDialogIn({ status, backlog: true });
    },
    [openCreateTicketDialogIn]
  );

  // ── View mode persistence ─────────────────────────────────────────

  const handleSetViewMode = useCallback(
    (mode: ViewMode) => {
      setViewMode(mode);
      window.localStorage.setItem("backlog-view-mode", mode);
    },
    [setViewMode]
  );

  useEffect(() => {
    const storedViewMode = window.localStorage.getItem("backlog-view-mode");
    if (storedViewMode) {
      setViewMode(storedViewMode as ViewMode);
    }
  }, [setViewMode]);

  return {
    // UI state
    viewMode,
    incidentCloseDialogProps,
    setViewMode: handleSetViewMode,
    showAICreator,
    setShowAICreator,
    includeChildBoards,
    setIncludeChildBoards: handleIncludeAllBoardsChange,

    // Dialog state
    openCreateTicketDialog: handleCreateTicket,
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
    backlogId,
    tickets,
    sprints,
    childBoards,
    boardOptions,
    isLoading,
    ticketsLoading,

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

    // Handlers
    handleSearchChange,
    handleMoveToSprint,
    handleDeleteTicket,
    handleTicketClick,
    handleCreateTicket,
    handleSprintsNeeded,
    handleShowAICreator,
    handleSubTicketToggle,
    handleRequestCreateSubTicket,
    copyTicketLink,
  };
}
