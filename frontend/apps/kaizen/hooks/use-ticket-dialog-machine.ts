"use client";

/**
 * useTicketDialogMachine
 *
 * The ticket create/edit dialog IS a route, so "which ticket is open" is
 * derived from the URL, not from local state. What used to be a four-state
 * machine is now:
 *
 *   - ticket actions  -> a navigation (see `useTicketNavigation`)
 *   - sprint dialog   -> the only genuinely local dialog left
 *
 * The transition helpers keep their old names so the toolbars did not have
 * to change; they take the ticket's key and the slugs the urls carry.
 */

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { TicketStatus } from "@/types/enums";
import { useShortcutStore } from "@/hooks/use-shortcuts";
import {
  useTicketNavigation,
  type OpenableTicket,
} from "@/features/tickets/hooks/use-ticket-navigation";

interface UseTicketDialogMachineOptions {
  /**
   * When provided, the shortcut store's "createTicket" action is gated
   * behind a role check (role !== "VIEWER"). If omitted, the shortcut
   * always opens the create route.
   */
  currentUserRole?: string;
}

/** Placement/status overrides for a single "create ticket" entry point. */
export interface OpenCreateTicketOverrides {
  status?: TicketStatus;
  /** The sprint's slug on the board. Wins over `backlog` (backend VAL_TICKET_PLACEMENT). */
  sprintSlug?: string;
  /** Place the ticket in the board's backlog. */
  backlog?: boolean;
}

/** A ticket in a page's list, with the sub-tickets it carries. */
interface TicketTreeNode {
  id: string;
  key: string;
  subTickets?: readonly TicketTreeNode[] | null;
}

/**
 * The key of the ticket with `ticketId` among `tickets` and their
 * sub-tickets, for opening a create form under it. The id itself when the
 * list does not hold it: the create url accepts an id as well.
 */
export function ticketKeyOrId(
  tickets: readonly TicketTreeNode[] | null | undefined,
  ticketId: string,
): string {
  for (const ticket of tickets ?? []) {
    if (ticket.id === ticketId) return ticket.key;
    for (const sub of ticket.subTickets ?? []) {
      if (sub.id === ticketId) return sub.key;
    }
  }
  return ticketId;
}

export function useTicketDialogMachine(options: UseTicketDialogMachineOptions = {}) {
  const { currentUserRole } = options;
  // The board the page shows is the default board for create. Board pages
  // carry it as their route's slug; the board-less ticket lists have none.
  const boardSlug = useParams<{ boardSlug?: string }>()?.boardSlug;
  const { openTicket, openCreateTicket } = useTicketNavigation();

  const [showCreateSprintDialog, setShowCreateSprintDialog] = useState(false);

  // ── Transition helpers ──────────────────────────────────────────

  /**
   * Opens the create form with no placement. Pages that create into a
   * sprint or backlog wrap this with `openCreateTicketDialogIn`.
   */
  const openCreateTicketDialog = useCallback(
    (status?: TicketStatus) => {
      openCreateTicket({ boardSlug, status });
    },
    [openCreateTicket, boardSlug],
  );

  /** Opens the create form with an explicit sprint/backlog placement. */
  const openCreateTicketDialogIn = useCallback(
    (overrides: OpenCreateTicketOverrides) => {
      openCreateTicket({ boardSlug, ...overrides });
    },
    [openCreateTicket, boardSlug],
  );

  /**
   * Opens the create form as a sub-ticket of the ticket with `parentKey`.
   * Placement is deliberately omitted: the backend derives it from the
   * parent, and sending both trips `VAL_TICKET_PLACEMENT`.
   */
  const openCreateSubTicketDialog = useCallback(
    (parentKey: string) => {
      openCreateTicket({ boardSlug, parentKey });
    },
    [openCreateTicket, boardSlug],
  );

  /** Opens the edit dialog of a ticket from the page's list. */
  const openEditTicketDialog = useCallback(
    (ticket: OpenableTicket) => {
      openTicket(ticket.key, ticket);
    },
    [openTicket],
  );

  const openCreateSprintDialog = useCallback(() => {
    setShowCreateSprintDialog(true);
  }, []);

  const closeDialog = useCallback(() => {
    setShowCreateSprintDialog(false);
    useShortcutStore.getState().close();
  }, []);

  // ── Sync shortcut store -> create route (⌥N) ───────────────────

  const shortcutAction = useShortcutStore((s) => s.activeAction);
  useEffect(() => {
    if (shortcutAction !== "createTicket") return;
    // Clear the store first, both so the stale action doesn't fire again on
    // the next board and so this effect re-runs into its early return
    // instead of navigating twice.
    useShortcutStore.getState().close();
    if (currentUserRole !== undefined && currentUserRole === "VIEWER") return;
    openCreateTicketDialog();
  }, [shortcutAction, currentUserRole, openCreateTicketDialog]);

  return {
    // Transition helpers
    openCreateTicketDialog,
    openCreateTicketDialogIn,
    openCreateSubTicketDialog,
    openEditTicketDialog,
    openCreateSprintDialog,
    closeDialog,

    // Derived state
    showCreateSprintDialog,
  };
}
