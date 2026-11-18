import { z } from "zod";
import { attachmentResponseSchema } from "@oba/shared/features/attachments/schemas";
import { organizationRoleSchema } from "@oba/shared/features/users/schema";

/*
 * Wire shapes of the Kazan chat (`/kazan/chat/*`) and of its realtime
 * signals, the same as the web app reads. Instants are ISO strings with `Z`.
 */

// ── Limits (mirror the chat requests and validator on the server) ───

export const CHAT_MESSAGE_MAX_LENGTH = 2000;
/** The composer shows its character counter from this length on. */
export const CHAT_MESSAGE_COUNTER_FROM = 1800;
export const CHAT_MAX_USER_MENTIONS = 20;
export const CHAT_MAX_TICKET_REFS = 10;
export const CHAT_CLIENT_NONCE_MAX = 64;
export const CHAT_EMOJI_MAX = 64;
/** Messages per page; also the most the server returns for any chat list. */
export const CHAT_PAGE_SIZE = 50;
/** Unread counts are capped at this value by the server; the badge shows "99+". */
export const CHAT_UNREAD_CAP = 100;
/** The server accepts one typing signal per session in this interval and drops faster ones. */
export const CHAT_TYPING_INTERVAL_MS = 3000;
/** A typing indicator ends this long after the member's last signal. */
export const CHAT_TYPING_TIMEOUT_MS = 8000;
/** Consecutive messages of one author within this window share one header. */
export const CHAT_GROUP_WINDOW_MS = 7 * 60 * 1000;
/** A thread title is 1 to 100 characters. */
export const CHAT_THREAD_TITLE_MAX = 100;
/** A channel name is at most 80 characters, stored trimmed. */
export const CHAT_CHANNEL_NAME_MAX = 80;
/** The most member IDs one channel request accepts. */
export const CHAT_CHANNEL_MAX_MEMBER_IDS = 200;
/** The inactivity windows a thread archives after, in minutes: 1 hour, 24 hours, 3 days, 1 week. */
export const CHAT_THREAD_ARCHIVE_OPTIONS = [60, 1440, 4320, 10080] as const;
/** The window of a new thread, and the one preselected for a window the app does not offer. */
export const CHAT_THREAD_DEFAULT_ARCHIVE_MINUTES = 10080;
/** Files of one message. */
export const CHAT_MAX_FILES = 10;
/** The server's multipart limit for one send, files and message part together. */
export const CHAT_MAX_REQUEST_BYTES = 12 * 1024 * 1024;
/** A search runs from this many characters on, up to the maximum. */
export const CHAT_SEARCH_MIN = 2;
export const CHAT_SEARCH_MAX = 100;
export const CHAT_SEARCH_PAGE_SIZE = 25;

// ── Error codes the client reacts to ────────────────────────────────

export const CHAT_ERROR_CODES = {
  /** Send rate exceeded (429). */
  rateLimited: "GEN_API_004",
  /** Text, mention, reference or file limits (422). */
  messageInvalid: "KAZ_VAL_043",
  /**
   * A mentioned user cannot read the conversation, or a user added to a
   * channel is not an active member of the organization (422).
   */
  mentionNotMember: "KAZ_VAL_044",
  /** Channel absent, deleted, or private without the reader on its list (404). */
  channelNotFound: "KAZ_NOT_015",
  /** Message absent or deleted, or not in the conversation a cursor names (404). */
  messageNotFound: "KAZ_NOT_013",
  /** A delete by a reader who is neither the author nor an organization OWNER or ADMIN (403). */
  deleteRefused: "KAZ_SEC_007",
  /** The thread is closed (409): a send or an edit in it is refused until it is reopened. */
  threadClosed: "KAZ_STA_014",
  /** Thread absent, or of another organization (404). */
  threadNotFound: "KAZ_NOT_014",
  /** The message already has a thread (409); the error values carry its `threadId`. */
  threadExists: "KAZ_STA_013",
  /** Another live channel holds the purpose (409). */
  channelPurposeTaken: "KAZ_STA_016",
  /** A purpose other than GENERAL on a private channel (422). */
  channelPurposePrivate: "KAZ_VAL_053",
  /** `memberIds` sent in a channel update that does not make the channel private (422). */
  channelMembersNotExpected: "KAZ_VAL_051",
  /** Members added to or removed from a public channel (422). */
  channelNotPrivate: "KAZ_VAL_052",
  /** Removal of another member from a private channel by a reader who is not an OWNER or ADMIN (403). */
  channelMemberRemoveRefused: "KAZ_SEC_011",
  /** The organization-level refusal of a reader who is no longer an OWNER or ADMIN (403). */
  organizationRoleRefused: "ORG_SEC_003",
  /** A thread change by a reader who is neither its creator nor an OWNER or ADMIN (403). */
  threadManageRefused: "KAZ_SEC_005",
  /** A thread on a root that is not a live channel message, or an archive window not offered (422). */
  threadArchiveWindowInvalid: "KAZ_VAL_050",
} as const;

