import {
  chatMarkdownPlainText,
  parseChatMarkdown,
  type ChatPlainTokens,
} from "@/components/chat/chatMarkdown";
import {
  CHAT_MENTION_EVERYONE,
  CHAT_MENTION_HERE,
  CHAT_TICKET_TOKEN_PATTERN,
  CHAT_USER_TOKEN_PATTERN,
  maskChatCode,
  type ChatAuthorResponse,
  type ChatMemberResponse,
  type ChatMessageResponse,
  type ChatReactionSummaryResponse,
  type ChatUserResponse,
} from "@/lib/schemas/chat";

/*
 * How a chat message reads as text: names, previews and the copied text.
 * Names and the copied text follow the web app's rules; a preview drops
 * every markdown marker the bubble hides.
 */

type NamedPerson = {
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
};

/** First and last name, or the email without them; empty when there is neither. */
export function chatPersonName(person: NamedPerson): string {
  const name: string = [person.firstName, person.lastName]
    .filter((part): part is string => !!part && part.trim().length > 0)
    .join(" ")
    .trim();
  return name || (person.email ?? "");
}

/** A user of a page; "unknown member" once they left the organization. */
export function chatUserDisplayName(
  user: ChatUserResponse | undefined,
  unknownLabel: string
): string {
  if (!user || !user.member) return unknownLabel;
  return chatPersonName(user) || unknownLabel;
}

/** True when the reader wrote the message: it is drawn on the right, in the reader's colour. */
export function isOwnChatMessage(
  message: Pick<ChatMessageResponse, "author">,
  meId: string | undefined
): boolean {
  return !!meId && !!message.author && message.author.userId.toLowerCase() === meId.toLowerCase();
}

/**
 * How a mention chip reads, as on the web: `me` (highlighted) when it names
 * the reader, or is `@everyone` or `@here` in a message that notifies them;
 * `unknown` (muted) for a user who is not a member; `member` otherwise.
 */
export type ChatMentionTone = "me" | "member" | "unknown";

export function chatMentionTone(
  target: string,
  user: Pick<ChatUserResponse, "member"> | undefined,
  meId: string | undefined,
  mentionsMe: boolean
): ChatMentionTone {
  if (target === CHAT_MENTION_EVERYONE || target === CHAT_MENTION_HERE) {
    return mentionsMe ? "me" : "member";
  }
  if (!user?.member) return "unknown";
  return !!meId && target.toLowerCase() === meId.toLowerCase() ? "me" : "member";
}

/** The author of a message, read from the page's users so a member who left shows as unknown. */
export function chatAuthorName(message: ChatMessageResponse, unknownLabel: string): string {
  const author: ChatAuthorResponse | null = message.author;
  if (!author) return unknownLabel;
  const user: ChatUserResponse | undefined =
    message.users[author.userId] ?? message.users[author.userId.toLowerCase()];
  return user ? chatUserDisplayName(user, unknownLabel) : chatPersonName(author) || unknownLabel;
}

export function chatMemberName(member: ChatMemberResponse): string {
  return chatPersonName(member);
}

/** True for a daily update or a shared pull request, which changes only through its own screen. */
export function isChatEntryCard(
  message: Pick<ChatMessageResponse, "dailyUpdateId" | "pullRequestShareId">
): boolean {
  return message.dailyUpdateId != null || message.pullRequestShareId != null;
}

/** The summary of a card entry; empty when its source is gone. Null for any other message. */
export function chatEntrySummary(message: ChatMessageResponse): string | null {
  if (message.dailyUpdateId != null) return message.dailyUpdate?.summaryText ?? "";
  if (message.pullRequestShareId != null) return message.pullRequestShare?.summaryText ?? "";
  return null;
}

/** A user token (group 1) or a ticket token (group 2). */
const CHAT_TOKEN_PATTERN = new RegExp(
  `${CHAT_USER_TOKEN_PATTERN.source}|${CHAT_TICKET_TOKEN_PATTERN.source}`,
  "g"
);

