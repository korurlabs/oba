import { createLoader, parseAsInteger, parseAsString, parseAsStringLiteral } from "nuqs/server";
import type { Values } from "nuqs";
import { EVENT_LIMITS, EVENTS_PAGE_SIZE, EventPeriod, type EventQueryRequest } from "../schemas";

/** The list's tabs, in the order they are shown. */
export const EVENT_TABS = ["upcoming", "past"] as const;
export type EventTab = (typeof EVENT_TABS)[number];

/** The period each tab asks the server for. */
export const EVENT_TAB_PERIODS: Record<EventTab, EventPeriod> = {
  upcoming: EventPeriod.UPCOMING,
  past: EventPeriod.PAST,
};

/** The event list's tab, search and page in the URL, so a view can be reloaded and shared. */
export const eventFilterParams = {
  tab: parseAsStringLiteral(EVENT_TABS).withDefault("upcoming"),
  search: parseAsString,
  page: parseAsInteger.withDefault(1),
};

export type EventFilterParams = Values<typeof eventFilterParams>;

export const loadEventFilterParams = createLoader(eventFilterParams);

/**
 * The query the list sends for its URL state on the viewer's calendar day
 * `today` (`YYYY-MM-DD`): an event is upcoming while its last day is today
 * or later, and only the client knows the viewer's day. An absent search is
 * left out; a search longer than the server takes is cut to its limit.
 */
export function toEventQuery(filters: EventFilterParams, today: string): EventQueryRequest {
  const query: EventQueryRequest = {
    period: EVENT_TAB_PERIODS[filters.tab],
    today,
    page: Math.max(1, filters.page),
    amount: EVENTS_PAGE_SIZE,
  };
  const search = filters.search?.trim().slice(0, EVENT_LIMITS.searchMax);
  if (search) query.search = search;
  return query;
}
