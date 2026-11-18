import type { EventQueryRequest } from "../schemas";

/**
 * Query keys of the events. Every key starts with the organization, so the
 * events of one organization never show under another. Kept out of the
 * "use client" hook file so a server page can prefetch under the exact keys
 * the hooks read.
 */
export const EVENT_KEYS = {
  all: (organizationId: string) => ["events", organizationId] as const,
  lists: (organizationId: string) => [...EVENT_KEYS.all(organizationId), "list"] as const,
  list: (organizationId: string, params: EventQueryRequest) =>
    [...EVENT_KEYS.lists(organizationId), params] as const,
  detail: (organizationId: string, eventId: string) =>
    [...EVENT_KEYS.all(organizationId), "detail", eventId] as const,
};
