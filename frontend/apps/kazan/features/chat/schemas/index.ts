import { z } from "zod";
import { attachmentResponseSchema } from "@oba/shared/features/attachments/schemas";
import { organizationRoleSchema } from "@oba/shared/features/users/schema";
import { MEMBER_WITHOUT_KAZAN_ACCESS_CODE } from "@/lib/kazan-member-access";
import { updateAuthorResponseSchema, updateResponseSchema } from "@/features/updates/schemas";
import { PullRequestShareStatusSchema, pullRequestShareResponseSchema } from "@/features/pull-requests/schemas";

// ── Limits (mirror the chat requests and validator on the server) ───

export const CHAT_MESSAGE_MAX_LENGTH = 2000;
/** The composer shows its character counter from this length on. */
export const CHAT_MESSAGE_COUNTER_FROM = 1800;
export const CHAT_MAX_USER_MENTIONS = 20;
export const CHAT_MAX_TICKET_REFS = 10;
export const CHAT_MAX_FILES = 10;
/** The server's multipart limit for one send, files and message part together. */
export const CHAT_MAX_REQUEST_BYTES = 12 * 1024 * 1024;
export const CHAT_CLIENT_NONCE_MAX = 64;
export const CHAT_THREAD_TITLE_MIN = 1;
export const CHAT_THREAD_TITLE_MAX = 100;
/** The auto-archive windows a thread accepts, in minutes: 1 hour, 24 hours, 3 days, 1 week. */
export const CHAT_AUTO_ARCHIVE_OPTIONS = [60, 1440, 4320, 10080] as const;
export const CHAT_DEFAULT_AUTO_ARCHIVE_MINUTES = 10080;
export const CHAT_EMOJI_MAX = 64;
export const CHAT_MAX_REACTIONS_PER_MESSAGE = 20;
export const CHAT_SEARCH_MIN = 2;
export const CHAT_SEARCH_MAX = 100;
export const CHAT_SEARCH_PAGE_SIZE = 25;
/** Messages per page; also the most the server returns for any chat list. */
export const CHAT_PAGE_SIZE = 50;
export const CHAT_MAX_PINS = 50;
/** Unread counts are capped at this value by the server; the badge shows "99+". */
export const CHAT_UNREAD_CAP = 100;
/** The server accepts one typing signal per session in this interval and drops faster ones. */
export const CHAT_TYPING_INTERVAL_MS = 3000;
/** A typing indicator ends this long after the member's last signal. */
export const CHAT_TYPING_TIMEOUT_MS = 8000;
/** Consecutive messages of one author within this window share one header. */
export const CHAT_GROUP_WINDOW_MS = 7 * 60 * 1000;
export const CHAT_CHANNEL_NAME_MIN = 1;
export const CHAT_CHANNEL_NAME_MAX = 80;
/** The most member IDs one channel request accepts. */
export const CHAT_CHANNEL_MAX_MEMBER_IDS = 200;

// ── Error codes the client reacts to ────────────────────────────────

