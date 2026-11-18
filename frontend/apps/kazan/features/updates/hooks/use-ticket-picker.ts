"use client";

import { useCallback, useMemo, useState } from "react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { useBoardPreferences } from "@/hooks/use-board-preferences";
import {
  countActiveTicketFilters,
  useTicketFilters,
  type TicketFilterParams,
} from "@/lib/hooks/use-ticket-filters";
import { useBoards, BOARD_TREE_PARAMS } from "@/features/boards/hooks/use-boards";
import { flattenBoardsForSelect } from "@/features/boards/hooks/use-board-select";
import type { BoardFilterOption } from "@/features/tickets/components/ticket-filter-panel";
import { useAllTickets } from "@/features/all-tickets/hooks/use-all-tickets";
import type { AllTicketListResponse } from "@/features/all-tickets/schemas";

/**
 * State of a ticket picker: the all-tickets list with the shared, URL-synced
 * ticket filters. Unless the URL already holds a filter, the list starts on
 * the reader's filters saved under `preferencesKey`, or on the reader's own
 * tickets (assignee = me) when none are saved; every filter, Board included,
 * stays usable. Each screen that picks tickets passes its own key, so its
 * saved filters do not change another's; the create and edit screens of one
 * kind share one key and so one saved set.
 *
 * The starting set is derived, not written into the URL on mount: nuqs drops
 * a URL write queued in the same task as a client navigation's history push,
 * so a link into this screen would lose it. The first change writes the whole
 * starting set plus that change to the URL, so no starting filter is lost;
 * from then on, and after clearing all filters, the URL alone decides.
 */
export function useTicketPicker(preferencesKey: string) {
  const { data: meResponse, isFetched: isMeFetched } = useGetMe();
  const meUserId = meResponse?.data?.id;
  const prefs = useBoardPreferences(preferencesKey);

  const {
    filters: urlFilters,
    setFilters: setUrlFilters,
    clearFilters: clearUrlFilters,
  } = useTicketFilters();

  const [filtersTouched, setFiltersTouched] = useState(false);

  const seed = useMemo((): Partial<TicketFilterParams> => {
    if (prefs.hasSavedFilters && prefs.savedFilters) {
      return prefs.savedFilters as Partial<TicketFilterParams>;
    }
    return meUserId ? { assigneeIds: [meUserId] } : {};
  }, [prefs.hasSavedFilters, prefs.savedFilters, meUserId]);

  const usesSeed = !filtersTouched && countActiveTicketFilters(urlFilters) === 0;

  const filters = useMemo(
    (): TicketFilterParams => (usesSeed ? { ...urlFilters, ...seed } : urlFilters),
    [usesSeed, urlFilters, seed],
  );
  const activeFilterCount = countActiveTicketFilters(filters);

  const setFilters = useCallback(
    (changes: Partial<TicketFilterParams>) => {
      setFiltersTouched(true);
      return setUrlFilters(usesSeed ? { ...seed, ...changes } : changes);
    },
    [usesSeed, seed, setUrlFilters],
  );

  const clearFilters = useCallback(() => {
    setFiltersTouched(true);
    return clearUrlFilters();
  }, [clearUrlFilters]);

  // The sidebar's cached board tree (same query key, no extra request),
  // flattened so a child board is listed under its parent.
  const { data: boardTreeResponse } = useBoards(BOARD_TREE_PARAMS);
  const boardOptions = useMemo(
    (): BoardFilterOption[] =>
      flattenBoardsForSelect(boardTreeResponse?.data ?? []).map((board) => ({
        id: board.id,
        name: board.name,
        prefix: board.prefix,
        color: board.color ?? "",
        depth: board.depth,
      })),
    [boardTreeResponse?.data],
  );

  // The list waits for "me", so it never loads unfiltered first.
  const ticketParams = useMemo(() => {
    if (!isMeFetched) return undefined;
    return {
      search: filters.search,
      statuses: filters.statuses,
      types: filters.types,
      priorities: filters.priorities,
      assigneeIds: filters.assigneeIds,
      reviewerIds: filters.reviewerIds,
      labelIds: filters.labelIds,
      boardIds: filters.boardIds,
      dueDateStart: filters.dueDateStart,
      dueDateEnd: filters.dueDateEnd,
      sortBy: filters.sortBy ?? "createdAt",
      sortDirection: filters.sortDirection ?? "desc",
    };
  }, [isMeFetched, filters]);

  const ticketsQuery = useAllTickets(ticketParams as Record<string, unknown> | undefined);

  // The list holds root tickets with their sub-tickets nested.
  const tickets = useMemo(
    (): AllTicketListResponse[] =>
      ((ticketsQuery.data?.data ?? []) as AllTicketListResponse[]).filter(
        (ticket) => !ticket.parentTicketId,
      ),
    [ticketsQuery.data?.data],
  );

  return {
    prefs,
    filters,
    setFilters,
    clearFilters,
    activeFilterCount,
    boardOptions,
    tickets,
    meUserId,
    isLoading: !isMeFetched || ticketsQuery.isLoading,
    isError: ticketsQuery.isError,
    error: ticketsQuery.error,
    refetch: ticketsQuery.refetch,
    isFetching: ticketsQuery.isFetching,
  };
}
