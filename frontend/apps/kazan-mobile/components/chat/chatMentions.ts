import { chatMemberName, chatUserDisplayName } from "@/components/chat/chatDisplay";
import { hasKazanAccess } from "@/lib/kazanAccess";
import {
  CHAT_MENTION_EVERYONE,
  CHAT_MENTION_HERE,
  CHAT_TICKET_TOKEN_PATTERN,
  CHAT_USER_TOKEN_PATTERN,
  chatTicketToken,
  chatUserToken,
  maskChatCode,
  type ChatMemberResponse,
  type ChatMessageResponse,
  type ChatTicketSuggestion,
  type ChatUserResponse,
} from "@/lib/schemas/chat";

/*
 * Mentions in the composer's plain text field. The field shows a picked
 * member as `@Name`; the message is sent with the server's token `<@id>`.
 * Each pick remembers which text stands for which token, so the conversion
 * is exact, and text the reader typed by hand stays text.
 */

/** A picked mention or ticket: the text the field shows and the token it is sent as. */
export interface ChatComposerPick {
  display: string;
  token: string;
}

/** At most this many members are offered at once; typing narrows the list. */
export const CHAT_MENTION_SUGGESTIONS_MAX = 8;

/** The `@` word at the caret: where its `@` starts and what follows it. */
export interface ChatMentionQuery {
  start: number;
  query: string;
}

const MENTION_AT_CARET = /(^|\s)@([^\s@]{0,40})$/;

/** The `@` word being typed at the caret, or null when the caret is not in one. */
export function activeChatMentionQuery(text: string, caret: number): ChatMentionQuery | null {
  const match: RegExpExecArray | null = MENTION_AT_CARET.exec(text.slice(0, caret));
  if (!match) return null;
  const query: string = match[2] ?? "";
  return { start: caret - query.length - 1, query };
}

const TICKET_AT_CARET = /(^|\s)#([^\s#]{0,40})$/;

/** The `#` word being typed at the caret, or null when the caret is not in one. */
export function activeChatTicketQuery(text: string, caret: number): ChatMentionQuery | null {
  const match: RegExpExecArray | null = TICKET_AT_CARET.exec(text.slice(0, caret));
  if (!match) return null;
  const query: string = match[2] ?? "";
  return { start: caret - query.length - 1, query };
}

/**
 * Where one line break was typed into `previous` to give `next`, or null for
 * any other change: the Return key of a multiline field, which picks the
 * first suggestion while the suggestions are open instead of breaking the line.
 */
export function chatReturnTypedAt(previous: string, next: string): number | null {
  if (next.length !== previous.length + 1) return null;
  let at = 0;
  while (at < previous.length && previous[at] === next[at]) at += 1;
  return next[at] === "\n" && next.slice(at + 1) === previous.slice(at) ? at : null;
}

/**
 * Replaces the trigger word at the caret (from its `@` or `#`) with `word `
 * and returns the text and the new caret.
 */
export function insertChatComposerWord(
  text: string,
  caret: number,
  at: ChatMentionQuery,
  word: string
): { text: string; caret: number } {
  const before: string = text.slice(0, at.start);
  const after: string = text.slice(caret).replace(/^ /, "");
  const inserted = `${word} `;
  return { text: `${before}${inserted}${after}`, caret: before.length + inserted.length };
}

/** Replaces the `@` word at the caret with `@display ` and returns the text and the new caret. */
export function insertChatMention(
  text: string,
  caret: number,
  at: ChatMentionQuery,
  display: string
): { text: string; caret: number } {
  return insertChatComposerWord(text, caret, at, `@${display}`);
}

/**
 * The pick of a ticket: the field shows its key, the message carries its
 * token, which the server turns into a link that follows a later key change.
 */
export function chatTicketPick(ticket: Pick<ChatTicketSuggestion, "id" | "key">): ChatComposerPick {
  return { display: ticket.key, token: chatTicketToken(ticket.id) };
}

export type ChatMentionSuggestion =
  | {
      kind: "member";
      key: string;
      member: ChatMemberResponse;
      name: string;
      /** False for a member without Kazan: the server refuses a message that mentions them. */
      canMention: boolean;
    }
  | { kind: typeof CHAT_MENTION_EVERYONE | typeof CHAT_MENTION_HERE; key: string };

/**
 * The members matching the typed text by name or email, at most
 * `CHAT_MENTION_SUGGESTIONS_MAX`, in the server's order (online first), then
 * `@everyone` and `@here` when the text starts them. A member without Kazan
 * is listed but cannot be picked. The web app offers the same list.
 */
export function chatMentionSuggestions(
  members: readonly ChatMemberResponse[],
  query: string
): ChatMentionSuggestion[] {
  const text: string = query.trim().toLowerCase();
  const matching: ChatMentionSuggestion[] = members
    .map((member) => ({ member, name: chatMemberName(member) }))
    .filter(
      ({ member, name }) =>
        !text ||
        name.toLowerCase().includes(text) ||
        (member.email ?? "").toLowerCase().includes(text)
    )
    .slice(0, CHAT_MENTION_SUGGESTIONS_MAX)
    .map(({ member, name }) => ({
      kind: "member",
      key: member.userId,
      member,
      name,
      canMention: hasKazanAccess(member),
    }));
  for (const group of [CHAT_MENTION_EVERYONE, CHAT_MENTION_HERE] as const) {
    if (group.startsWith(text)) matching.push({ kind: group, key: group });
  }
  return matching;
}

/** True when the suggestion can be picked: a group, or a member who has Kazan. */
export function chatMentionPickable(suggestion: ChatMentionSuggestion): boolean {
  return suggestion.kind !== "member" || suggestion.canMention;
}

