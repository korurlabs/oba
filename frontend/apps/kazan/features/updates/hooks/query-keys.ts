/**
 * Query-key factory for daily update queries.
 *
 * Not marked "use client": the create and edit pages import these on the
 * server to prefetch under the exact keys the client hooks use. Inside a "use client"
 * module the functions would become client-reference proxies and could not be
 * called during the server prefetch.
 */
import type { UpdateCreateRequest, UpdateEditRequest } from "../schemas";

export const UPDATE_KEYS = {
  all: ["updates"] as const,
  detail: (updateId: string) => [...UPDATE_KEYS.all, "detail", updateId] as const,
  seedTicket: (ticketKey: string) => [...UPDATE_KEYS.all, "seed-ticket", ticketKey] as const,
  preview: (request: UpdateCreateRequest | null) =>
    [...UPDATE_KEYS.all, "preview", request] as const,
  previewEdit: (updateId: string, request: UpdateEditRequest | null) =>
    [...UPDATE_KEYS.all, "preview", updateId, request] as const,
  /**
   * Who has not posted in one organization on one day (`null` = today). Under
   * `all`, so posting, editing or deleting an update refreshes the count.
   */
  missing: (organizationId: string, date: string | null) =>
    [...UPDATE_KEYS.all, "missing", organizationId, date] as const,
  /**
   * The author's latest update up to `beforeDay`. Under `all`, so posting an
   * update refreshes it.
   */
  previous: (authorId: string, beforeDay: string) =>
    [...UPDATE_KEYS.all, "previous", authorId, beforeDay] as const,
};
