import { htmlPlainText, parseHtmlBody } from "@/components/ticket/descriptionHtml";
import type { ApiError } from "@/lib/api";
import type { Comment, SubTicket, TicketDetail } from "@/lib/schemas/tickets";

/*
 * Pure rules of the ticket screen: ticket keys and links, names, sub-ticket
 * progress, the description text and comment mentions. No React here, so the
 * rules run under `bun test`.
 */

/** A ticket key: the board prefix (upper-case letters, digits, underscores), a dash and the number. */
export const TICKET_KEY_PATTERN = /^[A-Z0-9_]+-[0-9]+$/;

/** The app languages that prefix a Kazan web path, the locales of Kazan web. */
export const WEB_LOCALES: readonly string[] = ["en", "nl", "tr"];

/**
 * A route or link parameter as a ticket key, upper-cased, or null when it is
 * not one. Keys are stored upper-case, so a key typed in lower case still
 * opens its ticket.
 */
export function normalizeTicketKey(raw: string | string[] | undefined | null): string | null {
  const value: string | undefined = Array.isArray(raw) ? raw[0] : (raw ?? undefined);
  if (!value) return null;
  let decoded: string;
  try {
    decoded = decodeURIComponent(value);
  } catch (error: unknown) {
    // A malformed escape is not a key; the caller shows "ticket not found".
    console.warn("[ticket] undecodable key", error instanceof Error ? error.message : error);
    return null;
  }
  const key: string = decoded.trim().toUpperCase();
  return TICKET_KEY_PATTERN.test(key) ? key : null;
}

/**
 * The Kazan web path of a ticket in a language, the path Copy link shares.
 * A language Kazan web has no pages in gets the English page.
 */
export function ticketWebPath(language: string, ticketKey: string): string {
  const locale: string = WEB_LOCALES.includes(language) ? language : "en";
  return `/${locale}/ticket/${encodeURIComponent(ticketKey)}`;
}

/**
 * The ticket key a link opens, or null for any other link:
 *
 * - `kazan://ticket/{key}`, the app's own link;
 * - `https://{kazanWebHost}/{locale}/ticket/{key}`, the Kazan web page of a
 *   ticket, which iOS universal links and Android app links hand to the app.
 *
 * The web form needs one of the Kazan web locales, so `/en/ticket/new` (the
 * web new-ticket form) and other web pages stay out.
 */