export const CHAT_ERROR_CODES = {
  /** Send rate exceeded (429). */
  rateLimited: "GEN_API_004",
  /** Text, mention, reference or file limits (422). */
  messageInvalid: "KAZ_VAL_043",
  /** A mentioned user cannot read the conversation: no active member, or not on a private channel's list (422). */
  mentionNotMember: "KAZ_VAL_044",
  /** A private channel member or a mentioned user who has no Kazan access in the organization (422). */
  memberWithoutKazanAccess: MEMBER_WITHOUT_KAZAN_ACCESS_CODE,
  /** `memberIds` sent in a channel update that does not make the channel private (422). */
  channelMembersNotExpected: "KAZ_VAL_051",
  /** Members added to or removed from a public channel (422). */
  channelNotPrivate: "KAZ_VAL_052",
  /** A channel with a purpose other than GENERAL made private, or a private channel given such a purpose (422). */
  channelPurposePrivate: "KAZ_VAL_053",
  /** A daily update entry sent to a channel that is not the daily updates channel (422). */
  dailyUpdateWrongChannel: "KAZ_VAL_054",
  /** A daily update entry for another user's update (403). */
  dailyUpdateNotAuthor: "KAZ_SEC_008",
  /** The daily update already has its entry in the chat (409). */
  dailyUpdateAlreadyPosted: "KAZ_STA_015",
  /**
   * A purpose other than GENERAL asked for while another live channel of the
   * organization holds it (409); `errorValues.purpose` names the purpose.
   */
  channelPurposeTaken: "KAZ_STA_016",
  /** Channel absent, deleted, of another organization, or private without the reader on its list (404). */
  channelNotFound: "KAZ_NOT_015",
  /** A reply targets another conversation (422). */
  replyOutsideConversation: "KAZ_VAL_045",
  /** Not one emoji, or 20 distinct emoji reached (422). */
  reactionInvalid: "KAZ_VAL_046",
  /** A daily update entry is edited and deleted only through the daily update (422). */
  dailyUpdateReadOnly: "KAZ_VAL_047",
  /** 50 pins reached (422). */
  pinLimitReached: "KAZ_VAL_048",
  /** The message already has a thread (409); `errorValues.threadId` names it. */
  threadExists: "KAZ_STA_013",
  /** A delete by a reader who is neither the author nor an organization OWNER or ADMIN (403). */
  deleteRefused: "KAZ_SEC_007",
  /** Removal of another member from a private channel by a reader who is not an organization OWNER or ADMIN (403). */
  channelMemberRemoveRefused: "KAZ_SEC_011",
  /** Message absent, deleted or of another organization (404). */
  messageNotFound: "KAZ_NOT_013",
  /** Thread absent or of another organization (404). */
  threadNotFound: "KAZ_NOT_014",
  /**
   * The thread is closed (409): a send or an edit in it, or an archive of it,
   * is refused until it is reopened.
   */
  threadClosed: "KAZ_STA_014",
} as const;

/**
 * The refusals of a channel write that the channel dialogs explain in the
 * reader's language next to the form; the channel mutations do not toast
 * them again. `ORG_SEC_003` is the organization-level refusal of a reader who
 * is no longer OWNER or ADMIN. `chat-channel-dialog.tsx` maps each of these
 * codes to its text, keyed by `ChatChannelDialogErrorCode`, so a code added
 * here without a text does not compile.
 */
const CHAT_CHANNEL_DIALOG_ERROR_CODE_LIST = [
  CHAT_ERROR_CODES.channelMembersNotExpected,
  CHAT_ERROR_CODES.channelNotPrivate,
  CHAT_ERROR_CODES.channelPurposePrivate,
  CHAT_ERROR_CODES.channelPurposeTaken,
  CHAT_ERROR_CODES.mentionNotMember,
  CHAT_ERROR_CODES.channelNotFound,
  CHAT_ERROR_CODES.channelMemberRemoveRefused,
  CHAT_ERROR_CODES.memberWithoutKazanAccess,
  "ORG_SEC_003",
] as const;

export type ChatChannelDialogErrorCode = (typeof CHAT_CHANNEL_DIALOG_ERROR_CODE_LIST)[number];

export const CHAT_CHANNEL_DIALOG_ERROR_CODES: ReadonlySet<string> = new Set<string>(CHAT_CHANNEL_DIALOG_ERROR_CODE_LIST);

/** True when the code is one of `CHAT_CHANNEL_DIALOG_ERROR_CODES`. */
export function isChatChannelDialogErrorCode(code: string): code is ChatChannelDialogErrorCode {
  return CHAT_CHANNEL_DIALOG_ERROR_CODES.has(code);
}

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

export const chatUserToken = (userId: string) => `<@${userId}>`;
export const chatTicketToken = (ticketId: string) => `<#${ticketId}>`;

