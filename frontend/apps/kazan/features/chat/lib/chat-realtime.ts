import { realtimeOrganizationTopic } from "@oba/shared/realtime";
import type { ChatMessageResponse } from "../schemas";

/**
 * The organization's chat signals: presence, and CHANNEL_CHANGED for public
 * channels. The server accepts it only from a session of that organization.
 */
export function chatOrganizationDestination(organizationId: string): string {
  return realtimeOrganizationTopic(organizationId, "/chat");
}

/**
 * One channel's signals: messages, reactions, pins, threads and typing. The
 * server accepts it only from a session that can read the channel, and ends
 * the subscription when the reader loses the channel.
 */
export function chatChannelDestination(organizationId: string, channelId: string): string {
  return realtimeOrganizationTopic(organizationId, `/chat/channels/${channelId}`);
}

/**
 * The reader's own chat signals: unread counts, thread membership, and
 * CHANNEL_CHANGED for private channels the reader is or was on (and for every
 * private channel when the reader is OWNER or ADMIN).
 */
export const CHAT_USER_DESTINATION = "/user/queue/chat";

/**
 * Where the composer sends the typing signal, which the server relays to the
 * channel's topic. The server drops it from a GUEST, who cannot send.
 */
export const CHAT_TYPING_DESTINATION = "/app/chat/typing";

// ── Message versions ────────────────────────────────────────────────

/** An ISO instant split into what `Date.parse` reads and the fraction it would round. */
const INSTANT = /^(.*T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$/;

function instantParts(version: string): [number, number] {
  const match = INSTANT.exec(version);
  if (!match) return [Date.parse(version), 0];
  const [, seconds, fraction = "", zone] = match;
  return [Date.parse(`${seconds}${zone}`), Number(fraction.padEnd(9, "0"))];
}

/**
 * Orders two message versions: negative when `a` is older, 0 when equal,
 * positive when newer. The server writes instants with up to nine fraction
 * digits and drops trailing zeros, so neither a string comparison nor
 * `Date.parse` (milliseconds) orders two edits within one millisecond. A
 * missing version (the deleted placeholder of a thread root has none) is the
 * oldest, so any versioned copy replaces it.
 */
export function compareChatVersions(a: string | null | undefined, b: string | null | undefined): number {
  if (!a || !b) return (a ? 1 : 0) - (b ? 1 : 0);
  const [aSeconds, aFraction] = instantParts(a);
  const [bSeconds, bFraction] = instantParts(b);
  return aSeconds - bSeconds || aFraction - bFraction;
}

/**
 * True when `candidate` is strictly newer than `held`. A missing version
 * counts as the oldest, so a versioned candidate replaces a copy without one
 * and is newer than no signalled version at all.
 */
export function isNewerChatVersion(candidate: string | null | undefined, held: string | null | undefined): boolean {
  return compareChatVersions(candidate, held) > 0;
}

// Per message, the newest version a signal announced and the ids deleted in
// this session. Both are cleared after a reconnect, when the lists reload
// from the server and every signal of the gap is lost anyway.
const signalledVersions = new Map<string, string>();
const deletedMessageIds = new Set<string>();

/** Records the version a signal announced, keeping the newest per message. */
export function rememberChatMessageVersion(messageId: string, version: string): void {
  if (isNewerChatVersion(version, signalledVersions.get(messageId))) signalledVersions.set(messageId, version);
}

/** The newest version a signal announced for the message; undefined when none did. */
export function signalledChatMessageVersion(messageId: string): string | undefined {
  return signalledVersions.get(messageId);
}

/** Marks a message deleted, so a payload of it that arrives late is dropped. */
export function rememberChatMessageDeleted(messageId: string): void {
  deletedMessageIds.add(messageId);
  signalledVersions.delete(messageId);
}

export function isChatMessageDeleted(messageId: string): boolean {
  return deletedMessageIds.has(messageId);
}

export function clearChatMessageVersions(): void {
  signalledVersions.clear();
  deletedMessageIds.clear();
}

// ── Reader fields of a payload ──────────────────────────────────────

export interface ChatReader {
  /** The reader's Oba user ID. */
  userId: string;
  /**
   * For a thread message: true when the reader holds the thread's detail and
   * is a member of the thread, the only case in which a thread `@everyone`
   * is known to reach the reader on the client.
   */
  threadMember: boolean;
}

/**
 * The fields of a realtime payload that the server leaves neutral, computed
 * for the reader. `@here` never needs it: a message with `@here` comes
 * without a payload and is fetched. A thread `@everyone` while the reader
 * holds no thread detail shows as no mention until the thread is opened; the
 * unread counts, which the server computes, still count it.
 */
export function withChatReaderFields(message: ChatMessageResponse, reader: ChatReader): ChatMessageResponse {
  const mine = message.author?.userId === reader.userId;
  const everyoneReachesMe = message.mentionsEveryone && (!message.threadId || reader.threadMember);
  const mentionsMe = !mine && (message.mentionedUserIds.includes(reader.userId) || everyoneReachesMe);
  const repliesToMe =
    !mine && !!message.replyTo && !message.replyTo.deleted && message.replyTo.authorId === reader.userId;
  const reactions = message.reactions.map((reaction) => ({
    ...reaction,
    reactedByMe: reaction.userIds.includes(reader.userId),
  }));
  return { ...message, mentionsMe, repliesToMe, reactions };
}