/**
 * The readable text of a message with its line breaks: mentions as `@Name`,
 * ticket tokens as keys, a card entry as its summary. Tokens inside code stay
 * literal, as they render.
 */
export function chatMessageText(message: ChatMessageResponse, unknownMember: string): string {
  const summary: string | null = chatEntrySummary(message);
  if (summary !== null) return summary;
  const usersById = new Map<string, ChatUserResponse>(
    Object.entries(message.users).map(([id, user]) => [id.toLowerCase(), user])
  );
  const keysById = new Map<string, string>(
    message.ticketRefs.flatMap((ref) =>
      ref.ticketId ? [[ref.ticketId.toLowerCase(), ref.ticketKey] as const] : []
    )
  );
  // Code is blanked with spaces of the same length, so a token is outside
  // code exactly when the masked text still holds it at the same offset.
  const masked: string = maskChatCode(message.content);
  return message.content.replace(
    CHAT_TOKEN_PATTERN,
    (token: string, target: string | undefined, ticketId: string | undefined, offset: number) => {
      if (!masked.startsWith(token, offset)) return token;
      if (ticketId !== undefined) return keysById.get(ticketId.toLowerCase()) ?? token;
      if (target === undefined) return token;
      return target === CHAT_MENTION_EVERYONE || target === CHAT_MENTION_HERE
        ? `@${target}`
        : `@${chatUserDisplayName(usersById.get(target.toLowerCase()), unknownMember)}`;
    }
  );
}

/**
 * The start of a message on one line, for the channel list: the words the
 * bubble shows, without any markdown marker, names for mentions and keys
 * for tickets, file names for a file-only message, cut at `max` characters
 * with an ellipsis. It reads the parsed message, so every marker the bubble
 * hides (single `*` and `_` emphasis among them) is gone here too.
 */
export function chatMessageSnippet(
  message: ChatMessageResponse,
  unknownMember: string,
  max: number
): string {
  const usersById = new Map<string, ChatUserResponse>(
    Object.entries(message.users).map(([id, user]) => [id.toLowerCase(), user])
  );
  const keysById = new Map<string, string>(
    message.ticketRefs.flatMap((ref) =>
      ref.ticketId ? [[ref.ticketId.toLowerCase(), ref.ticketKey] as const] : []
    )
  );
  const tokens: ChatPlainTokens = {
    mention: (target: string): string =>
      target === CHAT_MENTION_EVERYONE || target === CHAT_MENTION_HERE
        ? `@${target}`
        : `@${chatUserDisplayName(usersById.get(target.toLowerCase()), unknownMember)}`,
    ticket: (ticketId: string): string => keysById.get(ticketId.toLowerCase()) ?? `<#${ticketId}>`,
  };
  const text: string = chatMarkdownPlainText(
    parseChatMarkdown(chatEntrySummary(message) ?? message.content),
    tokens
  )
    .replace(/\s+/g, " ")
    .trim();
  const readable: string =
    text || message.attachments.map((attachment) => attachment.fileName).join(", ");
  return readable.length > max ? `${readable.slice(0, max).trimEnd()}…` : readable;
}

/** Who reacted with an emoji: the names of the users the server lists, and how many more reacted. */
export interface ChatReactionPeople {
  names: string[];
  others: number;
}

/** The server lists the first 10 users of a reaction; the rest are counted. */
export function chatReactionPeople(
  reaction: Pick<ChatReactionSummaryResponse, "count" | "userIds">,
  users: ChatMessageResponse["users"],
  unknownLabel: string
): ChatReactionPeople {
  const names: string[] = reaction.userIds.map((userId: string) =>
    chatUserDisplayName(users[userId] ?? users[userId.toLowerCase()], unknownLabel)
  );
  return { names, others: Math.max(0, reaction.count - names.length) };
}
