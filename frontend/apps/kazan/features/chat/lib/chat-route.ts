/**
 * Paths of the chat pages and the choice of the channel a chat URL opens.
 *
 * A chat URL names its channel the way people do: `/chats/<channel slug>`
 * for a GENERAL channel, and the fixed `/daily-updates` and `/pull-requests`
 * for the one channel of each other purpose an organization holds. URLs carry
 * no organization: a slug resolves among the channels of the session's
 * default organization, the organization every chat request goes to.
 *
 * Not marked "use client": the chat pages resolve and redirect on the server
 * with these, and the client components build the same links. Inside a
 * "use client" module the functions would become client-reference proxies
 * and could not be called on the server.
 */
import type { UserPreferences } from "@oba/shared/features/users/schema";
import { PULL_REQUEST_STATUSES_PARAM } from "@/features/pull-requests/lib/pull-request-channel-filter";
import { UPDATE_FILTER_KEYS } from "@/features/updates/lib/update-day";
import { ChatChannelPurpose, type ChatChannelResponse } from "../schemas";

/** The chat: redirects to a named channel. */
export const CHATS_PATH = "/chats";
/** The daily updates channel. */
export const DAILY_UPDATES_PATH = "/daily-updates";
/** The pull requests channel. */
export const PULL_REQUESTS_PATH = "/pull-requests";

/**
 * The query parameter that named the open channel by ID before channels had
 * slugs. Old links in notifications, bookmarks and message text still carry
 * it; `/chats` turns it into the channel's URL.
 */
export const LEGACY_CHANNEL_ID_PARAM = "channelId";

/**
 * The query parameters that point into one channel's content: an open
 * thread, a message to jump to, a message of the thread, the day and person
 * filters of the daily updates channel, and the status filter of the pull
 * requests channel (which shares `person`). A redirect to another channel
 * than the one they belong to drops them, as the chat view does when it
 * falls back to another channel.
 */
export const CHAT_CHANNEL_CONTENT_PARAMS = [
  "thread",
  "jump",
  "threadMessage",
  ...UPDATE_FILTER_KEYS,
  PULL_REQUEST_STATUSES_PARAM,
] as const;

/** Key in `userPreferences`: the reader's channel order per organization, `{ [orgId]: channelId[] }`. */
const CHAT_CHANNEL_ORDER_KEY = "chatChannelOrder";

/** The URL of a channel: its fixed path for the daily updates and pull requests channels, else its slug. */
export function chatChannelPath(channel: Pick<ChatChannelResponse, "slug" | "purpose">): string {
  switch (channel.purpose) {
    case ChatChannelPurpose.DAILY_UPDATES:
      return DAILY_UPDATES_PATH;
    case ChatChannelPurpose.PULL_REQUESTS:
      return PULL_REQUESTS_PATH;
    default:
      return `${CHATS_PATH}/${encodeURIComponent(channel.slug)}`;
  }
}

/**
 * The channel a chat path names, among `channels`: the one channel of its
 * purpose on `/daily-updates` and `/pull-requests`, the channel of the slug
 * on `/chats/<slug>`. Undefined on `/chats`, on any other path, and for a
 * slug no listed channel holds. The channel found may be one the reader
 * cannot read (`member` false).
 */
export function chatRouteChannel(
  pathname: string,
  channels: readonly ChatChannelResponse[],
): ChatChannelResponse | undefined {
  if (pathname === DAILY_UPDATES_PATH) {
    return channels.find((channel) => channel.purpose === ChatChannelPurpose.DAILY_UPDATES);
  }
  if (pathname === PULL_REQUESTS_PATH) {
    return channels.find((channel) => channel.purpose === ChatChannelPurpose.PULL_REQUESTS);
  }
  const prefix = `${CHATS_PATH}/`;
  if (!pathname.startsWith(prefix)) return undefined;
  const slug = pathname.slice(prefix.length);
  if (!slug || slug.includes("/")) return undefined;
  return channels.find((channel) => channel.slug === slug);
}

/**
 * `path` with a query string, less the `drop` parameters: the request's
 * query on the server, the URL's search parameters in the browser. Used by
 * the redirects and URL replacements of the chat, so they keep a search, a
 * jump target or an open thread. Only the query is copied, and the path is
 * one of the fixed chat paths or a resolved channel's, so the target never
 * leaves the app.
 */
export function chatRouteHref(
  path: string,
  query: URLSearchParams | Record<string, string | string[] | undefined>,
  drop: readonly string[] = [],
): string {
  const search = new URLSearchParams();
  const entries: [string, string | string[] | undefined][] =
    query instanceof URLSearchParams ? Array.from(query.entries()) : Object.entries(query);
  for (const [name, value] of entries) {
    if (value === undefined || drop.includes(name)) continue;
    for (const item of Array.isArray(value) ? value : [value]) search.append(name, item);
  }
  const queryString = search.toString();
  return queryString ? `${path}?${queryString}` : path;
}

/** The reader's saved channel order of one organization; empty when none is saved or the value is not a list of ids. */
function savedChannelOrder(preferences: UserPreferences | null | undefined, orgId: string): string[] {
  const byOrganization = preferences?.[CHAT_CHANNEL_ORDER_KEY];
  if (!byOrganization || typeof byOrganization !== "object") return [];
  const order = (byOrganization as Record<string, unknown>)[orgId];
  return Array.isArray(order) ? order.filter((id): id is string => typeof id === "string") : [];
}

/**
 * The channel `/chats` opens: the first channel the reader can read in their
 * own order (the saved ids first, then server order), a GENERAL one before
 * any other. The chat view's channel order hook orders the channels the same
 * way. OWNER and ADMIN see private channels they are not on (`member` false)
 * and read none of their content, so such a channel is never chosen.
 */
export function defaultChatChannel(
  channels: ChatChannelResponse[],
  preferences: UserPreferences | null | undefined,
  orgId: string,
): ChatChannelResponse | undefined {
  const savedOrder = savedChannelOrder(preferences, orgId);
  const rank = (channel: ChatChannelResponse) => {
    const index = savedOrder.indexOf(channel.id);
    return index === -1 ? savedOrder.length : index;
  };
  // A stable sort keeps server order among the channels that have no saved place.
  const ordered = channels.filter((channel) => channel.member).sort((a, b) => rank(a) - rank(b));
  return ordered.find((channel) => channel.purpose === ChatChannelPurpose.GENERAL) ?? ordered[0];
}
