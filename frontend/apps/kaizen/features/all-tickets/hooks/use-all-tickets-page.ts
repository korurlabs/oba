"use client";

import { useState, useMemo, useCallback, useEffect } from "react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import type { ViewMode } from "@/components/layout/view-toggle";
import { MissionRank, TicketStatus, TicketStatusSchema, TicketType, TicketPriority } from "@/types/enums";
import type { TicketListResponse } from "@/features/tickets/schemas";
import {
  useBaselineTicketFilters,
  type TicketFilterBaseline,
} from "@/lib/hooks/use-ticket-filters";
import { useCreateTicket } from "@/features/tickets/hooks/use-tickets";
import {
  useIncidentCloseGuard,
  type IncidentCloseGuardTicket,
} from "@/features/tickets/hooks/use-incident-close-guard";
import { useBoardPreferences } from "@/hooks/use-board-preferences";
import { ticketKeyOrId, useTicketDialogMachine } from "@/hooks/use-ticket-dialog-machine";
import { useLegacyTicketDeepLink } from "@/hooks/use-ticket-deep-link";
import { useCopyTicketLink } from "@/hooks/use-copy-ticket-link";
import { useBoards, BOARD_TREE_PARAMS } from "@/features/boards/hooks/use-boards";
import { flattenBoardsForSelect } from "@/features/boards/hooks/use-board-select";
import type {
  BoardFilterOption,
  FilterCategory,
} from "@/features/tickets/components/ticket-filter-panel";
import { useAllTickets } from "./use-all-tickets";
import type { AllTicketListResponse } from "../schemas";

// ── View Mode Persistence ──────────────────────────────────────────

const VIEW_MODE_KEY = "tickets-view-mode";

// ── Variants ───────────────────────────────────────────────────────

/**
 * The cross-board list serves two pages. "tickets" lists every ticket and
 * starts on the user's own; "incidents" lists only INCIDENT tickets and starts
 * on the open ones, with no person filter, because an incident concerns
 * everyone on the board until someone picks it up.
 */
export type AllTicketsVariant = "tickets" | "incidents";

/**
 * Every status but DONE counts as open, so an incident waiting on a third
 * party stays in view and a status added later is open by default.
 */
export const OPEN_STATUSES = TicketStatusSchema.options.filter(
  (status) => status !== TicketStatus.DONE,
);

/** Saved filters and columns live under their own key per page. */
const PREFERENCES_KEY: Record<AllTicketsVariant, string> = {
  tickets: "main",
  incidents: "incidents",
};

// ── Hook ────────────────────────────────────────────────────────────

export type AllTicketsPageState = ReturnType<typeof useAllTicketsPage>;