/** The pick of a suggestion: `@Name` for a member, `@everyone` or `@here` for a group. */
export function chatMentionPick(suggestion: ChatMentionSuggestion): ChatComposerPick {
  if (suggestion.kind === "member") {
    return { display: `@${suggestion.name}`, token: chatUserToken(suggestion.member.userId) };
  }
  return { display: `@${suggestion.kind}`, token: chatUserToken(suggestion.kind) };
}

/** A letter or digit right after a pick means the reader typed a longer word; it stays text. */
const WORD_CHARACTER = /[\wÀ-￿]/;

/** A run of the composer's text: typed text, or a pick that is sent as its token. */
export interface ChatComposerSegment {
  text: string;
  pick: ChatComposerPick | null;
}

/**
 * The composer's text split into typed text and picks: every picked
 * `@Name` (or ticket key) outside code is a pick. Longer picks go first, so
 * `@Ali Kaya` wins over `@Ali`; a pick followed by a letter or digit is not
 * one. The field highlights the picks; the send turns them into tokens.
 */
export function chatComposerSegments(
  text: string,
  picks: readonly ChatComposerPick[]
): ChatComposerSegment[] {
  if (text.length === 0) return [];
  if (picks.length === 0) return [{ text, pick: null }];
  const ordered: ChatComposerPick[] = [...picks].sort(
    (left, right) => right.display.length - left.display.length
  );
  const masked: string = maskChatCode(text);
  const segments: ChatComposerSegment[] = [];
  let typed = "";
  let index = 0;
  while (index < text.length) {
    const pick: ChatComposerPick | undefined =
      masked[index] === text[index]
        ? ordered.find(
            (candidate) =>
              text.startsWith(candidate.display, index) &&
              !WORD_CHARACTER.test(text.charAt(index + candidate.display.length))
          )
        : undefined;
    if (pick) {
      if (typed) segments.push({ text: typed, pick: null });
      typed = "";
      segments.push({ text: pick.display, pick });
      index += pick.display.length;
    } else {
      typed += text.charAt(index);
      index += 1;
    }
  }
  if (typed) segments.push({ text: typed, pick: null });
  return segments;
}

/** The text to send: every pick of `chatComposerSegments` becomes its token. */
export function serializeChatComposerText(
  text: string,
  picks: readonly ChatComposerPick[]
): string {
  return chatComposerSegments(text, picks)
    .map((segment) => (segment.pick ? segment.pick.token : segment.text))
    .join("");
}

/** A user token (group 1) or a ticket token (group 2). */
const TOKEN_PATTERN = new RegExp(
  `${CHAT_USER_TOKEN_PATTERN.source}|${CHAT_TICKET_TOKEN_PATTERN.source}`,
  "g"
);

/**
 * The text of a message as the composer edits it: mentions as `@Name` and
 * ticket tokens as their keys, with the picks that turn them back into the
 * same tokens on save. Tokens inside code stay as they are.
 */
export function chatComposerDraft(
  message: Pick<ChatMessageResponse, "content" | "users" | "ticketRefs">,
  unknownMember: string
): { text: string; picks: ChatComposerPick[] } {
  const usersById = new Map<string, ChatUserResponse>(
    Object.entries(message.users).map(([id, user]) => [id.toLowerCase(), user])
  );
  const keysById = new Map<string, string>(
    message.ticketRefs.flatMap((ref) =>
      ref.ticketId ? [[ref.ticketId.toLowerCase(), ref.ticketKey] as const] : []
    )
  );
  const masked: string = maskChatCode(message.content);
  const picks = new Map<string, ChatComposerPick>();
  const text: string = message.content.replace(
    TOKEN_PATTERN,
    (token: string, target: string | undefined, ticketId: string | undefined, offset: number) => {
      if (!masked.startsWith(token, offset)) return token;
      let display: string | undefined;
      if (ticketId !== undefined) {
        display = keysById.get(ticketId.toLowerCase());
      } else if (target === CHAT_MENTION_EVERYONE || target === CHAT_MENTION_HERE) {
        display = `@${target}`;
      } else if (target !== undefined) {
        display = `@${chatUserDisplayName(usersById.get(target.toLowerCase()), unknownMember)}`;
      }
      if (!display) return token;
      // Two tokens shown the same way (two members of one name) cannot both
      // be told apart in plain text: the second keeps its token.
      const known: ChatComposerPick | undefined = picks.get(display);
      if (known && known.token !== token) return token;
      picks.set(display, { display, token });
      return display;
    }
  );
  return { text, picks: [...picks.values()] };
}

/**
 * The text of an unconfirmed message of this device: user tokens as
 * `@Name` from the member list, so a mention reads the same before and
 * after the server confirms it.
 */
export function chatOutboxText(
  content: string,
  members: readonly ChatMemberResponse[],
  unknownMember: string
): string {
  const byId = new Map<string, ChatMemberResponse>(
    members.map((member) => [member.userId.toLowerCase(), member])
  );
  const masked: string = maskChatCode(content);
  return content.replace(
    new RegExp(CHAT_USER_TOKEN_PATTERN.source, "g"),
    (token: string, target: string, offset: number) => {
      if (!masked.startsWith(token, offset)) return token;
      if (target === CHAT_MENTION_EVERYONE || target === CHAT_MENTION_HERE) return `@${target}`;
      const member: ChatMemberResponse | undefined = byId.get(target.toLowerCase());
      return `@${(member ? chatMemberName(member) : "") || unknownMember}`;
    }
  );
}