export function ticketKeyFromLink(link: string, kazanWebHost: string | null): string | null {
  const schemeMatch: RegExpMatchArray | null = link.match(
    /^kazan:\/\/ticket\/([^/?#]+)\/?(?:[?#].*)?$/i
  );
  if (schemeMatch) return normalizeTicketKey(schemeMatch[1]);

  const webMatch: RegExpMatchArray | null = link.match(
    /^https:\/\/([^/?#]+)\/([a-z]{2})\/ticket\/([^/?#]+)\/?(?:[?#].*)?$/i
  );
  if (!webMatch || !kazanWebHost) return null;
  const [, host, locale, key] = webMatch;
  if (host?.toLowerCase() !== kazanWebHost.toLowerCase()) return null;
  if (!locale || !WEB_LOCALES.includes(locale.toLowerCase())) return null;
  return normalizeTicketKey(key);
}

/** The host of an absolute URL, or null when it has none. */
export function hostOf(url: string): string | null {
  const match: RegExpMatchArray | null = url.match(/^https?:\/\/([^/?#:]+)/i);
  return match?.[1]?.toLowerCase() ?? null;
}

type NamedPerson = {
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
};

/** A person's first and last name, else the email, else the fallback. */
export function personName(person: NamedPerson | null | undefined, fallback: string): string {
  if (!person) return fallback;
  const name: string = [person.firstName, person.lastName].filter(Boolean).join(" ").trim();
  return name || person.email?.trim() || fallback;
}

/** Who reported the ticket: a user, an integration such as an alert route, or nobody known. */
export function reporterName(
  ticket: Partial<Pick<TicketDetail, "createdBy" | "createdBySystemActor">>,
  fallback: string
): string {
  if (ticket.createdBy) return personName(ticket.createdBy, fallback);
  return ticket.createdBySystemActor?.name?.trim() || fallback;
}

/** The author of a comment by name. */
export function commentAuthorName(comment: Comment, fallback: string): string {
  return personName(
    {
      firstName: comment.authorFirstName,
      lastName: comment.authorLastName,
      email: comment.authorEmail,
    },
    fallback
  );
}

/** Done and total sub-tickets, for "2 of 4" and the progress bar. */
export function subTicketProgress(subTickets: readonly SubTicket[] | null | undefined): {
  done: number;
  total: number;
  percent: number;
} {
  const list: readonly SubTicket[] = subTickets ?? [];
  const done: number = list.filter((ticket) => ticket.status === "DONE").length;
  const total: number = list.length;
  return { done, total, percent: total === 0 ? 0 : Math.round((done / total) * 100) };
}

/** Sub-tickets with the open ones first, each group in the order the API gave. */
export function orderSubTickets(subTickets: readonly SubTicket[] | null | undefined): SubTicket[] {
  const list: readonly SubTicket[] = subTickets ?? [];
  return [
    ...list.filter((ticket) => ticket.status !== "DONE"),
    ...list.filter((ticket) => ticket.status === "DONE"),
  ];
}

/**
 * The description as plain text. Kazan web writes Markdown and may embed
 * images as `<img>` tags or `![alt](url)`; the phone shows each image as the
 * placeholder and keeps only the text of other HTML, read by an HTML parser
 * so no markup reaches the reader however the tags are written.
 */
export function descriptionText(
  markdown: string | null | undefined,
  imagePlaceholder: string
): string {
  if (!markdown) return "";
  return htmlPlainText(parseHtmlBody(markdown), imagePlaceholder, (text) =>
    text.replace(/!\[[^\]]*\]\([^)]*\)/g, imagePlaceholder)
  )
    .replace(/\u00a0/g, " ")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

/**
 * Whether a ticket's comments are read from the API: only while the app shows
 * comments (`TICKET_COMMENTS_ENABLED`), and once the organization and the
 * ticket are known.
 */
export function commentsReadable(
  commentsEnabled: boolean,
  orgId: string | null,
  ticket: { boardId: string; ticketId: string } | undefined
): boolean {
  return commentsEnabled && !!orgId && !!ticket;
}

/**
 * The message of the delete question. It names how many comments go with the
 * ticket only while the app shows comments; otherwise it speaks of the
 * ticket's data, as the Kazan web delete question does.
 */
export function ticketDeleteMessageKey(
  commentsEnabled: boolean
): "ticketDeleteMessage" | "ticketDeleteMessageNoComments" {
  return commentsEnabled ? "ticketDeleteMessage" : "ticketDeleteMessageNoComments";
}

/**
 * Only the author may change or delete a comment, and only while they may
 * still write on the board (`canWriteBoard`): the API checks the board role
 * first, so an author who became a viewer or a guest is refused as well.
 */
export function canEditComment(
  comment: Comment,
  meId: string | undefined,
  canWrite: boolean
): boolean {
  return canWrite && !!meId && comment.authorId === meId;
}

/** The backend's code for a comment that does not exist on the ticket. */
export const COMMENT_NOT_FOUND_CODE = "KAZ_NOT_007";

/** A comment in the composer: its text with `@Name` mentions and the people they name. */
export interface CommentDraft {
  text: string;
  picked: MentionPerson[];
}

/**
 * True when saving an edit failed because the comment can no longer be
 * reached at its address: it was deleted, or its ticket or board is gone or
 * closed to the user. Saving again gets the same 404, so the edit ends.
 */
export function isCommentEditGone(error: Pick<ApiError, "kind">): boolean {
  return error.kind === "not-found";
}

/** True when a comment was changed after it was posted (a save within a second is the post itself). */
export function isCommentEdited(comment: Comment): boolean {
  const created: number = Date.parse(comment.createdAt);
  const updated: number = Date.parse(comment.updatedAt);
  if (Number.isNaN(created) || Number.isNaN(updated)) return false;
  return updated - created > 1000;
}

/** Comments oldest first, so the newest sits next to the composer. */
export function orderComments(comments: readonly Comment[]): Comment[] {
  return [...comments].sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt));
}

// Mentions. A comment stores a mention as `<@userId>`, the token chat
// messages use, so a mention survives a rename and can later drive
// notifications. The composer shows `@Name` and swaps in the tokens on send.

const UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

/** Group 1 is the mentioned user's id. */
export const MENTION_TOKEN_PATTERN = new RegExp(`<@(${UUID_PATTERN})>`, "g");

/** The token stored for a mention of a user. */
export const mentionToken = (userId: string): string => `<@${userId}>`;

/** A person who can be mentioned: a member of the ticket's board. */
export interface MentionPerson {
  userId: string;
  name: string;
}

/** A piece of comment text: plain text, or a mention with the person's name when known. */
export type CommentSegment =
  | { kind: "text"; text: string }
  | { kind: "mention"; userId: string; name: string | null };

/** Splits stored comment text into text and mentions, naming each mention from `people`. */
export function commentSegments(
  content: string,
  people: ReadonlyMap<string, string>
): CommentSegment[] {
  const segments: CommentSegment[] = [];
  let last: number = 0;
  for (const match of content.matchAll(MENTION_TOKEN_PATTERN)) {
    const index: number = match.index ?? 0;
    if (index > last) segments.push({ kind: "text", text: content.slice(last, index) });
    const userId: string = (match[1] ?? "").toLowerCase();
    segments.push({ kind: "mention", userId, name: people.get(userId) ?? null });
    last = index + match[0].length;
  }
  if (last < content.length) segments.push({ kind: "text", text: content.slice(last) });
  return segments;
}

/** The mention being typed at the cursor: the `@` position and the text after it. */
export interface MentionQuery {
  start: number;
  query: string;
}

/**
 * The mention being typed at the cursor, or null. A mention starts with `@`
 * at the start of the text or after a space or line break, and its query has
 * no line break and at most one space ("@Ayşe K").
 */
export function activeMentionQuery(text: string, cursor: number): MentionQuery | null {
  const before: string = text.slice(0, cursor);
  const match: RegExpMatchArray | null = before.match(/(^|\s)@([^\s@]*(?: [^\s@]*)?)$/);
  if (!match || match.index === undefined) return null;
  const start: number = match.index + (match[1]?.length ?? 0);
  return { start, query: match[2] ?? "" };
}

/**
 * The mention the user is typing, or null. The text the composer started
 * with (a comment being edited, a kept draft) opens no list until the user
 * changes it, so editing a comment that ends in a mention does not open the
 * list over it.
 */
export function typedMentionQuery(
  text: string,
  cursor: number,
  startText: string
): MentionQuery | null {
  return text === startText ? null : activeMentionQuery(text, cursor);
}

/** People whose name contains the query, in the given order, at most `limit`. */
export function mentionCandidates(
  people: readonly MentionPerson[],
  query: string,
  limit: number = 6
): MentionPerson[] {
  const needle: string = query.trim().toLocaleLowerCase();
  return people
    .filter((person) => needle === "" || person.name.toLocaleLowerCase().includes(needle))
    .slice(0, limit);
}

/** The draft after picking a person for the mention at `query`, with the cursor after it. */
export function insertMention(
  text: string,
  cursor: number,
  query: MentionQuery,
  person: MentionPerson
): { text: string; cursor: number } {
  const inserted: string = `@${person.name} `;
  const next: string = text.slice(0, query.start) + inserted + text.slice(cursor);
  return { text: next, cursor: query.start + inserted.length };
}

/**
 * The text to store: each picked person's `@Name` becomes their token. Longer
 * names go first, so "@Ayşe Kaya" is not taken for "@Ayşe". A picked name the
 * user has since deleted is simply not found.
 */
export function encodeMentions(text: string, picked: readonly MentionPerson[]): string {
  const byLength: MentionPerson[] = [...picked].sort((a, b) => b.name.length - a.name.length);
  let result: string = text;
  for (const person of byLength) {
    result = result.split(`@${person.name}`).join(mentionToken(person.userId));
  }
  return result;
}

/**
 * Stored text back as composer text, for editing a comment: each token becomes
 * `@Name`, and the named people count as picked so saving keeps the tokens. A
 * token of someone no longer on the board stays as it is.
 */
export function decodeMentions(content: string, people: ReadonlyMap<string, string>): CommentDraft {
  const picked: Map<string, MentionPerson> = new Map();
  const text: string = content.replace(MENTION_TOKEN_PATTERN, (token: string, id: string) => {
    const userId: string = id.toLowerCase();
    const name: string | undefined = people.get(userId);
    if (!name) return token;
    picked.set(userId, { userId, name });
    return `@${name}`;
  });
  return { text, picked: [...picked.values()] };
}
