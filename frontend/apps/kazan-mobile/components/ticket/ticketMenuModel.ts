import type { TranslationKey } from "@/i18n";

/*
 * The pure half of the ticket menu, shared by the ticket screen and a held
 * board card: which actions a user may see, in which order. No React and no
 * icons, so `bun test` covers the gates.
 */

/** One action of the ticket menu; `components/ticket/ticketMenu.ts` gives each its icon. */
export type TicketMenuItemId =
  | "open"
  | "select"
  | "status"
  | "assignToMe"
  | "priority"
  | "moveBoard"
  | "moveSprint"
  | "moveBacklog"
  | "copyLink"
  | "shareToChat"
  | "openWeb"
  | "delete";

export interface TicketMenuItem {
  id: TicketMenuItemId;
  labelKey: TranslationKey;
  destructive?: boolean;
  onPress: () => void;
}

/** What the signed-in user may do with the ticket. */
export interface TicketMenuGates {
  /** A board member with write rights; without it only the reading actions are offered. */
  canWrite: boolean;
  /** False for a top-level backlog ticket, which the API keeps in Todo. */
  canChangeStatus: boolean;
  /** Whether the user already is an assignee; null while the user is not loaded. */
  isAssignee: boolean | null;
}

/**
 * What each action does. An optional handler that is left out hides its
 * action where the caller cannot offer it, such as Move to backlog for a
 * ticket that is not in a sprint.
 */
export interface TicketMenuHandlers {
  open?: () => void;
  /**
   * Starts selection mode with the ticket picked. Its caller offers it only
   * where tickets can be moved together, to a board role that may.
   */
  select?: () => void;
  changeStatus: () => void;
  assignToMe: () => void;
  setPriority: () => void;
  /** Left out for a sub-ticket, which stays on its parent's board. */
  moveToBoard?: () => void;
  moveToSprint?: () => void;
  moveToBacklog?: () => void;
  copyLink: () => void;
  shareToChat?: () => void;
  openWeb?: () => void;
  delete: () => void;
}

/**
 * The ticket menu in display order. Opening, copying the link, sharing to
 * chat and opening the web page need only read access; every other action
 * changes the ticket and needs write rights.
 */
export function ticketMenuItems(
  gates: TicketMenuGates,
  handlers: TicketMenuHandlers
): TicketMenuItem[] {
  const { canWrite } = gates;
  const items: (TicketMenuItem | null)[] = [
    handlers.open ? { id: "open", labelKey: "boardMenuOpen", onPress: handlers.open } : null,
    handlers.select
      ? { id: "select", labelKey: "boardSelectTicket", onPress: handlers.select }
      : null,
    canWrite && gates.canChangeStatus
      ? { id: "status", labelKey: "ticketChangeStatus", onPress: handlers.changeStatus }
      : null,
    canWrite && gates.isAssignee === false
      ? { id: "assignToMe", labelKey: "ticketAssignToMe", onPress: handlers.assignToMe }
      : null,
    canWrite
      ? { id: "priority", labelKey: "ticketSetPriority", onPress: handlers.setPriority }
      : null,
    canWrite && handlers.moveToBoard
      ? { id: "moveBoard", labelKey: "ticketMoveToBoard", onPress: handlers.moveToBoard }
      : null,
    canWrite && handlers.moveToSprint
      ? { id: "moveSprint", labelKey: "moveSprintTitle", onPress: handlers.moveToSprint }
      : null,
    canWrite && handlers.moveToBacklog
      ? { id: "moveBacklog", labelKey: "ticketMoveToBacklog", onPress: handlers.moveToBacklog }
      : null,
    { id: "copyLink", labelKey: "ticketCopyLink", onPress: handlers.copyLink },
    handlers.shareToChat
      ? { id: "shareToChat", labelKey: "ticketShareToChat", onPress: handlers.shareToChat }
      : null,
    handlers.openWeb
      ? { id: "openWeb", labelKey: "ticketOpenWeb", onPress: handlers.openWeb }
      : null,
    canWrite
      ? {
          id: "delete",
          labelKey: "ticketDeleteAction",
          destructive: true,
          onPress: handlers.delete,
        }
      : null,
  ];
  return items.filter((item): item is TicketMenuItem => item !== null);
}