// ── Tokens ──────────────────────────────────────────────────────────
// The same patterns as the server's ChatTokenParser. Code is masked first:
// nothing inside an inline code span or a fenced block is a token, so it is
// neither a mention nor counted against the limits.

const UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
export const CHAT_MENTION_EVERYONE = "everyone";
export const CHAT_MENTION_HERE = "here";
/** Group 1 is `everyone`, `here` or a user ID. */
export const CHAT_USER_TOKEN_PATTERN = new RegExp(`<@(everyone|here|${UUID_PATTERN})>`, "g");
/** Group 1 is a ticket ID. */
export const CHAT_TICKET_TOKEN_PATTERN = new RegExp(`<#(${UUID_PATTERN})>`, "g");
/** A fence without a closing fence runs to the end of the text. */
const FENCED_CODE_PATTERN = /```[\s\S]*?(?:```|$(?![\s\S]))/g;
const INLINE_CODE_PATTERN = /`[^`\n]+`/g;

export const chatUserToken = (userId: string): string => `<@${userId}>`;
export const chatTicketToken = (ticketId: string): string => `<#${ticketId}>`;

/** Replaces code with spaces, so positions in the result are positions in the original. */
export function maskChatCode(content: string): string {
  const blank = (match: string): string => " ".repeat(match.length);
  return content.replace(FENCED_CODE_PATTERN, blank).replace(INLINE_CODE_PATTERN, blank);
}

export interface ChatTokens {
  /** Users mentioned directly, distinct, in order of first mention. */
  userIds: string[];
  mentionsEveryone: boolean;
  mentionsHere: boolean;
  /** Tickets referenced, distinct, in order of first reference. */
  ticketIds: string[];
}

/** Reads the tokens outside code; a repeated token counts once, as on the server. */
export function parseChatTokens(content: string): ChatTokens {
  const masked: string = maskChatCode(content);
  const userIds = new Set<string>();
  let mentionsEveryone = false;
  let mentionsHere = false;
  for (const match of masked.matchAll(CHAT_USER_TOKEN_PATTERN)) {
    const target: string | undefined = match[1];
    if (!target) continue;
    if (target === CHAT_MENTION_EVERYONE) mentionsEveryone = true;
    else if (target === CHAT_MENTION_HERE) mentionsHere = true;
    else userIds.add(target.toLowerCase());
  }
  const ticketIds = new Set<string>();
  for (const match of masked.matchAll(CHAT_TICKET_TOKEN_PATTERN)) {
    const ticketId: string | undefined = match[1];
    if (ticketId) ticketIds.add(ticketId.toLowerCase());
  }
  return { userIds: [...userIds], mentionsEveryone, mentionsHere, ticketIds: [...ticketIds] };
}

/** Why the composer cannot send its text, or null when it can. */
export type ChatComposerProblem = "empty" | "tooLong" | "tooManyMentions" | "tooManyTicketRefs";

/**
 * The composer's check before a send, the limits the server answers 422
 * `KAZ_VAL_043` for. Length counts raw characters, tokens included.
 */
export function chatComposerProblem(content: string): ChatComposerProblem | null {
  if (content.trim().length === 0) return "empty";
  if (content.length > CHAT_MESSAGE_MAX_LENGTH) return "tooLong";
  const tokens: ChatTokens = parseChatTokens(content);
  if (tokens.userIds.length > CHAT_MAX_USER_MENTIONS) return "tooManyMentions";
  if (tokens.ticketIds.length > CHAT_MAX_TICKET_REFS) return "tooManyTicketRefs";
  return null;
}