/** Replaces code with spaces, so positions in the result are positions in the original. */
export function maskChatCode(content: string): string {
  const blank = (match: string) => " ".repeat(match.length);
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
  const masked = maskChatCode(content);
  const userIds = new Set<string>();
  let mentionsEveryone = false;
  let mentionsHere = false;
  for (const match of masked.matchAll(CHAT_USER_TOKEN_PATTERN)) {
    const target = match[1];
    if (!target) continue;
    if (target === CHAT_MENTION_EVERYONE) mentionsEveryone = true;
    else if (target === CHAT_MENTION_HERE) mentionsHere = true;
    else userIds.add(target.toLowerCase());
  }
  const ticketIds = new Set<string>();
  for (const match of masked.matchAll(CHAT_TICKET_TOKEN_PATTERN)) {
    const ticketId = match[1];
    if (ticketId) ticketIds.add(ticketId.toLowerCase());
  }
  return { userIds: [...userIds], mentionsEveryone, mentionsHere, ticketIds: [...ticketIds] };
}

// ── Enum Schemas ────────────────────────────────────────────────────

/** How a channel works; set at creation and never changed. */
export const ChatChannelTypeSchema = z.enum(["TEXT"]);
export type ChatChannelType = z.infer<typeof ChatChannelTypeSchema>;
export const ChatChannelType = ChatChannelTypeSchema.enum;

/**
 * What a channel is for. `DAILY_UPDATES` is the one channel of the
 * organization that receives the daily update entries; `PULL_REQUESTS` is the
 * one channel for the team's pull request talk. Each purpose other than
 * `GENERAL` has at most one live channel per organization, always public.
 */
export const ChatChannelPurposeSchema = z.enum(["GENERAL", "DAILY_UPDATES", "PULL_REQUESTS"]);
export type ChatChannelPurpose = z.infer<typeof ChatChannelPurposeSchema>;
export const ChatChannelPurpose = ChatChannelPurposeSchema.enum;

/** `PUBLIC`: every member of the organization reads it; `PRIVATE`: only the users on its member list. */
export const ChatChannelVisibilitySchema = z.enum(["PUBLIC", "PRIVATE"]);
export type ChatChannelVisibility = z.infer<typeof ChatChannelVisibilitySchema>;
export const ChatChannelVisibility = ChatChannelVisibilitySchema.enum;

/**
 * The effective status of a thread. `ARCHIVED` covers an archive by hand and
 * one by inactivity; `CLOSED` is read-only and never archives by itself.
 */
export const ChatThreadStatusSchema = z.enum(["ACTIVE", "CLOSED", "ARCHIVED"]);
export type ChatThreadStatus = z.infer<typeof ChatThreadStatusSchema>;
export const ChatThreadStatus = ChatThreadStatusSchema.enum;

export const ChatAutoArchiveMinutesSchema = z.literal(CHAT_AUTO_ARCHIVE_OPTIONS);
export type ChatAutoArchiveMinutes = (typeof CHAT_AUTO_ARCHIVE_OPTIONS)[number];

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * Keyset cursor of a message list: at most one of the three is set (the
 * server answers 422 `KAZ_VAL_049` otherwise); none asks for the newest page.
 */
export const chatMessageCursorSchema = z.object({
  before: z.string().optional(),
  after: z.string().optional(),
  around: z.string().optional(),
});

/** A thread page; the thread fixes the channel. */
export const chatMessageQueryParamsSchema = chatMessageCursorSchema.extend({
  limit: z.number().int().min(1).max(CHAT_PAGE_SIZE).optional(),
});

/**
 * A channel page. `day` (YYYY-MM-DD, a day in Europe/Istanbul), `authorId`
 * and `pullRequestStatuses` narrow every page direction, so `hasOlder` and
 * `hasNewer` describe the filtered rows. With statuses only share entries
 * whose share has one of them are listed; an empty list does not narrow. The
 * server refuses an unknown status with 422 `GEN_VAL_001`.
 */
export const chatChannelMessageQueryParamsSchema = chatMessageQueryParamsSchema.extend({
  channelId: z.string(),
  day: z.string().optional(),
  authorId: z.string().optional(),
  pullRequestStatuses: z.array(PullRequestShareStatusSchema).optional(),
});

/**
 * The JSON part `message` of a send; files travel as separate `files` parts.
 * A `threadId` of another channel is 404 `KAZ_NOT_014`. `dailyUpdateId`
 * posts the entry of the reader's own daily update (empty `content` is then
 * accepted) and only in the daily updates channel.
 */
