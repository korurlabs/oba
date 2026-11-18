import { boardHref } from "@/components/boards/boardRoutes";

/*
 * The app path of the ticket screen, without React, so every tab, the
 * deep-link handler and the push handler build the same link.
 */

/** The app path pattern of the ticket screen. */
export const TICKET_PATHNAME = "/ticket/[ticketKey]" as const;

/** The ticket screen of a ticket key. */
export function ticketHref(ticketKey: string) {
  return { pathname: TICKET_PATHNAME, params: { ticketKey } };
}

/** The ticket screen as a plain path, for the system deep-link handler. */
export function ticketPath(ticketKey: string): string {
  return `/ticket/${encodeURIComponent(ticketKey)}`;
}

/** The Boards tab, where a ticket of an unknown board leaves to. */
export const BOARDS_PATHNAME = "/boards" as const;

/**
 * Where the ticket screen leaves to when nothing lies under it: the ticket's
 * board, as the web's breadcrumb leads back to it, or the Boards tab while
 * the board is not known yet (the key is still being looked up, or failed).
 */
export function ticketExitHref(boardId: string | null | undefined) {
  return boardId ? boardHref(boardId) : BOARDS_PATHNAME;
}

/** The sheets the ticket screen opens over itself. */
export type TicketSheet = "assignees" | "labels" | "description" | "move-board";

/** A sheet of the ticket screen, for the ticket of `ticketKey`. */
export function ticketSheetHref(ticketKey: string, sheet: TicketSheet) {
  return { pathname: `/ticket/[ticketKey]/${sheet}` as const, params: { ticketKey } };
}

/**
 * The choice sheet of a ticket value (type, status, priority, mission rank), over a saved
 * ticket or New ticket alike; its picker comes through the choice store.
 */
export const TICKET_CHOICE_PATHNAME = "/ticket/choose" as const;

/**
 * The Close incident sheet, which asks for the root cause analysis and lessons
 * learned before an incident is set to Done, from a board, a ticket or New
 * ticket alike; its question comes through the incident close store.
 */
export const INCIDENT_CLOSE_PATHNAME = "/ticket/close-incident" as const;

/** The app path of the ticket screen without a ticket: New ticket. */
export const NEW_TICKET_PATHNAME = "/ticket/new" as const;

/** The sheets the New ticket screen opens over itself; they edit its draft. */
export type NewTicketSheet = "assignees" | "labels" | "description";

/** A sheet of the New ticket screen. */
export function newTicketSheetHref(sheet: NewTicketSheet) {
  return { pathname: `/ticket/new/${sheet}` as const };
}

/** What New ticket starts with; every field is optional. */
export interface NewTicketPreset {
  /** The board the ticket goes to. */
  boardId?: string | null;
  /** The sprint "Add to" starts on; the backlog without it. */
  sprintId?: string | null;
  /** The status it starts in, such as the Kanban column it was added from. */
  status?: string | null;
  /** The ticket the new one is a sub-ticket of. */
  parentKey?: string | null;
  /** The type it starts as, such as INCIDENT for Report incident. */
  type?: string | null;
  /** The priority it starts with, such as P1 for Report incident. */
  priority?: string | null;
}

/** New ticket with its preset; empty values are left out of the link. */
export function newTicketHref(preset: NewTicketPreset = {}) {
  const params: Record<string, string> = {};
  for (const [name, value] of Object.entries(preset)) {
    if (typeof value === "string" && value) params[name] = value;
  }
  return { pathname: NEW_TICKET_PATHNAME, params };
}

/** The preset of New ticket, read back from its route params. */
export function parseNewTicketPreset(
  params: Record<string, string | string[] | undefined>
): Required<{ [K in keyof NewTicketPreset]: string | null }> {
  const read = (name: keyof NewTicketPreset): string | null => {
    const raw: string | string[] | undefined = params[name];
    const value: string | undefined = Array.isArray(raw) ? raw[0] : raw;
    return value ? value : null;
  };
  return {
    boardId: read("boardId"),
    sprintId: read("sprintId"),
    status: read("status"),
    parentKey: read("parentKey"),
    type: read("type"),
    priority: read("priority"),
  };
}

/** The app path pattern of the full-screen image viewer of a ticket. */
export const TICKET_IMAGE_PATHNAME = "/ticket/[ticketKey]/image" as const;

/**
 * Which image of a ticket the viewer shows: the image at `index` among the
 * description's images in reading order, or the attachment of `attachmentId`.
 */
export type TicketImageTarget = { index: number } | { attachmentId: string };

/** The image viewer of a ticket, opened on `target`. */
export function ticketImageHref(ticketKey: string, target: TicketImageTarget) {
  return {
    pathname: TICKET_IMAGE_PATHNAME,
    params:
      "index" in target
        ? { ticketKey, index: String(target.index) }
        : { ticketKey, attachmentId: target.attachmentId },
  };
}

/**
 * The image the viewer was opened on, read back from its route params, or
 * null when a link carries neither a whole non-negative `index` nor an
 * `attachmentId`. An attachment wins when a link carries both.
 */
export function parseTicketImageTarget(
  params: Record<string, string | string[] | undefined>
): TicketImageTarget | null {
  const first = (name: string): string | undefined => {
    const raw: string | string[] | undefined = params[name];
    return Array.isArray(raw) ? raw[0] : raw;
  };
  const attachmentId: string | undefined = first("attachmentId");
  if (attachmentId) return { attachmentId };
  const index: string | undefined = first("index");
  if (index !== undefined && /^\d+$/.test(index)) return { index: Number(index) };
  return null;
}