// ── Enum Schemas ────────────────────────────────────────────────────

/**
 * What a channel is for. `DAILY_UPDATES` and `PULL_REQUESTS` each have at
 * most one live, public channel per organization.
 */
export const ChatChannelPurposeSchema = z.enum(["GENERAL", "DAILY_UPDATES", "PULL_REQUESTS"]);
export type ChatChannelPurpose = z.infer<typeof ChatChannelPurposeSchema>;
// eslint-disable-next-line @typescript-eslint/no-redeclare -- reason: the enum's type and its value map share one name, so callers write `ChatChannelPurpose.GENERAL` and annotate with the same identifier.
export const ChatChannelPurpose = ChatChannelPurposeSchema.enum;

export const ChatChannelVisibilitySchema = z.enum(["PUBLIC", "PRIVATE"]);
export type ChatChannelVisibility = z.infer<typeof ChatChannelVisibilitySchema>;
// eslint-disable-next-line @typescript-eslint/no-redeclare -- reason: the enum's type and its value map share one name.
export const ChatChannelVisibility = ChatChannelVisibilitySchema.enum;

/** `ARCHIVED` covers an archive by hand and one by inactivity; `CLOSED` is read-only. */
export const ChatThreadStatusSchema = z.enum(["ACTIVE", "CLOSED", "ARCHIVED"]);
export type ChatThreadStatus = z.infer<typeof ChatThreadStatusSchema>;
// eslint-disable-next-line @typescript-eslint/no-redeclare -- reason: the enum's type and its value map share one name.
export const ChatThreadStatus = ChatThreadStatusSchema.enum;

export const ChatThreadArchiveMinutesSchema = z.literal(CHAT_THREAD_ARCHIVE_OPTIONS);
export type ChatThreadArchiveMinutes = (typeof CHAT_THREAD_ARCHIVE_OPTIONS)[number];

// ── Request Schemas ─────────────────────────────────────────────────

/** Keyset cursor of a message list: at most one is set; none asks for the newest page. */
export const chatMessageCursorSchema = z.object({
  before: z.string().optional(),
  after: z.string().optional(),
  around: z.string().optional(),
});

/** The JSON part `message` of a send; files travel as separate `files` parts. */
export const chatMessageCreateRequestSchema = z.object({
  channelId: z.string(),
  content: z.string().max(CHAT_MESSAGE_MAX_LENGTH).nullable().optional(),
  threadId: z.string().nullable().optional(),
  replyToMessageId: z.string().nullable().optional(),
  clientNonce: z.string().min(1).max(CHAT_CLIENT_NONCE_MAX),
});

/** A thread on a live channel message; a null title takes the server's default from the root. */
export const chatThreadCreateRequestSchema = z.object({
  rootMessageId: z.string(),
  title: z.string().min(1).max(CHAT_THREAD_TITLE_MAX).nullable(),
});

/** A private channel's members are its creator plus `memberIds`; a public channel takes none. */
export const chatChannelCreateRequestSchema = z.object({
  name: z.string().trim().min(1).max(CHAT_CHANNEL_NAME_MAX),
  visibility: ChatChannelVisibilitySchema,
  purpose: ChatChannelPurposeSchema,
  memberIds: z.array(z.string()).optional(),
});

/**
 * A full update of a channel (OWNER or ADMIN). `memberIds` is taken only
 * when the update makes a public channel private, and its members are then
 * the admin plus `memberIds`; any other update with members is refused
 * (422 `KAZ_VAL_051`). A channel made public loses its member list.
 */
export const chatChannelUpdateRequestSchema = z.object({
  name: z.string().trim().min(1).max(CHAT_CHANNEL_NAME_MAX),
  visibility: ChatChannelVisibilitySchema,
  purpose: ChatChannelPurposeSchema,
  memberIds: z.array(z.string()).max(CHAT_CHANNEL_MAX_MEMBER_IDS).optional(),
});

/** Adds users to a private channel's member list (422 `KAZ_VAL_052` for a public channel). */
export const chatChannelMembersRequestSchema = z.object({
  userIds: z.array(z.string()).min(1).max(CHAT_CHANNEL_MAX_MEMBER_IDS),
});