export const chatMessageCreateRequestSchema = z.object({
  channelId: z.string(),
  content: z.string().max(CHAT_MESSAGE_MAX_LENGTH).nullable().optional(),
  threadId: z.string().nullable().optional(),
  dailyUpdateId: z.string().nullable().optional(),
  replyToMessageId: z.string().nullable().optional(),
  clientNonce: z.string().min(1).max(CHAT_CLIENT_NONCE_MAX),
});

export const chatMessageUpdateRequestSchema = z.object({
  content: z.string().max(CHAT_MESSAGE_MAX_LENGTH),
});

export const chatReactionRequestSchema = z.object({
  emoji: z.string().min(1).max(CHAT_EMOJI_MAX),
});

/** `threadId` null marks the channel itself. The server only moves the position forward. */
export const chatReadStateRequestSchema = z.object({
  channelId: z.string(),
  threadId: z.string().nullable(),
  lastReadMessageId: z.string(),
});

/** Without `channelId` the search covers every channel the reader can read. */
export const chatSearchParamsSchema = z.object({
  q: z.string().min(CHAT_SEARCH_MIN).max(CHAT_SEARCH_MAX),
  channelId: z.string().optional(),
  authorId: z.string().optional(),
  before: z.string().optional(),
  limit: z.number().int().min(1).max(CHAT_PAGE_SIZE).optional(),
});

export const chatPinsParamsSchema = z.object({
  channelId: z.string(),
  before: z.string().optional(),
  limit: z.number().int().min(1).max(CHAT_PAGE_SIZE).optional(),
});

export const chatThreadListParamsSchema = z.object({
  channelId: z.string(),
  status: ChatThreadStatusSchema,
  before: z.string().optional(),
  limit: z.number().int().min(1).max(CHAT_PAGE_SIZE).optional(),
});

/** `title` null takes the server's default derived from the root message. */
export const chatThreadCreateRequestSchema = z.object({
  rootMessageId: z.string(),
  title: z.string().trim().min(CHAT_THREAD_TITLE_MIN).max(CHAT_THREAD_TITLE_MAX).nullable().optional(),
});

export const chatThreadUpdateRequestSchema = z.object({
  title: z.string().trim().min(CHAT_THREAD_TITLE_MIN).max(CHAT_THREAD_TITLE_MAX),
  autoArchiveMinutes: ChatAutoArchiveMinutesSchema,
});

export const chatThreadStatusUpdateRequestSchema = z.object({
  status: ChatThreadStatusSchema,
});

/**
 * Without `channelId` the organization's active members. With it, a private
 * channel lists its members; `authorsOnly` lists the distinct authors of the
 * channel's live messages instead.
 */
export const chatMembersParamsSchema = z.object({
  search: z.string().optional(),
  channelId: z.string().optional(),
  authorsOnly: z.boolean().optional(),
});

/** OWNER and ADMIN only. A private channel's members are the creator plus `memberIds`, each an active member. */
export const chatChannelCreateRequestSchema = z.object({
  name: z.string().trim().min(CHAT_CHANNEL_NAME_MIN).max(CHAT_CHANNEL_NAME_MAX),
  visibility: ChatChannelVisibilitySchema,
  purpose: ChatChannelPurposeSchema,
  memberIds: z.array(z.string()).max(CHAT_CHANNEL_MAX_MEMBER_IDS).optional(),
});

/**
 * A full update; the type cannot change. `memberIds` is accepted only when
 * the update makes a public channel private (422 `KAZ_VAL_051` otherwise);
 * a channel made public loses its member list. `DAILY_UPDATES` moves the
 * purpose here, and the previous holder becomes `GENERAL`.
 */
export const chatChannelUpdateRequestSchema = chatChannelCreateRequestSchema;

/** Adds users to a private channel's member list (422 `KAZ_VAL_052` for a public channel). */
export const chatChannelMembersRequestSchema = z.object({
  userIds: z.array(z.string()).min(1).max(CHAT_CHANNEL_MAX_MEMBER_IDS),
});

