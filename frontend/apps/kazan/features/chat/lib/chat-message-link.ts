/**
 * Links to a chat message, as "Copy link" writes them: the channel's URL
 * (`/chats/<slug>`, `/daily-updates` or `/pull-requests`) with
 * `jump=<messageId>` for a channel message, or `thread=<threadId>` and
 * `threadMessage=<messageId>` for a message of a thread. Message text keeps
 * links written before channels had URLs of their own, the chat page
 * `/dashboard/chat` (earlier `/dashboard/updates`) with the channel as
 * `channelId` or with no channel at all; they still show as message chips.
 * Links are recognised on any origin and with or without a locale segment,
 * so a link copied on another deployment or in another language shows as a
 * chip too. The text of a message keeps the URL itself; the chip is
 * presentation only.
 */
import { ChatChannelPurpose } from "../schemas";
import { CHATS_PATH, DAILY_UPDATES_PATH, LEGACY_CHANNEL_ID_PARAM, PULL_REQUESTS_PATH } from "./chat-route";

const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
/** A leading locale segment such as `/en` or `/nl-BE`, which a link may carry. */
const LOCALE_PREFIX = /^\/[a-z]{2}(?:-[A-Za-z]{2})?(?=\/)/;
/** The chat page before channels had URLs; its channel is the `channelId` parameter. */
const LEGACY_CHAT_PATH = /^\/dashboard\/(?:chat|updates)$/;
/** A channel URL by slug. Slugs hold only lower-case letters, digits and hyphens. */
const CHANNEL_SLUG_PATH = new RegExp(`^${CHATS_PATH}/([a-z0-9-]+)$`);
/** Candidate URLs in plain text; each is checked with `parseChatMessageLink`. */
export const CHAT_URL_PATTERN = /https?:\/\/[^\s<>]*[^\s<>.,:;"'!?)\]]/g;

/**
 * The channel a link names: by ID in an old link, by slug for a GENERAL
 * channel, by purpose for the one daily updates or pull requests channel.
 */
export type ChatMessageLinkChannel = { id: string } | { slug: string } | { purpose: ChatChannelPurpose };

export interface ChatMessageLink {
  /** The URL as written in the message. */
  href: string;
  messageId: string;
  /** The thread of a thread message link; null for a channel message link. */
  threadId: string | null;
  /** The channel of the message; null for an old link written before links named it. */
  channel: ChatMessageLinkChannel | null;
}

/** The channel a link's path names; undefined when the path is no chat message link's. */
function linkChannel(pathname: string, query: URLSearchParams): ChatMessageLinkChannel | null | undefined {
  // One trailing slash is allowed, as the chat pages accept it too.
  const unprefixed = pathname.replace(LOCALE_PREFIX, "");
  const path = unprefixed.length > 1 && unprefixed.endsWith("/") ? unprefixed.slice(0, -1) : unprefixed;
  if (path === DAILY_UPDATES_PATH) return { purpose: ChatChannelPurpose.DAILY_UPDATES };
  if (path === PULL_REQUESTS_PATH) return { purpose: ChatChannelPurpose.PULL_REQUESTS };
  const slug = CHANNEL_SLUG_PATH.exec(path)?.[1];
  if (slug) return { slug };
  if (!LEGACY_CHAT_PATH.test(path)) return undefined;
  const id = query.get(LEGACY_CHANNEL_ID_PARAM);
  return id && UUID.test(id) ? { id: id.toLowerCase() } : null;
}

/** The message a URL points to, or null when it is not a chat message link. */
export function parseChatMessageLink(href: string): ChatMessageLink | null {
  // Text that is no valid URL is no message link; it renders as typed.
  if (!URL.canParse(href)) return null;
  const url = new URL(href);
  if (url.protocol !== "http:" && url.protocol !== "https:") return null;
  const channel = linkChannel(url.pathname, url.searchParams);
  if (channel === undefined) return null;
  const jump = url.searchParams.get("jump");
  if (jump && UUID.test(jump)) return { href, messageId: jump.toLowerCase(), threadId: null, channel };
  const thread = url.searchParams.get("thread");
  const threadMessage = url.searchParams.get("threadMessage");
  if (thread && threadMessage && UUID.test(thread) && UUID.test(threadMessage)) {
    return { href, messageId: threadMessage.toLowerCase(), threadId: thread.toLowerCase(), channel };
  }
  return null;
}

/** The message links of plain text with their offsets, in order. */
export function findChatMessageLinks(text: string): { from: number; to: number; link: ChatMessageLink }[] {
  const found: { from: number; to: number; link: ChatMessageLink }[] = [];
  for (const match of text.matchAll(CHAT_URL_PATTERN)) {
    const link = parseChatMessageLink(match[0]);
    if (!link || match.index === undefined) continue;
    found.push({ from: match.index, to: match.index + match[0].length, link });
  }
  return found;
}