/** Renames a thread and sets how long it may stay idle before it archives. */
export const chatThreadUpdateRequestSchema = z.object({
  title: z.string().trim().min(1).max(CHAT_THREAD_TITLE_MAX),
  autoArchiveMinutes: ChatThreadArchiveMinutesSchema,
});

/** `threadId` null marks the channel itself. The server only moves the position forward. */
export const chatReadStateRequestSchema = z.object({
  channelId: z.string(),
  threadId: z.string().nullable(),
  lastReadMessageId: z.string(),
});

// ── Response Schemas ────────────────────────────────────────────────

/**
 * A stored file. An image of a type the server can read carries its pixel
 * `width` and `height`, both or neither, so the list reserves its box
 * before the image loads; other files and older uploads carry null.
 */
export const chatAttachmentResponseSchema = attachmentResponseSchema.extend({
  width: z.number().int().positive().nullable().optional(),
  height: z.number().int().positive().nullable().optional(),
});

/** The author of a message (`TicketCreatedByResponse` on the server). */
export const chatAuthorResponseSchema = z.object({
  userId: z.string(),
  email: z.string(),
  firstName: z.string().nullable().optional(),
  lastName: z.string().nullable().optional(),
});

/** A user referenced by a page; `member` false renders "unknown member". */
export const chatUserResponseSchema = z.object({
  userId: z.string(),
  firstName: z.string().nullable(),
  lastName: z.string().nullable(),
  email: z.string().nullable(),
  member: z.boolean(),
});

export const chatReplyPreviewResponseSchema = z.object({
  messageId: z.string(),
  deleted: z.boolean(),
  /** Null when the quoted message was deleted. */
  authorId: z.string().nullable(),
  /** First 200 characters with tokens rendered; empty when deleted. */
  snippet: z.string(),
  attachments: z.array(chatAttachmentResponseSchema).default([]),
});

/** One `<#ticketId>` token with the snapshot taken at send time. */
export const chatTicketRefResponseSchema = z.object({
  /** Null once the ticket row was removed. */
  ticketId: z.string().nullable(),
  boardId: z.string(),
  ticketKey: z.string(),
  ticketTitle: z.string(),
  /** True when the reader is a member of the ticket's board and the ticket is live. */
  canOpen: z.boolean(),
});

/** A ticket key typed as plain text that the reader can open. */
export const chatLinkedTicketResponseSchema = z.object({
  ticketKey: z.string(),
  ticketId: z.string(),
  boardId: z.string(),
});

export const chatReactionSummaryResponseSchema = z.object({
  emoji: z.string(),
  count: z.number(),
  reactedByMe: z.boolean(),
  /** The first 10 users who reacted, in reaction order. */
  userIds: z.array(z.string()),
});

export const chatThreadSummaryResponseSchema = z.object({
  threadId: z.string(),
  title: z.string(),
  messageCount: z.number(),
  lastMessageAt: z.string(),
  status: ChatThreadStatusSchema,
  memberPreviewIds: z.array(z.string()),
  memberCount: z.number(),
});

/**
 * A daily update or pull request share carried by its chat entry. The chat
 * list shows only its one-line summary, so the rest of the shape is kept as
 * sent and not checked here.
 */
export const chatEntryCardSchema = z.looseObject({
  summaryText: z.string().nullable().optional(),
});

export const chatMessageResponseSchema = z.object({
  id: z.string(),
  organizationId: z.string(),
  /** A thread message carries its thread's channel. */
  channelId: z.string(),
  /** Null for a message of the channel itself. */
  threadId: z.string().nullable(),
  /** Set for a daily update entry, which is read-only in the chat. */
  dailyUpdateId: z.string().nullable(),
  /** Set for a pull request share entry, which is read-only in the chat. */
  pullRequestShareId: z.string().nullable(),
  /** True for the placeholder of a deleted thread root: no author, empty content, `thread` set. */
  deleted: z.boolean(),
  author: chatAuthorResponseSchema.nullable(),
  /** Markdown with tokens as stored; empty for a daily update or a placeholder. */
  content: z.string(),
  createdAt: z.string(),
  editedAt: z.string().nullable(),
  pinnedAt: z.string().nullable(),
  /** The sender's nonce, only in the response to the send that created the message. */
  clientNonce: z.string().nullable(),
  replyTo: chatReplyPreviewResponseSchema.nullable(),
  mentionedUserIds: z.array(z.string()),
  mentionsEveryone: z.boolean(),
  mentionsHere: z.boolean(),
  /** Computed for the reader: direct mention, `@everyone`, or `@here` while the reader was online. */
  mentionsMe: z.boolean(),
  /** Computed for the reader: someone else's message quoting a live message of the reader. */
  repliesToMe: z.boolean(),
  ticketRefs: z.array(chatTicketRefResponseSchema),
  linkedTickets: z.array(chatLinkedTicketResponseSchema),
  attachments: z.array(chatAttachmentResponseSchema),
  reactions: z.array(chatReactionSummaryResponseSchema),
  thread: chatThreadSummaryResponseSchema.nullable(),
  dailyUpdate: chatEntryCardSchema.nullable(),
  pullRequestShare: chatEntryCardSchema.nullable(),
  /** Every author, mentioned and quoted user of the message, by user ID. */
  users: z.record(z.string(), chatUserResponseSchema),
  /**
   * The row's last change (ISO instant with nanoseconds). A copy is only
   * replaced by a newer one; null for a deleted thread root placeholder.
   */
  version: z.string().nullable(),
});