/** Body of the typing frame; `threadId` null is the channel itself. */
export const chatTypingRequestSchema = z.object({
  channelId: z.string(),
  threadId: z.string().nullable(),
});

// ── Form Schemas ────────────────────────────────────────────────────
// The messages are i18n keys of the `chat` namespace. The limits mirror the
// server, which answers 422 `KAZ_VAL_043` for the same cases.

/** A browser `File`; checked without referencing `File` at import time, so the module loads on the server. */
const fileSchema = z.custom<File>(
  (value) => typeof File !== "undefined" && value instanceof File,
);

/**
 * The composer's content (markdown with tokens, as sent) and files. Text or
 * at least one file is required; length counts raw characters, tokens
 * included; mentions and references count once each and never inside code.
 */
export const chatComposerSchema = z
  .object({
    content: z.string(),
    files: z.array(fileSchema),
  })
  .superRefine((value, ctx) => {
    if (value.content.trim().length === 0 && value.files.length === 0) {
      ctx.addIssue({ code: "custom", path: ["content"], message: "chat.composer.empty" });
    }
    if (value.content.length > CHAT_MESSAGE_MAX_LENGTH) {
      ctx.addIssue({ code: "custom", path: ["content"], message: "chat.composer.tooLong" });
    }
    const tokens = parseChatTokens(value.content);
    if (tokens.userIds.length > CHAT_MAX_USER_MENTIONS) {
      ctx.addIssue({ code: "custom", path: ["content"], message: "chat.composer.tooManyMentions" });
    }
    if (tokens.ticketIds.length > CHAT_MAX_TICKET_REFS) {
      ctx.addIssue({ code: "custom", path: ["content"], message: "chat.composer.tooManyTicketRefs" });
    }
    if (value.files.length > CHAT_MAX_FILES) {
      ctx.addIssue({ code: "custom", path: ["files"], message: "chat.composer.tooManyFiles" });
    }
    const totalBytes = value.files.reduce((sum, file) => sum + file.size, 0);
    if (totalBytes > CHAT_MAX_REQUEST_BYTES) {
      ctx.addIssue({ code: "custom", path: ["files"], message: "chat.composer.filesTooLarge" });
    }
  });

export const chatThreadTitleFormSchema = z.object({
  title: z
    .string()
    .trim()
    .min(CHAT_THREAD_TITLE_MIN, "chat.thread.titleRequired")
    .max(CHAT_THREAD_TITLE_MAX, "chat.thread.titleTooLong"),
  autoArchiveMinutes: ChatAutoArchiveMinutesSchema,
});

// ── Response Schemas ────────────────────────────────────────────────

/** The author of a message (`TicketCreatedByResponse` on the server). */
export const chatAuthorResponseSchema = updateAuthorResponseSchema;

/** A user referenced by a page; `member` false renders "@unknown member". */
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
  /**
   * Files of the quoted message; empty when it was deleted or has none. The
   * default reads a response of a server that does not send them yet.
   */
  attachments: z.array(attachmentResponseSchema).default([]),
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

/** A ticket key typed as plain text that the reader can open; keys the reader cannot open are absent. */
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
  /** Up to 3 members: the most recent repliers, then the creator. */
  memberPreviewIds: z.array(z.string()),
  memberCount: z.number(),
});