export function useAllTicketsPage(variant: AllTicketsVariant = "tickets") {
  const isIncidents = variant === "incidents";

  const { data: meResponse, isFetched: isMeFetched } = useGetMe();
  const meUserId = meResponse?.data?.id;

  // ── Simple UI state ──────────────────────────────────────────────
  const [viewMode, setViewMode] = useState<ViewMode>("list");

  // ── Dialog state machine ─────────────────────────────────────────
  // Ticket create/edit is a route now. This page is not scoped to a
  // board, so no default board is passed: ticket clicks route with the
  // ticket's own board, and create opens the board-less create route
  // where the user picks a board in the form.
  const {
    openCreateTicketDialog,
    openCreateSubTicketDialog,
    openEditTicketDialog,
    closeDialog,
  } = useTicketDialogMachine();

  // Backwards compatibility for links shared as `?id=<TICKET-KEY>`:
  // resolve the key and forward to the real ticket route.
  useLegacyTicketDeepLink();

  const copyTicketLink = useCopyTicketLink();

  // ── Board preferences (persisted via user preferences API) ────────
  const boardPrefs = useBoardPreferences(PREFERENCES_KEY[variant]);

  // ── Filters (URL-synced via nuqs, relative to a baseline) ─────────
  // The baseline is the saved set, even one that filters nothing; without a
  // saved set, Tickets starts on the user's own tickets and Incidents on the
  // open ones. It is unknown until the user is loaded.
  const filterBaseline = useMemo((): TicketFilterBaseline | null => {
    if (!isMeFetched || !boardPrefs.isMeFetched || !meUserId) return null;
    if (boardPrefs.hasSavedFilterSet && boardPrefs.savedFilters) return boardPrefs.savedFilters;
    return isIncidents ? { statuses: OPEN_STATUSES } : { assigneeIds: [meUserId] };
  }, [
    isMeFetched,
    boardPrefs.isMeFetched,
    boardPrefs.hasSavedFilterSet,
    boardPrefs.savedFilters,
    meUserId,
    isIncidents,
  ]);

  const {
    filters,
    setFilters,
    clearFilters,
    resetToBaseline,
    hasChanges: hasFilterChanges,
    activeFilterCount: rawActiveFilterCount,
    isReady: filtersReady,
  } = useBaselineTicketFilters(filterBaseline);

  // The Incidents page fixes the type, so a `types` value left in the URL is
  // neither applied nor counted as an active filter.
  const activeFilterCount =
    isIncidents && (filters.types ?? []).length > 0
      ? rawActiveFilterCount - 1
      : rawActiveFilterCount;

  // ── Board filter options ─────────────────────────────────────────
  // The sidebar's cached board tree (same query key, no extra request),
  // flattened so a child board is listed under its parent.
  const { data: boardTreeResponse } = useBoards(BOARD_TREE_PARAMS);
  const boardOptions = useMemo(
    (): BoardFilterOption[] =>
      flattenBoardsForSelect(boardTreeResponse?.data ?? []).map((b) => ({
        id: b.id,
        name: b.name,
        prefix: b.prefix,
        color: b.color ?? "",
        depth: b.depth,
      })),
    [boardTreeResponse?.data],
  );

  // ── Data Fetching ────────────────────────────────────────────────
  const ticketParams = useMemo(() => {
    if (!filtersReady || !meUserId) return undefined;
    return {
      search: filters.search,
      statuses: filters.statuses,
      types: isIncidents ? [TicketType.INCIDENT] : filters.types,
      priorities: filters.priorities,
      assigneeIds: filters.assigneeIds,
      reviewerIds: filters.reviewerIds,
      createdByIds: filters.createdByIds,
      labelIds: filters.labelIds,
      boardIds: filters.boardIds,
      dueDateStart: filters.dueDateStart,
      dueDateEnd: filters.dueDateEnd,
      sortBy: filters.sortBy ?? "createdAt",
      sortDirection: filters.sortDirection ?? "desc",
    };
  }, [filtersReady, meUserId, filters, isIncidents]);

  const { data: ticketsResponse, isLoading: ticketsLoading } = useAllTickets(
    ticketParams as Record<string, unknown> | undefined,
  );

  const tickets = useMemo(
    (): AllTicketListResponse[] => (ticketsResponse?.data ?? []) as AllTicketListResponse[],
    [ticketsResponse?.data],
  );

  const ticketCount = tickets.length;

  // ── Mutations ────────────────────────────────────────────────────
  const createTicketMutation = useCreateTicket();

  // ── Handlers ─────────────────────────────────────────────────────

  const findTicketBoardId = useCallback(
    (ticketId: string): string | undefined => {
      for (const t of tickets) {
        if (t.id === ticketId) return t.board?.id;
        for (const sub of t.subTickets ?? []) {
          if (sub.id === ticketId) return sub.board?.id ?? t.board?.id;
        }
      }
      return undefined;
    },
    [tickets],
  );

  const findTicket = useCallback(
    (ticketId: string): IncidentCloseGuardTicket | undefined => {
      for (const t of tickets) {
        if (t.id === ticketId) return t;
        const sub = t.subTickets?.find((candidate) => candidate.id === ticketId);
        if (sub) return sub;
      }
      return undefined;
    },
    [tickets],
  );

  // An incident moved into Done stops at the close dialog first.
  const { guardStatusChange, dialogProps: incidentCloseDialogProps } = useIncidentCloseGuard();

  const handleStatusChange = useCallback(
    async (ticketId: string, newStatus: TicketStatus) => {
      const ticket = findTicket(ticketId);
      const boardId = findTicketBoardId(ticketId);
      if (!ticket || !boardId) return;
      await guardStatusChange(ticket, newStatus, boardId);
    },
    [findTicket, findTicketBoardId, guardStatusChange],
  );

  const handleSubTicketToggle = useCallback(
    async (subTicketId: string, completed: boolean) => {
      const ticket = findTicket(subTicketId);
      const boardId = findTicketBoardId(subTicketId);
      if (!ticket || !boardId) return;
      await guardStatusChange(
        ticket,
        completed ? TicketStatus.DONE : TicketStatus.TODO,
        boardId,
      );
    },
    [findTicket, findTicketBoardId, guardStatusChange],
  );

  const handleQuickAddSubTicket = useCallback(
    async (parentId: string, title: string) => {
      const boardId = findTicketBoardId(parentId);
      if (!boardId) return;
      await createTicketMutation.mutateAsync({
        boardId,
        data: {
          title,
          type: TicketType.FEATURE,
          priority: TicketPriority.MEDIUM,
          status: TicketStatus.TODO,
          missionRank: MissionRank.M,
          parentTicketId: parentId,
        },
      });
    },
    [findTicketBoardId, createTicketMutation],
  );

  const handleTicketClick = useCallback(
    (ticket: TicketListResponse) => {
      openEditTicketDialog(ticket);
    },
    [openEditTicketDialog],
  );

  const handleRequestCreateSubTicket = useCallback(
    (parentId: string) => {
      openCreateSubTicketDialog(ticketKeyOrId(tickets, parentId));
    },
    [openCreateSubTicketDialog, tickets],
  );

  const handleSearchChange = useCallback(
    (value: string | undefined) => {
      setFilters({ search: value || null });
    },
    [setFilters],
  );

  const handleSetViewMode = useCallback(
    (mode: ViewMode) => {
      setViewMode(mode);
      window.localStorage.setItem(VIEW_MODE_KEY, mode);
    },
    [setViewMode],
  );

  // Restore view mode from localStorage
  useEffect(() => {
    const storedViewMode = window.localStorage.getItem(VIEW_MODE_KEY);
    if (storedViewMode === "list" || storedViewMode === "kanban") {
      setViewMode(storedViewMode);
    }
  }, [setViewMode]);

  // ── Derived Values ───────────────────────────────────────────────
  const isLoading = ticketsLoading;

  // Props for the filter panel: Incidents hides the type it fixes and offers
  // the reporter, which only this cross-board list can filter by.
  const filterPanelOptions = useMemo(
    (): { hiddenCategories: FilterCategory[]; showReporter: boolean } =>
      isIncidents
        ? { hiddenCategories: ["type"], showReporter: true }
        : { hiddenCategories: [], showReporter: false },
    [isIncidents],
  );

  return {
    variant,

    // UI state
    viewMode,
    setViewMode: handleSetViewMode,

    // Dialog state
    incidentCloseDialogProps,
    openCreateTicketDialog,
    openEditTicketDialog,
    closeDialog,

    // Filters
    filters,
    setFilters,
    clearFilters,
    resetToBaseline,
    hasFilterChanges,
    activeFilterCount,
    boardOptions,
    filterPanelOptions,

    // Board preferences (column/filter persistence)
    boardPrefs,

    // Data
    tickets,
    ticketCount,
    isLoading,
    ticketsLoading,
    meUserId,

    // Handlers
    handleSearchChange,
    handleStatusChange,
    handleSubTicketToggle,
    handleQuickAddSubTicket,
    handleTicketClick,
    handleCreateTicket: openCreateTicketDialog,
    handleRequestCreateSubTicket,
    copyTicketLink,
  };
}
