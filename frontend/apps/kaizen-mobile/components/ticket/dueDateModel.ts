import { endOfDay } from "date-fns";

import { dueState } from "@/components/home/homeModel";
import { parseDateOnly } from "@/lib/dates";
import type { TicketDetail } from "@/lib/schemas/tickets";

/*
 * A ticket's due date between the picker and the API. The user picks a
 * calendar day; the API keeps the due date as an instant. No React, so
 * `bun test` covers it.
 */

/**
 * The wire value of a picked due day. The API stores a due date as an
 * instant, so the day goes out as the end of that day in the viewer's zone,
 * as a UTC instant with `Z`: read back in the same zone it is the picked day,
 * and for the team's zone it also falls on that day in UTC, which the API's
 * due-date filters compare against. Null for a value that is not a day.
 */
export function dueDateInstant(day: string | null | undefined): string | null {
  const date: Date | null = parseDateOnly(day);
  return date ? endOfDay(date).toISOString() : null;
}

/** The ticket's due day on the viewer's calendar, `YYYY-MM-DD`, or null without one. */
export function dueDayOf(ticket: Pick<TicketDetail, "dueDate">): string | null {
  return dueState(ticket.dueDate)?.day ?? null;
}