export const chatMessageResponseSchema = z.object({
  id: z.string(),
  organizationId: z.string(),
  /** A thread message carries its thread's channel. */
  channelId: z.string(),
  /** Null for a message of the channel itself. */
  threadId: z.string().nullable(),
  /** Set for a daily update entry, which is read-only in the chat; null for any other message. */
  dailyUpdateId: z.string().nullable(),
  /** Set for a pull request share entry, which is read-only in the chat; null for any other message and for placeholders. */
  pullRequestShareId: z.string().nullable(),
  /** True for the placeholder of a deleted thread root: no author, empty content, `thread` set. */
  deleted: z.boolean(),
  /** Null for the placeholder of a deleted thread root. */
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
  /**
   * Computed for the reader: someone else's message quoting a live message of
   * the reader. False for the reader's own messages and when the quoted
   * message was deleted. Highlighted like a mention.
   */
  repliesToMe: z.boolean(),
  ticketRefs: z.array(chatTicketRefResponseSchema),
  linkedTickets: z.array(chatLinkedTicketResponseSchema),
  attachments: z.array(attachmentResponseSchema),
  reactions: z.array(chatReactionSummaryResponseSchema),
  /** The thread started on this message; null when there is none. */
  thread: chatThreadSummaryResponseSchema.nullable(),
  /** The daily update of an entry (`dailyUpdateId` set), with `canOpen` per reader; null otherwise. */
  dailyUpdate: updateResponseSchema.nullable(),
  /**
   * The share of an entry (`pullRequestShareId` set), with `linkedTicket` per
   * reader; null otherwise, and null when the share is gone. `mentionsMe` is
   * true for its reviewers.
   */
  pullRequestShare: pullRequestShareResponseSchema.nullable(),
  /** Every author, mentioned and quoted user of the message, by user ID. */
  users: z.record(z.string(), chatUserResponseSchema),
  /**
   * The row's last change (ISO instant, the server's `updatedAt`). REST
   * responses and realtime payloads carry the same value, so a copy is only
   * replaced by a newer one; compare with `compareChatVersions`. Null for the
   * deleted placeholder of a thread root, which counts as the oldest.
   */
  version: z.string().nullable(),
});

/**
 * A keyset page. `items` are oldest first for the channel and thread lists,
 * newest pin first for pins and newest first for search.
 */
export const chatMessagePageSchema = z.object({
  items: z.array(chatMessageResponseSchema),
  hasOlder: z.boolean(),
  hasNewer: z.boolean(),
});

export const chatThreadResponseSchema = z.object({
  id: z.string(),
  channelId: z.string(),
  rootMessageId: z.string(),
  /** The root message; the deleted placeholder when the root was deleted. */
  rootMessage: chatMessageResponseSchema,
  title: z.string(),
  createdBy: z.string(),
  messageCount: z.number(),
  lastMessageAt: z.string(),
  autoArchiveMinutes: z.number(),
  status: ChatThreadStatusSchema,
  /**
   * When the status last changed; null when it never did. For a thread
   * archived by inactivity it is the end of its window.
   */
  statusChangedAt: z.string().nullable(),
  /** Who changed the status last; null when it never changed and for an archive by inactivity. */
  statusChangedBy: chatUserResponseSchema.nullable(),
  /** True when the reader is a member of the thread. */
  member: z.boolean(),
  members: z.array(chatUserResponseSchema),
  /** True when the reader may rename the thread, change its auto-archive window and set its status. */
  canManage: z.boolean(),
});

/** Threads by most recent activity first. */
export const chatThreadPageSchema = z.object({
  items: z.array(chatThreadResponseSchema),
  hasMore: z.boolean(),
});

/** The reader's unread state in one channel or one thread. */
export const chatConversationUnreadResponseSchema = z.object({
  /** The channel ID or the thread ID. */
  id: z.string(),
  /** The channel itself (equal to `id`) or the thread's channel. */
  channelId: z.string(),
  unread: z.number(),
  /** The unread messages among `unread` that notify the reader. */
  mentionCount: z.number(),
  /** Null before the reader's first read position. */
  lastReadMessageId: z.string().nullable(),
  /** The creation time of that message; the "New messages" divider follows it. */
  lastReadAt: z.string().nullable(),
});

/**
 * The reader's unread state in every channel the reader can read and every
 * active thread the reader is a member of. Every count except `mentionTotal`
 * is capped at `CHAT_UNREAD_CAP`. A notification is an unread message of
 * someone else that mentions the reader (directly, or through `@here` while
 * the reader was online), mentions `@everyone`, or replies to a live message
 * of the reader.
 */
export const chatUnreadResponseSchema = z.object({
  channels: z.array(chatConversationUnreadResponseSchema),
  threads: z.array(chatConversationUnreadResponseSchema),
  total: z.number(),
  /** The notification counts of every channel and thread added up; it can exceed the cap. */
  mentionTotal: z.number(),
});