/**
 * The root message of a thread. A root whose row is gone comes back as the
 * deleted placeholder without its channel and creation time, so both may be
 * null here; a message of a channel or thread page always carries them.
 */
export const chatThreadRootMessageSchema = chatMessageResponseSchema.extend({
  channelId: z.string().nullable(),
  createdAt: z.string().nullable(),
});

/** A keyset page, oldest first for channel and thread lists. */
export const chatMessagePageSchema = z.object({
  items: z.array(chatMessageResponseSchema),
  hasOlder: z.boolean(),
  hasNewer: z.boolean(),
});

/** The reader's unread state in one channel or one thread. */
export const chatConversationUnreadResponseSchema = z.object({
  /** The channel ID or the thread ID. */
  id: z.string(),
  channelId: z.string(),
  unread: z.number(),
  /** The unread messages among `unread` that notify the reader. */
  mentionCount: z.number(),
  /** Null before the reader's first read position. */
  lastReadMessageId: z.string().nullable(),
  lastReadAt: z.string().nullable(),
});

/**
 * `ChatUnreadResponse`: the reader's unread state in every readable channel
 * and every active thread the reader is a member of. Counts except
 * `mentionTotal` are capped at `CHAT_UNREAD_CAP`.
 */
export const chatUnreadSchema = z.object({
  channels: z.array(chatConversationUnreadResponseSchema),
  threads: z.array(chatConversationUnreadResponseSchema),
  total: z.number().int().nonnegative(),
  mentionTotal: z.number().int().nonnegative(),
});

/**
 * A channel the reader sees in the list. OWNER and ADMIN see every private
 * channel, with `member` false until they add themselves.
 */
export const chatChannelResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string(),
  type: z.string(),
  purpose: ChatChannelPurposeSchema,
  visibility: ChatChannelVisibilitySchema,
  member: z.boolean(),
  memberCount: z.number(),
  createdAt: z.string(),
});

/**
 * A thread as one reader sees it. `status` is effective: an active thread
 * idle longer than its window reads ARCHIVED. `canManage` (the creator, an
 * OWNER or an ADMIN) allows renaming and status changes.
 */
export const chatThreadResponseSchema = z.object({
  id: z.string(),
  channelId: z.string(),
  rootMessageId: z.string(),
  /** The deleted placeholder when the root was deleted. */
  rootMessage: chatThreadRootMessageSchema,
  title: z.string(),
  createdBy: z.string(),
  messageCount: z.number(),
  lastMessageAt: z.string(),
  autoArchiveMinutes: z.number(),
  status: ChatThreadStatusSchema,
  statusChangedAt: z.string().nullable(),
  /** Null when never changed, and for a thread archived by inactivity. */
  statusChangedBy: chatUserResponseSchema.nullable(),
  member: z.boolean(),
  members: z.array(chatUserResponseSchema),
  canManage: z.boolean(),
});

/**
 * The thread's root as a message of its channel, or null for a placeholder
 * whose row is gone: without a channel and a creation time it has no place in
 * a list, so it is treated as a root that is not loaded.
 */
