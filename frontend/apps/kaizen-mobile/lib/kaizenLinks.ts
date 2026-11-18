import { BOARD_VIEWS, DEFAULT_BOARD_VIEW, type BoardView } from "@/components/boards/boardModel";
import { normalizeTicketKey } from "@/components/ticket/ticketModel";
import { CHAT_ERROR_CODES, ChatChannelPurpose } from "@/lib/schemas/chat";

/*
 * Reading the Kaizen web links people share, without React, so a link tapped
 * in a message and a link the system hands to the app open the same screen.
 * React Native's URL class has no query support, so the parts are read with
 * patterns here.
 */

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** An absolute http(s) URL: host, path, query. The fragment is dropped. */
const HTTP_URL = /^https?:\/\/([^/?#:]+)(?::\d+)?([^?#]*)(?:\?([^#]*))?(?:#.*)?$/i;

/** A leading locale segment such as `/en` or `/nl-BE`, which a web link may carry. */
const LOCALE_PREFIX = /^\/[a-z]{2}(?:-[A-Za-z]{2})?(?=\/|$)/;

/** Candidate URLs in plain text, as the web finds them; each is checked with `parseChatMessageLink`. */
const CHAT_URL_PATTERN = /https?:\/\/[^\s<>]*[^\s<>.,:;"'!?)\]]/g;

/** The channel a link names: by ID in an old link, by slug, or by purpose. */
export type KaizenLinkChannel =
  | { id: string }
  | { slug: string }
  | { purpose: typeof ChatChannelPurpose.DAILY_UPDATES | typeof ChatChannelPurpose.PULL_REQUESTS };

/** A link to one chat message, as "Copy message link" writes it on the web and in the app. */
export interface ChatMessageLink {
  /** The URL as written in the message. */
  href: string;
  messageId: string;
  /** The thread of a thread message link; null for a channel message link. */
  threadId: string | null;
  /** The channel the link names; null for an old link that named none. */
  channel: KaizenLinkChannel | null;
}

/** The board views the web has a page for and the app a view of. */
export type KaizenLinkBoardView = BoardView;

/**
 * The sprint a board link names: by slug on a sprint's own page and on All,
 * or by ID on the Sprint tab of a link made before sprints had slugs.
 */
export type KaizenLinkSprint = { slug: string } | { id: string };

/** The screen a Kaizen web link names. */
export type KaizenLinkTarget =
  | { kind: "ticket"; ticketKey: string }
  | { kind: "newTicket" }
  /** A board at a view, on one sprint when the link names one. */
  | {
      kind: "board";
      boardSlug: string;
      view: KaizenLinkBoardView;
      sprint: KaizenLinkSprint | null;
    }
  | { kind: "chatHome" }
  /** A channel, at a message or a thread when the link carries one. */
  | {
      kind: "channel";
      channel: KaizenLinkChannel | null;
      messageId: string | null;
      threadId: string | null;
    }
  | { kind: "newUpdate" }
  | { kind: "sharePullRequest" }
  | { kind: "home" }
  | { kind: "incidents" }
  | { kind: "profile" }
  | { kind: "settings" };

interface ParsedUrl {
  host: string;
  /** The path without the locale segment and without a trailing slash. */
  path: string;
  query: ReadonlyMap<string, string>;
}

function decode(value: string): string | null {
  try {
    return decodeURIComponent(value.replace(/\+/g, " "));
  } catch (error: unknown) {
    // A malformed escape is not a link the app can read; the caller treats the URL as foreign.
    console.warn("[links] malformed escape", error instanceof Error ? error.message : error);
    return null;
  }
}

function parseQuery(query: string | undefined): Map<string, string> {
  const params = new Map<string, string>();
  for (const pair of (query ?? "").split("&")) {
    if (!pair) continue;
    const [rawName = "", rawValue = ""] = pair.split("=", 2);
    const name: string | null = decode(rawName);
    const value: string | null = decode(rawValue);
    if (name !== null && value !== null && !params.has(name)) params.set(name, value);
  }
  return params;
}

function parseHttpUrl(href: string): ParsedUrl | null {
  const match: RegExpMatchArray | null = href.trim().match(HTTP_URL);
  if (!match) return null;
  const [, host = "", rawPath = ""] = match;
  const unprefixed: string = rawPath.replace(LOCALE_PREFIX, "") || "/";
  const path: string =
    unprefixed.length > 1 && unprefixed.endsWith("/") ? unprefixed.slice(0, -1) : unprefixed;
  return { host: host.toLowerCase(), path, query: parseQuery(match[3]) };
}

function uuidParam(query: ReadonlyMap<string, string>, name: string): string | null {
  const value: string | undefined = query.get(name);
  return value && UUID.test(value) ? value.toLowerCase() : null;
}

/** The channel a chat path names; undefined when the path is no chat page. */
function chatPathChannel(
  path: string,
  query: ReadonlyMap<string, string>
): KaizenLinkChannel | null | undefined {
  if (path === "/daily-updates") return { purpose: ChatChannelPurpose.DAILY_UPDATES };
  if (path === "/pull-requests") return { purpose: ChatChannelPurpose.PULL_REQUESTS };
  const slug: string | undefined = path.match(/^\/chats\/([a-z0-9-]+)$/)?.[1];
  if (slug) return { slug };
  // The chat page before channels had URLs, with the channel as `channelId`.
  if (path === "/chats" || /^\/dashboard\/(?:chat|updates)$/.test(path)) {
    const id: string | null = uuidParam(query, "channelId");
    return id ? { id } : null;
  }
  return undefined;
}

/**
 * The message a URL points to, or null when it is no chat message link.
 * Links are recognised on any origin and with or without a locale segment,
 * as on the web, so a link copied on another deployment still shows as a
 * message.
 */
export function parseChatMessageLink(href: string): ChatMessageLink | null {
  const url: ParsedUrl | null = parseHttpUrl(href);
  if (!url) return null;
  const channel: KaizenLinkChannel | null | undefined = chatPathChannel(url.path, url.query);
  if (channel === undefined) return null;
  const jump: string | null = uuidParam(url.query, "jump");
  if (jump) return { href, messageId: jump, threadId: null, channel };
  const thread: string | null = uuidParam(url.query, "thread");
  const threadMessage: string | null = uuidParam(url.query, "threadMessage");
  if (thread && threadMessage) return { href, messageId: threadMessage, threadId: thread, channel };
  return null;
}

/** The message links of plain text, in order. */
export function findChatMessageLinks(text: string): ChatMessageLink[] {
  const found: ChatMessageLink[] = [];
  for (const match of text.matchAll(CHAT_URL_PATTERN)) {
    const link: ChatMessageLink | null = parseChatMessageLink(match[0]);
    if (link) found.push(link);
  }
  return found;
}

/**
 * The board view of a board link's segment. A bare board link opens the
 * default view, as the web redirects it to All; a view's page opens that
 * view; any other segment is a sprint's own page, which the app shows as the
 * board's sprint view.
 */
function boardLinkView(segment: string | undefined): KaizenLinkBoardView {
  if (segment === undefined) return DEFAULT_BOARD_VIEW;
  return (BOARD_VIEWS as readonly string[]).includes(segment)
    ? (segment as KaizenLinkBoardView)
    : "sprint";
}

/**
 * The sprint a board link names, as the web reads it: a segment that is no
 * view is the sprint's slug, All carries the slug as `sprint`, and the Sprint
 * tab takes an ID as `sprintId`. Null when the link names none, and the board
 * opens on its default sprint.
 */
function boardLinkSprint(
  segment: string | undefined,
  query: ReadonlyMap<string, string>
): KaizenLinkSprint | null {
  if (segment === undefined) return null;
  if (!(BOARD_VIEWS as readonly string[]).includes(segment)) {
    const slug: string | null = decode(segment);
    return slug ? { slug } : null;
  }
  if (segment === "all") {
    const slug: string | undefined = query.get("sprint");
    return slug ? { slug } : null;
  }
  if (segment === "sprint") {
    const id: string | undefined = query.get("sprintId");
    return id ? { id } : null;
  }
  return null;
}

/**
 * The screen a Kaizen web link names, or null when the app has none for it
 * (or the link is not Kaizen's): then it opens in the browser. Chat message
 * links are read on any origin, as their chips are; every other page only
 * on the Kaizen web host of this build.
 */
export function parseKaizenLink(
  href: string,
  kaizenWebHost: string | null
): KaizenLinkTarget | null {
  const message: ChatMessageLink | null = parseChatMessageLink(href);
  if (message) {
    return {
      kind: "channel",
      channel: message.channel,
      messageId: message.messageId,
      threadId: message.threadId,
    };
  }
  const url: ParsedUrl | null = parseHttpUrl(href);
  if (!url || !kaizenWebHost || url.host !== kaizenWebHost.toLowerCase()) return null;
  const { path, query } = url;

  const ticketKey: string | undefined = path.match(/^(?:\/board\/[^/]+)?\/ticket\/([^/]+)$/)?.[1];
  if (ticketKey === "new") return { kind: "newTicket" };
  if (ticketKey) {
    const key: string | null = normalizeTicketKey(ticketKey);
    return key ? { kind: "ticket", ticketKey: key } : null;
  }

  const board: RegExpMatchArray | null = path.match(/^\/board\/([^/]+)(?:\/([^/]+))?$/);
  if (board?.[1]) {
    const boardSlug: string | null = decode(board[1]);
    if (!boardSlug) return null;
    return {
      kind: "board",
      boardSlug,
      view: boardLinkView(board[2]),
      sprint: boardLinkSprint(board[2], query),
    };
  }

  if (path === "/daily-updates/new") return { kind: "newUpdate" };
  if (path === "/pull-requests/new") return { kind: "sharePullRequest" };
  // An edit form opens its channel: the app edits an update or a share from its card.
  if (/^\/daily-updates\/[^/]+\/edit$/.test(path)) {
    return {
      kind: "channel",
      channel: { purpose: ChatChannelPurpose.DAILY_UPDATES },
      messageId: null,
      threadId: null,
    };
  }
  if (/^\/pull-requests\/[^/]+\/edit$/.test(path)) {
    return {
      kind: "channel",
      channel: { purpose: ChatChannelPurpose.PULL_REQUESTS },
      messageId: null,
      threadId: null,
    };
  }

  const channel: KaizenLinkChannel | null | undefined = chatPathChannel(path, query);
  if (channel !== undefined) {
    if (channel === null) return { kind: "chatHome" };
    return { kind: "channel", channel, messageId: null, threadId: uuidParam(query, "thread") };
  }

  switch (path) {
    case "/":
    case "/home":
      return { kind: "home" };
    case "/incidents":
      return { kind: "incidents" };
    case "/profile":
      return { kind: "profile" };
    case "/settings":
      return { kind: "settings" };
    default:
      return null;
  }
}

/**
 * The listed channel a link names. A link without a channel (an old link)
 * matches none; its message still names its channel once loaded.
 */
export function findLinkChannel<T extends { id: string; slug: string; purpose: string }>(
  channels: readonly T[],
  target: KaizenLinkChannel
): T | undefined {
  if ("id" in target) return channels.find((channel) => channel.id === target.id);
  if ("slug" in target) return channels.find((channel) => channel.slug === target.slug);
  return channels.find((channel) => channel.purpose === target.purpose);
}

/**
 * The ID of the board's sprint a link names, or null when the link names
 * none or the board no longer has it (deleted, or the slug of another
 * board's sprint): the board then opens on its default sprint, as the web
 * does for an unknown `sprintId`.
 */
export function linkSprintId(
  sprints: readonly { id: string; slug?: string | null }[],
  target: KaizenLinkSprint | null
): string | null {
  if (!target) return null;
  const found = sprints.find((sprint) =>
    "id" in target ? sprint.id === target.id : sprint.slug === target.slug
  );
  return found?.id ?? null;
}

/**
 * An app path for a Kaizen web link that needs no lookup, for the system
 * deep-link handler, which cannot wait for data: a ticket, a form or a fixed
 * screen. Null for a board, a channel or a message, whose slug or channel is
 * looked up by the link screen.
 */
export function kaizenLinkDirectPath(target: KaizenLinkTarget): string | null {
  switch (target.kind) {
    case "ticket":
      return `/ticket/${encodeURIComponent(target.ticketKey)}`;
    case "newTicket":
      return "/ticket/new";
    case "chatHome":
      return "/chat";
    case "newUpdate":
      return "/chat/new-update";
    case "sharePullRequest":
      return "/chat/share-pr";
    case "home":
      return "/home";
    case "incidents":
      return "/incidents";
    case "profile":
      return "/account/profile";
    case "settings":
      return "/account/settings";
    case "board":
    case "channel":
      return null;
  }
}

/** The app path of the link screen, which looks up and opens a board or a chat link. */
export const LINK_SCREEN_PATH = "/link" as const;

/** The link screen for `href`, as a plain path for the system deep-link handler. */
export function linkScreenPath(href: string): string {
  return `${LINK_SCREEN_PATH}?url=${encodeURIComponent(href)}`;
}

/** Where a chat link opens in the app. */
export type ChatLinkHref =
  | {
      pathname: "/chat/channel/[channelId]";
      params: { channelId: string; message?: string; thread?: string };
    }
  | { pathname: "/chat/thread/[threadId]"; params: { threadId: string; channelId: string } }
  | { pathname: "/chat/updates" }
  | { pathname: "/chat/pull-requests" };

/**
 * The screen of a chat link once its channel is known. A message opens in
 * the plain channel screen, which pages to any message and opens a thread
 * over itself, as a search result does; a thread without a message opens
 * the thread; a channel alone opens its own screen.
 */
export function chatLinkHref(
  channel: { id: string; purpose: string },
  messageId: string | null,
  threadId: string | null
): ChatLinkHref {
  if (messageId) {
    return {
      pathname: "/chat/channel/[channelId]",
      params: threadId
        ? { channelId: channel.id, message: messageId, thread: threadId }
        : { channelId: channel.id, message: messageId },
    };
  }
  if (threadId) {
    return { pathname: "/chat/thread/[threadId]", params: { threadId, channelId: channel.id } };
  }
  if (channel.purpose === ChatChannelPurpose.DAILY_UPDATES) return { pathname: "/chat/updates" };
  if (channel.purpose === ChatChannelPurpose.PULL_REQUESTS) {
    return { pathname: "/chat/pull-requests" };
  }
  return { pathname: "/chat/channel/[channelId]", params: { channelId: channel.id } };
}

/** What opening a chat link does once its channel is looked up. */
export type ChatLinkOutcome =
  | { kind: "open"; href: ChatLinkHref }
  /** No such channel among the reader's: deleted, or private without the reader on it. */
  | { kind: "channelNotFound" }
  | { kind: "messageGone" };

/**
 * The outcome of a chat link by the channel it names in the reader's channel
 * list. A channel the reader is listed but not a member of (a private one an
 * owner or admin sees without being on it) opens its screen, which says so;
 * its messages and threads cannot be read, so the link's message and thread
 * are not carried. The server lists no other channel the reader cannot read,
 * so a channel missing from the list is not found.
 */
export function chatLinkOutcome(
  channel: { id: string; purpose: string; member: boolean } | undefined,
  target: { messageId: string | null; threadId: string | null }
): ChatLinkOutcome {
  if (!channel) return { kind: "channelNotFound" };
  if (!channel.member) {
    return {
      kind: "open",
      href: { pathname: "/chat/channel/[channelId]", params: { channelId: channel.id } },
    };
  }
  return { kind: "open", href: chatLinkHref(channel, target.messageId, target.threadId) };
}

/** A refused lookup of a link's message: the server's error code and the values it sent along. */
export interface ChatLinkLookupRefusal {
  code: string | undefined;
  errorValues: Record<string, unknown>;
}

/**
 * The outcome of a link whose message was refused when it was looked up to
 * find its channel, by the server's error code. `KAI_NOT_015` says the
 * reader cannot read the message's channel and names it (`channelId`): a
 * listed channel the reader is not on (a private one an owner or admin sees)
 * opens its screen, which says so; any other channel is not found, deleted
 * or hidden from the reader. `KAI_NOT_013` says the message is gone. Null for
 * any other failure, which says nothing about the link.
 */
export function chatLinkLookupOutcome(
  refusal: ChatLinkLookupRefusal,
  channels: readonly { id: string; purpose: string; member: boolean }[]
): ChatLinkOutcome | null {
  if (refusal.code === CHAT_ERROR_CODES.channelNotFound) {
    const channelId: unknown = refusal.errorValues.channelId;
    const listed = channels.find((channel) => channel.id === channelId);
    if (listed && !listed.member) {
      return chatLinkOutcome(listed, { messageId: null, threadId: null });
    }
    return { kind: "channelNotFound" };
  }
  if (refusal.code === CHAT_ERROR_CODES.messageNotFound) return { kind: "messageGone" };
  return null;
}

/**
 * The message a link points to inside the conversation already on screen
 * (the same channel, and the same thread or none), which that list scrolls
 * to itself instead of opening a second copy of the screen; null otherwise.
 */
export function linkedMessageInConversation(
  target: { channel: KaizenLinkChannel | null; messageId: string | null; threadId: string | null },
  channel: { id: string; slug: string; purpose: string } | undefined,
  threadId: string | null
): string | null {
  if (!target.messageId || !target.channel || !channel) return null;
  if (target.threadId !== threadId) return null;
  return findLinkChannel([channel], target.channel) ? target.messageId : null;
}