/**
 * A channel the reader sees in the list. OWNER and ADMIN see every private
 * channel, with `member` false until they add themselves; only then do they
 * read its content.
 */
export const chatChannelResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  /** URL segment of the channel, unique among the organization's live channels. */
  slug: z.string(),
  type: ChatChannelTypeSchema,
  purpose: ChatChannelPurposeSchema,
  visibility: ChatChannelVisibilitySchema,
  /** True when the reader is on a private channel's list; true for every reader of a public channel. */
  member: z.boolean(),
  /** The member list's size for a private channel; the organization's active members for a public one. */
  memberCount: z.number(),
  createdAt: z.string(),
});

/** An active member; the server lists online members first, then by name. */
export const chatMemberResponseSchema = z.object({
  userId: z.string(),
  firstName: z.string().nullable(),
  lastName: z.string().nullable(),
  email: z.string().nullable(),
  role: organizationRoleSchema,
  online: z.boolean(),
  /** True when the member can open Kazan in the organization; a missing flag means no access. */
  kazanAccess: z.boolean().optional(),
});

// ── Realtime Signal Schemas ─────────────────────────────────────────
// Signals carry IDs and Oba user IDs, never reader-specific data. Only
// MESSAGE_CREATED and MESSAGE_UPDATED may carry the message itself, built for
// no particular reader; the client fills in the reader's own fields.

export const chatMessageSignalSchema = z.object({
  type: z.enum(["MESSAGE_CREATED", "MESSAGE_UPDATED", "MESSAGE_DELETED"]),
  messageId: z.string(),
  threadId: z.string().nullable(),
  authorId: z.string().nullable(),
  createdAt: z.string().nullable(),
  /** The sender's nonce on a `MESSAGE_CREATED` from a user send; null otherwise. */
  clientNonce: z.string().nullable(),
  /** The message's `version` after the change; null on MESSAGE_DELETED. */
  version: z.string().nullable(),
  /**
   * The message as no particular reader sees it: `mentionsMe`, `repliesToMe`
   * and every `reactedByMe` false, no `linkedTickets`, no `dailyUpdate`.
   * Null on MESSAGE_DELETED, and whenever the reader's copy depends on facts
   * only the server knows (ticket references, `@here`, daily update entries)
   * or the server could not build it: the client fetches the message then.
   */
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
  /** Null for the channel. */
  threadId: z.string().nullable(),
});

export const chatPresenceSignalSchema = z.object({
  type: z.literal("PRESENCE"),
  userId: z.string(),
  online: z.boolean(),
});

/**
 * A channel was created, changed or deleted, or its member list changed. A
 * public channel's signal goes to the organization topic, a private
 * channel's to the own queue of the readers it concerns.
 */
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
  unread: chatUnreadResponseSchema,
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