export function threadRootAsMessage(thread: ChatThreadResponse): ChatMessageResponse | null {
  const root: ChatThreadRootMessage = thread.rootMessage;
  if (root.channelId === null || root.createdAt === null) return null;
  return { ...root, channelId: root.channelId, createdAt: root.createdAt };
}

/** A keyset page of a channel's threads, most recent activity first. */
export const chatThreadPageSchema = z.object({
  items: z.array(chatThreadResponseSchema),
  /** True when more threads follow the last item. */
  hasMore: z.boolean(),
});

/**
 * A ticket the `#` suggestion offers: the fields of `GET /kazan/tickets`
 * the composer shows. Other fields are dropped by the parse.
 */
export const chatTicketSuggestionSchema = z.object({
  id: z.string(),
  key: z.string(),
  title: z.string(),
  /** The ticket type; an unknown one shows as Feature. */
  type: z.string().nullable().optional(),
  board: z.object({ name: z.string() }).nullable().optional(),
});

/**
 * An active member; the server lists online members first, then by name.
 * `kazanAccess` is false for a member who may not open Kazan in the
 * organization, whom the server refuses to mention or add to a channel; only
 * `true` counts as access.
 */
export const chatMemberResponseSchema = z.object({
  userId: z.string(),
  firstName: z.string().nullable(),
  lastName: z.string().nullable(),
  email: z.string().nullable(),
  role: organizationRoleSchema,
  online: z.boolean(),
  kazanAccess: z.boolean().optional(),
});

// ── Realtime Signal Schemas ─────────────────────────────────────────
// Signals carry IDs, never reader-specific data. Only MESSAGE_CREATED and
// MESSAGE_UPDATED may carry the message itself, built for no particular
// reader; the client fills in the reader's own fields.

export const chatMessageSignalSchema = z.object({
  type: z.enum(["MESSAGE_CREATED", "MESSAGE_UPDATED", "MESSAGE_DELETED"]),
  messageId: z.string(),
  threadId: z.string().nullable(),
  authorId: z.string().nullable(),
  createdAt: z.string().nullable(),
  clientNonce: z.string().nullable(),
  version: z.string().nullable(),
  /** Null on MESSAGE_DELETED and whenever the reader's copy depends on facts only the server knows. */
  message: chatMessageResponseSchema.nullable(),
});

export const chatThreadSignalSchema = z.object({
  type: z.enum(["THREAD_CREATED", "THREAD_UPDATED"]),
  threadId: z.string(),
  rootMessageId: z.string(),
});

export const chatReactionSignalSchema = z.object({
  type: z.literal("REACTION_CHANGED"),
  messageId: z.string(),
  threadId: z.string().nullable(),
  emoji: z.string(),
  userId: z.string(),
  added: z.boolean(),
});

export const chatPinSignalSchema = z.object({
  type: z.literal("PIN_CHANGED"),
  messageId: z.string(),
  threadId: z.string().nullable(),
  pinned: z.boolean(),
});

export const chatTypingSignalSchema = z.object({
  type: z.literal("TYPING"),
  userId: z.string(),
  threadId: z.string().nullable(),
});

export const chatPresenceSignalSchema = z.object({
  type: z.literal("PRESENCE"),
  userId: z.string(),
  online: z.boolean(),
});

/** A channel was created, changed or deleted, or its member list changed. */
export const chatChannelSignalSchema = z.object({
  type: z.literal("CHANNEL_CHANGED"),
  channelId: z.string(),
});

/** A frame of the organization topic or of a channel topic. */
export const chatSignalSchema = z.discriminatedUnion("type", [
  chatMessageSignalSchema,
  chatThreadSignalSchema,
  chatReactionSignalSchema,
  chatPinSignalSchema,
  chatTypingSignalSchema,
  chatPresenceSignalSchema,
  chatChannelSignalSchema,
]);

export const chatUnreadSignalSchema = z.object({
  type: z.literal("UNREAD_CHANGED"),
  unread: chatUnreadSchema,
});

export const chatThreadMembershipSignalSchema = z.object({
  type: z.literal("THREAD_MEMBERSHIP_CHANGED"),
  threadId: z.string(),
  member: z.boolean(),
});

/** A frame of the reader's own queue. */
export const chatUserSignalSchema = z.discriminatedUnion("type", [
  chatUnreadSignalSchema,
  chatThreadMembershipSignalSchema,
  chatChannelSignalSchema,
]);

