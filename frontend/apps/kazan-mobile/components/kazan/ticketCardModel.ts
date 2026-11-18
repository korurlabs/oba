import { resolvePriority } from "@/components/kazan/ticketMetaModel";
import { reporterName } from "@/components/ticket/ticketModel";
import type { DateFormatter, DateTranslate } from "@/lib/dates";
import type { BoardTicket } from "@/lib/schemas/boards";
import type { TicketListItem } from "@/lib/schemas/tickets";

/*
 * The rules of the ticket card that every ticket list shares: board views,
 * My Tickets and Incidents. The head row reads as the ticket's name, "{type}
 * {key}", and keeps priority and mission rank at its trailing edge, where a
 * scan down a list finds them. The meta row leads with the status, then the
 * time slot (the due day, or an incident's open time), sub-ticket progress
 * and, at its trailing edge, the assignees. A ticket of another board gets a
 * "Board: X" line above the card, so the rows keep their place. The last line
 * says who created the ticket and when.
 */

/**
 * What the card reads of a ticket: the fields that the board list
 * (`TicketListResponse`) and the cross-board list (`AllTicketListResponse`)
 * both carry.
 */
export type CardTicket = Pick<
  TicketListItem & BoardTicket,
  | "id"
  | "createdAt"
  | "title"
  | "key"
  | "type"
  | "status"
  | "priority"
  | "missionRank"
  | "dueDate"
  | "assignees"
  | "board"
> &
  Partial<
    Pick<
      BoardTicket,
      "labels" | "parentTicket" | "subTickets" | "createdBy" | "createdBySystemActor" | "sprint"
    >
  >;

/**
 * A time slot that replaces the due day, such as how long an incident has
 * been open. `late` draws it in the destructive colour.
 */
export interface TicketCardTiming {
  icon: "clock" | "calendar";
  label: string;
  late: boolean;
}

/** Labels a card shows before "+N". */
export const MAX_CARD_LABELS = 3;

/** A mark of the card's head row; `spacer` pushes the rest to the trailing edge. */
export type CardHeadItem = "type" | "key" | "spacer" | "priority" | "rank";

/** A mark of the card's meta row; `spacer` pushes the rest to the trailing edge. */
export type CardFootItem = "status" | "timing" | "progress" | "spacer" | "assignees";

/**
 * The prefix of the ticket's board when it is not `boardId`, so a ticket of
 * a child board names where it lives; null on the board itself.
 */
export function otherBoardPrefix(
  ticket: Pick<CardTicket, "board">,
  boardId: string | undefined
): string | null {
  if (!boardId || !ticket.board || ticket.board.id === boardId) return null;
  return ticket.board.prefix ?? null;
}

/** Done and total sub-tickets, or null for a ticket without any. */
export function cardProgress(
  subTickets: CardTicket["subTickets"]
): { done: number; total: number } | null {
  if (!subTickets || subTickets.length === 0) return null;
  return {
    done: subTickets.filter((subTicket) => subTicket.status === "DONE").length,
    total: subTickets.length,
  };
}

/** The head row's marks in order, leaving out a priority or rank the ticket lacks. */
export function cardHeadItems(input: {
  priority: string | null | undefined;
  rank: string | null | undefined;
}): CardHeadItem[] {
  const items: CardHeadItem[] = ["type", "key", "spacer"];
  if (resolvePriority(input.priority)) items.push("priority");
  if (input.rank) items.push("rank");
  return items;
}

/** The meta row's marks in order, leaving out what the ticket lacks. */
export function cardFootItems(input: {
  showStatus: boolean;
  timing: boolean;
  progress: boolean;
  assignees: boolean;
}): CardFootItem[] {
  const items: CardFootItem[] = [];
  if (input.showStatus) items.push("status");
  if (input.timing) items.push("timing");
  if (input.progress) items.push("progress");
  if (input.assignees) items.push("spacer", "assignees");
  return items;
}

/** Who created the ticket, a member or an integration; null when the list does not say. */
export function creatorName(
  ticket: Partial<Pick<CardTicket, "createdBy" | "createdBySystemActor">>
): string | null {
  return reporterName(ticket, "") || null;
}

/**
 * When a ticket was created, on the 24-hour clock in the viewer's zone:
 * "Today 09:40", "Yesterday 10:12", the weekday within a week, then the date.
 * It is the app's activity time with "Today" spelled out, because a bare time
 * after a name reads as a time of day, not as the day it happened.
 */
export function createdWhen(
  value: string,
  dates: Pick<DateFormatter, "time" | "timestamp">,
  t: DateTranslate
): string | null {
  const time: string | null = dates.time(value);
  const stamp: string | null = dates.timestamp(value);
  if (!time || !stamp) return null;
  // The activity time of an instant of today is the bare time.
  return stamp === time ? t("dateTodayAt", { time }) : stamp;
}

/**
 * "Created by Ayşe Kaya · Today 09:40", "Created by Ayşe Kaya" when the time
 * cannot be read, or null when the list does not name the creator.
 */
export function createdByLine(
  ticket: Pick<CardTicket, "createdAt" | "createdBy" | "createdBySystemActor">,
  dates: Pick<DateFormatter, "time" | "timestamp">,
  t: DateTranslate
): string | null {
  const name: string | null = creatorName(ticket);
  if (!name) return null;
  const when: string | null = createdWhen(ticket.createdAt, dates, t);
  return when ? t("ticketCreatedBy", { name, when }) : t("ticketCreatedByName", { name });
}
