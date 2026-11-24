"use client";

import { useState, useMemo, useCallback, useEffect } from "react";
import { parseAsString, useQueryState } from "nuqs";
import type { TicketListResponse, TicketBulkMoveRequest } from "@/features/tickets/schemas";
import type { TicketRelocationAction } from "@/features/tickets/schemas";
import type { SubTicketMoveChoice } from "@/features/tickets/components/sub-ticket-choice";
import { TicketStatus } from "@/types/enums";
import { useBaselineTicketFilters } from "@/lib/hooks/use-ticket-filters";
import {
  useSprints,
  useCreateSprint,
  useUpdateSprint,
  useDeleteSprint,
} from "@/features/sprints/hooks/use-sprints";
import { useBacklog } from "@/features/backlog/hooks/use-backlog";
import { ALL_TAB_SPRINT_PARAM, defaultSprint } from "@/features/sprints/lib/sprint-selection";
import type { SprintUpdateRequest } from "@/features/sprints/schemas";
import type { CreateSprintData } from "@/features/sprints/components/create-sprint-dialog";
import {
  useTickets,
  useUpdateTicket,
  useDeleteTicket,
  useMoveTicket,
  useBulkMoveTickets,
} from "@/features/tickets/hooks/use-tickets";
import {
  useBulkDeleteTickets,
  type BulkDeleteTicketTarget,
} from "@/features/tickets/hooks/use-bulk-delete-tickets";
import {
  useCachedBoard,
  useCachedChildBoards,
  useCachedDescendantBoards,
} from "@/features/boards/hooks/use-boards";
import type { BoardFilterOption } from "@/features/tickets/components/ticket-filter-panel";
import { useIncludeAllBoards } from "@/components/ui/include-all-boards-toggle";
import { useBoardPreferences } from "@/hooks/use-board-preferences";
import { useBoardRoleContext } from "@/features/boards/providers/board-role-provider";
import { useTicketDialogMachine } from "@/hooks/use-ticket-dialog-machine";
import { useLegacyTicketDeepLink } from "@/hooks/use-ticket-deep-link";
import { useCopyTicketLink } from "@/hooks/use-copy-ticket-link";
import { mapChildBoards } from "@/lib/utils/map-child-boards";
import { getBacklog } from "@/features/backlog/api";

// ── Hook ────────────────────────────────────────────────────────────

export type AllPageState = ReturnType<typeof useAllPage>;

/**
 * Combined page state for the All tab — shows a selectable sprint and the
 * backlog as two stacked list-only sections.
 *
 * Mirrors `useSprintPage` and `useBacklogPage` structure but:
 * - never reads/writes `viewMode` (list is forced in the page)
 * - independent row-selection state per section
 * - filters are shared (single useBaselineTicketFilters instance)
 * - hides the sprint section entirely when no sprints exist on the board
 *
 * The sprint section shows the sprint named by `?sprint=<sprint-slug>`, and
 * the board's default sprint (ACTIVE > PLANNED > first, as on the Sprint
 * tab) when the parameter is absent. The default sprint writes no parameter.
 */