// ── Inferred Types ──────────────────────────────────────────────────

export type ChatAttachmentResponse = z.infer<typeof chatAttachmentResponseSchema>;
export type ChatMessageCursor = z.infer<typeof chatMessageCursorSchema>;
export type ChatMessageCreateRequest = z.infer<typeof chatMessageCreateRequestSchema>;
export type ChatThreadCreateRequest = z.infer<typeof chatThreadCreateRequestSchema>;
export type ChatChannelCreateRequest = z.infer<typeof chatChannelCreateRequestSchema>;
export type ChatChannelUpdateRequest = z.infer<typeof chatChannelUpdateRequestSchema>;
export type ChatChannelMembersRequest = z.infer<typeof chatChannelMembersRequestSchema>;
export type ChatThreadUpdateRequest = z.infer<typeof chatThreadUpdateRequestSchema>;
export type ChatThreadResponse = z.infer<typeof chatThreadResponseSchema>;
export type ChatThreadPage = z.infer<typeof chatThreadPageSchema>;
export type ChatTicketSuggestion = z.infer<typeof chatTicketSuggestionSchema>;
export type ChatReadStateRequest = z.infer<typeof chatReadStateRequestSchema>;
export type ChatAuthorResponse = z.infer<typeof chatAuthorResponseSchema>;
export type ChatUserResponse = z.infer<typeof chatUserResponseSchema>;
export type ChatReplyPreviewResponse = z.infer<typeof chatReplyPreviewResponseSchema>;
export type ChatTicketRefResponse = z.infer<typeof chatTicketRefResponseSchema>;
export type ChatLinkedTicketResponse = z.infer<typeof chatLinkedTicketResponseSchema>;
export type ChatReactionSummaryResponse = z.infer<typeof chatReactionSummaryResponseSchema>;
export type ChatThreadSummaryResponse = z.infer<typeof chatThreadSummaryResponseSchema>;
export type ChatMessageResponse = z.infer<typeof chatMessageResponseSchema>;
export type ChatThreadRootMessage = z.infer<typeof chatThreadRootMessageSchema>;
export type ChatMessagePage = z.infer<typeof chatMessagePageSchema>;
export type ChatConversationUnreadResponse = z.infer<typeof chatConversationUnreadResponseSchema>;
export type ChatUnread = z.infer<typeof chatUnreadSchema>;
export type ChatChannelResponse = z.infer<typeof chatChannelResponseSchema>;
export type ChatMemberResponse = z.infer<typeof chatMemberResponseSchema>;
export type ChatMessageSignal = z.infer<typeof chatMessageSignalSchema>;
export type ChatChannelSignal = z.infer<typeof chatChannelSignalSchema>;
export type ChatSignal = z.infer<typeof chatSignalSchema>;
export type ChatUserSignal = z.infer<typeof chatUserSignalSchema>;

// ── UI-only Types ───────────────────────────────────────────────────

/**
 * A file picked on this device for a message: from the camera, the photo
 * library or the files app. `size` and the image dimensions are null when
 * the picker could not tell them.
 */
export interface ChatLocalFile {
  /** Stable while the file is staged, so removing one keeps the others in place. */
  key: string;
  uri: string;
  name: string;
  mimeType: string | null;
  size: number | null;
  width: number | null;
  height: number | null;
}

/**
 * A message of this device that the server has not confirmed: `pending`
 * while the send runs, `failed` after it failed ("Not sent", with Retry and
 * Delete). It keeps everything needed to send again, so the text is never lost.
 */
export interface ChatOutboxMessage {
  clientNonce: string;
  organizationId: string;
  channelId: string;
  /** Null for a message of the channel itself. */
  threadId: string | null;
  content: string;
  replyToMessageId: string | null;
  /** Files sent with the message; kept so Retry sends them again. */
  files: ChatLocalFile[];
  /** Share of the request uploaded, 0 to 1, while files go up; null otherwise. */
  progress: number | null;
  createdAt: string;
  status: "pending" | "failed";
  /** The error code of the last failure, for example `GEN_API_004`. */
  errorCode: string | null;
  /**
   * True when the last failure got no response at all. Such a row is sent
   * again by itself once the network or the realtime session is back; a row
   * the server refused waits for a manual Retry.
   */
  failedByNetwork: boolean;
}