export type ChatMessageCursor = z.infer<typeof chatMessageCursorSchema>;
export type ChatMessageQueryParams = z.infer<typeof chatMessageQueryParamsSchema>;
export type ChatChannelMessageQueryParams = z.infer<typeof chatChannelMessageQueryParamsSchema>;
export type ChatMessageCreateRequest = z.infer<typeof chatMessageCreateRequestSchema>;
export type ChatMessageUpdateRequest = z.infer<typeof chatMessageUpdateRequestSchema>;
export type ChatReactionRequest = z.infer<typeof chatReactionRequestSchema>;
export type ChatReadStateRequest = z.infer<typeof chatReadStateRequestSchema>;
export type ChatSearchParams = z.infer<typeof chatSearchParamsSchema>;
export type ChatPinsParams = z.infer<typeof chatPinsParamsSchema>;
export type ChatThreadListParams = z.infer<typeof chatThreadListParamsSchema>;
export type ChatMembersParams = z.infer<typeof chatMembersParamsSchema>;
export type ChatChannelCreateRequest = z.infer<typeof chatChannelCreateRequestSchema>;
export type ChatChannelUpdateRequest = z.infer<typeof chatChannelUpdateRequestSchema>;
export type ChatChannelMembersRequest = z.infer<typeof chatChannelMembersRequestSchema>;
export type ChatThreadCreateRequest = z.infer<typeof chatThreadCreateRequestSchema>;
export type ChatThreadUpdateRequest = z.infer<typeof chatThreadUpdateRequestSchema>;
export type ChatThreadStatusUpdateRequest = z.infer<typeof chatThreadStatusUpdateRequestSchema>;
export type ChatTypingRequest = z.infer<typeof chatTypingRequestSchema>;
export type ChatComposerValues = z.infer<typeof chatComposerSchema>;
export type ChatThreadTitleFormValues = z.infer<typeof chatThreadTitleFormSchema>;
export type ChatAuthorResponse = z.infer<typeof chatAuthorResponseSchema>;
export type ChatUserResponse = z.infer<typeof chatUserResponseSchema>;
export type ChatReplyPreviewResponse = z.infer<typeof chatReplyPreviewResponseSchema>;
export type ChatTicketRefResponse = z.infer<typeof chatTicketRefResponseSchema>;
export type ChatLinkedTicketResponse = z.infer<typeof chatLinkedTicketResponseSchema>;
export type ChatReactionSummaryResponse = z.infer<typeof chatReactionSummaryResponseSchema>;
export type ChatThreadSummaryResponse = z.infer<typeof chatThreadSummaryResponseSchema>;
export type ChatMessageResponse = z.infer<typeof chatMessageResponseSchema>;
export type ChatMessagePage = z.infer<typeof chatMessagePageSchema>;
export type ChatThreadResponse = z.infer<typeof chatThreadResponseSchema>;
export type ChatThreadPage = z.infer<typeof chatThreadPageSchema>;
export type ChatConversationUnreadResponse = z.infer<typeof chatConversationUnreadResponseSchema>;
export type ChatChannelResponse = z.infer<typeof chatChannelResponseSchema>;
export type ChatUnreadResponse = z.infer<typeof chatUnreadResponseSchema>;
export type ChatMemberResponse = z.infer<typeof chatMemberResponseSchema>;
export type ChatMessageSignal = z.infer<typeof chatMessageSignalSchema>;
export type ChatThreadSignal = z.infer<typeof chatThreadSignalSchema>;
export type ChatReactionSignal = z.infer<typeof chatReactionSignalSchema>;
export type ChatPinSignal = z.infer<typeof chatPinSignalSchema>;
export type ChatTypingSignal = z.infer<typeof chatTypingSignalSchema>;
export type ChatPresenceSignal = z.infer<typeof chatPresenceSignalSchema>;
export type ChatChannelSignal = z.infer<typeof chatChannelSignalSchema>;
export type ChatSignal = z.infer<typeof chatSignalSchema>;
export type ChatUnreadSignal = z.infer<typeof chatUnreadSignalSchema>;
export type ChatThreadMembershipSignal = z.infer<typeof chatThreadMembershipSignalSchema>;
export type ChatUserSignal = z.infer<typeof chatUserSignalSchema>;

// ── UI-only Types ───────────────────────────────────────────────────

/**
 * A message of this tab that the server has not confirmed: `pending` while
 * the send runs, `failed` after it failed ("Not sent", with Retry and
 * Delete). It keeps everything needed to send again, so the text is never
 * lost.
 */
export interface ChatOutboxMessage {
  clientNonce: string;
  organizationId: string;
  channelId: string;
  /** Null for a message of the channel itself. */
  threadId: string | null;
  content: string;
  replyToMessageId: string | null;
  files: File[];
  createdAt: string;
  status: "pending" | "failed";
  /** The server's message of the last failure. */
  errorMessage: string | null;
  /** The error code of the last failure, for example `GEN_API_004`. */
  errorCode: string | null;
  /**
   * True when the last failure got no response at all (a network failure or
   * a timeout). Such a row is sent again by itself once the network is back;
   * a row the server refused waits for a manual Retry.
   */
  failedByNetwork: boolean;
}
