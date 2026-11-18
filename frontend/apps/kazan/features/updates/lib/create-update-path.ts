/**
 * The link from a ticket selection to the create update screen.
 *
 * Not marked "use client": the create page parses the parameter on the
 * server, and inside a "use client" module these functions would become
 * client-reference proxies there.
 *
 * The selection travels as ticket keys, not IDs: a key resolves to its
 * ticket on any board the reader can open (`GET /kazan/tickets/by-key/{key}`),
 * while a ticket ID can only be read together with the ID of its board, and
 * a board view also lists tickets of child boards and of shared sprints.
 */

import { DAILY_UPDATES_PATH } from "@/features/chat/lib/chat-route";

/** Route of the create update screen. */
const CREATE_UPDATE_PATH = `${DAILY_UPDATES_PATH}/new`;

/** Query parameter with the comma-separated keys of the tickets to start with. */
export const UPDATE_TICKET_KEYS_PARAM = "ticketKeys";

/** The create update screen, starting with exactly these tickets in this order. */
export function updateCreatePathForTickets(ticketKeys: readonly string[]): string {
  const query = new URLSearchParams({ [UPDATE_TICKET_KEYS_PARAM]: ticketKeys.join(",") });
  return `${CREATE_UPDATE_PATH}?${query.toString()}`;
}

/**
 * The ticket keys of the parameter, trimmed, without blanks and repeats, in
 * their order. `null` when the parameter is absent: the update was not started
 * from a ticket selection, so its card starts empty.
 */
export function parseTicketKeysParam(value: string | string[] | undefined): string[] | null {
  if (value === undefined) return null;
  const raw = Array.isArray(value) ? value.join(",") : value;
  const keys = raw
    .split(",")
    .map((key) => key.trim())
    .filter((key) => key.length > 0);
  return Array.from(new Set(keys));
}