export function useAllPage(boardId: string) {
  // ── Sprint selection (URL-synced by slug) ──────────────────────────
  // Replace, not push: choosing a sprint is a view setting like a filter,
  // not a step the Back button should walk through.
  const [sprintParam, setSprintParam] = useQueryState(
    ALL_TAB_SPRINT_PARAM,
    parseAsString.withOptions({ history: "replace", scroll: false }),
  );

  // ── Persistent UI prefs (shared with backlog/sprint pages) ─────────
  const [includeAllBoards, setIncludeAllBoards] = useIncludeAllBoards(true);

  // ── Dialog state machine ─────────────────────────────────────────
  // Ticket create/edit is a route now; only the sprint dialogs are local.
  // The section-aware create wrappers live further down, where the sprint
  // and backlog ids they need are already resolved.
  const { currentUserRole } = useBoardRoleContext();
  const {
    openCreateTicketDialogIn,
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

  // ── Filters (URL-synced via nuqs, relative to the saved filters) —
  // shared across both sections. Unknown until the user is loaded; an
  // empty baseline when nothing is saved.
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
      setIncludeAllBoards(value);
      if (!value) setFilters({ boardIds: null });
    },
    [setIncludeAllBoards, setFilters],
  );

  // ── Independent row selection per section ──────────────────────────
  const [sprintRowSelection, setSprintRowSelection] = useState<
    Record<string, boolean>
  >({});
  const [backlogRowSelection, setBacklogRowSelection] = useState<
    Record<string, boolean>
  >({});

  // ── Bulk move dialogs (one per section, independent) ──────────────
  const [showSprintBulkMoveDialog, setShowSprintBulkMoveDialog] =
    useState(false);
  const [showBacklogBulkMoveDialog, setShowBacklogBulkMoveDialog] =
    useState(false);

  // ── Edit/Delete sprint dialog state ──────────────────────────────
  const [showEditSprintDialog, setShowEditSprintDialog] = useState(false);
  const [showDeleteSprintDialog, setShowDeleteSprintDialog] = useState(false);
  const [editTargetSprintId, setEditTargetSprintId] = useState<string | null>(null);
  const [deleteTargetSprintId, setDeleteTargetSprintId] = useState<string | null>(null);

  // ── Data fetching ─────────────────────────────────────────────────
  const { data: sprintsResponse, isLoading: sprintsLoading } = useSprints(boardId);
  const sprints = useMemo(() => sprintsResponse?.data ?? [], [sprintsResponse?.data]);

  // A rename changes the slug of the selected sprint while the tab is open.
  // The sprint last shown is remembered by id, so the selection survives
  // and the parameter follows it to the new slug.
  const [shownSprintId, setShownSprintId] = useState<string | null>(null);

  const currentSprint = useMemo(() => {
    const fallback = defaultSprint(sprints);
    if (!sprintParam) return fallback;
    return (
      sprints.find((s) => s.slug === sprintParam) ??
      sprints.find((s) => s.id === shownSprintId) ??
      fallback
    );
  }, [sprintParam, sprints, shownSprintId]);

  // Keeps `?sprint=` on the sprint shown, once the sprint list is known: a
  // renamed sprint gets its new slug, and a sprint that left the board, or
  // one that is now the default, drops the parameter.
  useEffect(() => {
    if (!sprintsResponse) return;
    if (currentSprint) setShownSprintId(currentSprint.id);
    const isDefault = !currentSprint || currentSprint.id === defaultSprint(sprints)?.id;
    const wanted = isDefault ? null : currentSprint.slug;
    if (wanted !== sprintParam) void setSprintParam(wanted);
  }, [sprintsResponse, currentSprint, sprints, sprintParam, setSprintParam]);

  /**
   * The sprint selector: shows the chosen sprint, by slug unless it is the
   * default. No sprint means the default sprint.
   */
  const selectSprint = useCallback(
    (sprintId: string | null) => {
      const sprint = sprintId ? sprints.find((s) => s.id === sprintId) : null;
      if (sprintId && !sprint) return;
      void setSprintParam(!sprint || sprint.id === defaultSprint(sprints)?.id ? null : sprint.slug);
    },
    [sprints, setSprintParam],
  );

  const { data: backlogResponse, isLoading: backlogLoading } = useBacklog(boardId);
  const backlogId = backlogResponse?.data?.id ?? null;

  const { data: childBoardsData } = useCachedChildBoards(boardId);
  const childBoards = useMemo(
    () => mapChildBoards(childBoardsData),
    [childBoardsData],
  );

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

  // Sprint tickets — only enabled when a sprint is selected and the saved
  // filters are known, so no request goes out without them.
  const sprintTicketParams = useMemo(() => {
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

  const {
    data: sprintTicketsResponse,
    isLoading: sprintTicketsQueryLoading,
  } = useTickets(boardId, sprintTicketParams as Parameters<typeof useTickets>[1]);

  // Backlog tickets — only enabled when backlog id and the saved filters are loaded
  const backlogTicketParams = useMemo(() => {
    if (!filtersReady || !backlogId) return undefined;
    return {
      backlogId,
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
  }, [filtersReady, backlogId, includeAllBoards, filters]);

  const {
    data: backlogTicketsResponse,
    isLoading: backlogTicketsQueryLoading,
  } = useTickets(boardId, backlogTicketParams);

  // A section whose query waits for the saved filters is loading, not empty.
  const sprintTicketsLoading = sprintTicketsQueryLoading || !filtersReady;
  const backlogTicketsLoading = backlogTicketsQueryLoading || !filtersReady;

  const sprintTickets = useMemo(
    (): TicketListResponse[] => sprintTicketsResponse?.data ?? [],
    [sprintTicketsResponse?.data],
  );
  const backlogTickets = useMemo(
    (): TicketListResponse[] => backlogTicketsResponse?.data ?? [],
    [backlogTicketsResponse?.data],
  );

  // Clear row selections when filters change or selected sprint switches
  useEffect(() => {
    setSprintRowSelection({});
  }, [currentSprint?.id, filters]);
  useEffect(() => {
    setBacklogRowSelection({});
  }, [filters]);

  // ── Mutations ────────────────────────────────────────────────────
  const updateTicketMutation = useUpdateTicket();
  const deleteTicketMutation = useDeleteTicket();
  const moveTicketMutation = useMoveTicket();
  const bulkMoveTicketsMutation = useBulkMoveTickets();
  const bulkDeleteTicketsMutation = useBulkDeleteTickets();
  const createSprintMutation = useCreateSprint();
  const updateSprintMutation = useUpdateSprint();
  const deleteSprintMutation = useDeleteSprint();

  // ── Sprint dialog handlers ───────────────────────────────────────
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
    [boardId, createSprintMutation],
  );

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
    [boardId, editTargetSprintId, updateSprintMutation],
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
    [boardId, deleteTargetSprintId, deleteSprintMutation],
  );

  const editTargetSprint = useMemo(
    () => (editTargetSprintId ? sprints.find((s) => s.id === editTargetSprintId) ?? null : null),
    [editTargetSprintId, sprints],
  );

  const deleteTargetSprint = useMemo(
    () => (deleteTargetSprintId ? sprints.find((s) => s.id === deleteTargetSprintId) ?? null : null),
    [deleteTargetSprintId, sprints],
  );

  // ── Helpers ─────────────────────────────────────────────────────
  // Find a ticket's home board ID across either section's data.
  const findTicketBoardId = useCallback(
    (ticketId: string): string => {
      const lists: TicketListResponse[][] = [sprintTickets, backlogTickets];
      for (const list of lists) {
        for (const t of list) {
          if (t.id === ticketId) return t.board?.id ?? boardId;
          for (const sub of t.subTickets ?? []) {
            if (sub.id === ticketId) return sub.board?.id ?? t.board?.id ?? boardId;
          }
        }
      }
      return boardId;
    },
    [sprintTickets, backlogTickets, boardId],
  );

  // ── Section-aware create entry points ────────────────────────────
  // The All tab has two add-ticket buttons (sprint section header and
  // backlog section header). Placement is mutually exclusive on the
  // backend (VAL_TICKET_PLACEMENT), so each entry point forwards exactly
  // one placement into the create route.
  const openSprintAddTicketDialog = useCallback(() => {
    openCreateTicketDialogIn({ sprintSlug: currentSprint?.slug });
  }, [openCreateTicketDialogIn, currentSprint?.slug]);

  const openBacklogAddTicketDialog = useCallback(() => {
    openCreateTicketDialogIn({ backlog: true });
  }, [openCreateTicketDialogIn]);

  // Generic entry point (kanban column "+", ⌥N). No section context, so
  // the create form falls through to its own backlog fallback against the
  // selected board.
  const openCreateTicketDialog = useCallback(
    (status?: TicketStatus) => {
      openCreateTicketDialogIn({ status });
    },
    [openCreateTicketDialogIn],
  );

  // ── Row action handlers ─────────────────────────────────────────
  const handleTicketClick = useCallback(
    (ticket: TicketListResponse) => {
      openEditTicketDialog(ticket);
    },
    [openEditTicketDialog],
  );

  const handleMoveToSprint = useCallback(
    async (ticketId: string, sprintId: string, subTickets?: SubTicketMoveChoice) => {
      await moveTicketMutation.mutateAsync({
        boardId: findTicketBoardId(ticketId),
        ticketId,
        data: { sprintId, ...subTickets },
      });
    },
    [findTicketBoardId, moveTicketMutation],
  );

  const handleMoveToBacklog = useCallback(
    async (ticketId: string, subTickets?: SubTicketMoveChoice) => {
      const ticketBoardId = findTicketBoardId(ticketId);
      const fetched = await getBacklog(ticketBoardId);
      const targetBacklogId = fetched?.data?.id;
      if (!targetBacklogId) return;
      await moveTicketMutation.mutateAsync({
        boardId: ticketBoardId,
        ticketId,
        data: { backlogId: targetBacklogId, ...subTickets },
      });
    },
    [findTicketBoardId, moveTicketMutation],
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

  // ── Bulk move (independent per section) ────────────────────────
  const handleSprintBulkMove = useCallback(
    async (data: TicketBulkMoveRequest) => {
      await bulkMoveTicketsMutation.mutateAsync({ boardId, data });
      setShowSprintBulkMoveDialog(false);
      setSprintRowSelection({});
    },
    [boardId, bulkMoveTicketsMutation],
  );

  const handleBacklogBulkMove = useCallback(
    async (data: TicketBulkMoveRequest) => {
      await bulkMoveTicketsMutation.mutateAsync({ boardId, data });
      setShowBacklogBulkMoveDialog(false);
      setBacklogRowSelection({});
    },
    [boardId, bulkMoveTicketsMutation],
  );

  // ── Selection / incomplete derivations ─────────────────────────
  const sprintSelectedTicketIds = useMemo(
    () => Object.keys(sprintRowSelection).filter((id) => sprintRowSelection[id]),
    [sprintRowSelection],
  );
  const backlogSelectedTicketIds = useMemo(
    () => Object.keys(backlogRowSelection).filter((id) => backlogRowSelection[id]),
    [backlogRowSelection],
  );

  // ── Bulk delete (independent per section) ──────────────────────
  // The backend has no bulk-delete endpoint, so the mutation fans out one
  // request per ticket and reports partial failure itself. The tickets that
  // failed stay selected in their own section so they can be retried.
  const deleteSelection = useCallback(
    async (
      ticketIds: readonly string[],
      setSelection: (selection: Record<string, boolean>) => void,
    ) => {
      if (ticketIds.length === 0) return;
      const targets: BulkDeleteTicketTarget[] = ticketIds.map((ticketId) => ({
        boardId: findTicketBoardId(ticketId),
        ticketId,
      }));
      const result = await bulkDeleteTicketsMutation.mutateAsync({ targets });
      setSelection(
        Object.fromEntries(
          result.failed.map((failure) => [failure.target.ticketId, true] as const),
        ),
      );
    },
    [findTicketBoardId, bulkDeleteTicketsMutation],
  );

  const handleSprintBulkDelete = useCallback(
    () => deleteSelection(sprintSelectedTicketIds, setSprintRowSelection),
    [deleteSelection, sprintSelectedTicketIds],
  );

  const handleBacklogBulkDelete = useCallback(
    () => deleteSelection(backlogSelectedTicketIds, setBacklogRowSelection),
    [deleteSelection, backlogSelectedTicketIds],
  );

  const sprintIncompleteTicketIds = useMemo(
    () =>
      sprintTickets
        .filter((t) => t.status !== TicketStatus.DONE)
        .map((t) => t.id),
    [sprintTickets],
  );
  const backlogIncompleteTicketIds = useMemo(
    () =>
      backlogTickets
        .filter((t) => t.status !== TicketStatus.DONE)
        .map((t) => t.id),
    [backlogTickets],
  );

  // ── Derived values ─────────────────────────────────────────────

  // ── Aggregate loading flag ─────────────────────────────────────
  const isLoading =
    sprintsLoading || backlogLoading || sprintTicketsLoading || backlogTicketsLoading;

  return {
    // Filters / prefs
    filters,
    setFilters,
    clearFilters,
    resetToBaseline,
    hasFilterChanges,
    activeFilterCount,
    includeAllBoards,
    setIncludeAllBoards: handleIncludeAllBoardsChange,
    boardPrefs,

    // Dialog state
    openCreateTicketDialog,
    openEditTicketDialog,
    openSprintAddTicketDialog,
    openBacklogAddTicketDialog,
    openCreateSprintDialog,
    closeDialog,

    // Sprint dialog state
    showCreateSprintDialog,
    showEditSprintDialog,
    handleEditSprintDialogChange,
    showDeleteSprintDialog,
    handleDeleteSprintDialogChange,
    editTargetSprint,
    deleteTargetSprint,
    handleCreateSprint,
    handleOpenEditSprintDialog,
    handleOpenDeleteSprintDialog,
    handleUpdateSprint,
    handleDeleteSprint,

    // Sprint selection
    setSelectedSprintId: selectSprint,

    // Data
    currentSprint,
    sprints,
    sprintTickets,
    backlogTickets,
    backlogId,
    childBoards,
    boardOptions,
    isLoading,
    sprintTicketsLoading,
    backlogTicketsLoading,

    // Sprint section row selection / bulk move
    sprintRowSelection,
    setSprintRowSelection,
    sprintSelectedTicketIds,
    sprintIncompleteTicketIds,
    showSprintBulkMoveDialog,
    setShowSprintBulkMoveDialog,
    handleSprintBulkMove,

    // Backlog section row selection / bulk move
    backlogRowSelection,
    setBacklogRowSelection,
    backlogSelectedTicketIds,
    backlogIncompleteTicketIds,
    showBacklogBulkMoveDialog,
    setShowBacklogBulkMoveDialog,
    handleBacklogBulkMove,

    isBulkMovePending: bulkMoveTicketsMutation.isPending,

    // Bulk delete (per section)
    handleSprintBulkDelete,
    handleBacklogBulkDelete,
    isBulkDeletePending: bulkDeleteTicketsMutation.isPending,

    // Handlers
    handleTicketClick,
    handleMoveToSprint,
    handleMoveToBacklog,
    handleDeleteTicket,
    handleCreateTicket: openCreateTicketDialog,
    copyTicketLink,
  };
}
