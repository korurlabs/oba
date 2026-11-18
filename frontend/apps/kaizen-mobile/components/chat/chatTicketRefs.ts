import {
  parseChatTokens,
  type ChatLinkedTicketResponse,
  type ChatTicketRefResponse,
} from "@/lib/schemas/chat";

/*
 * Ticket references in a message, read the way the web chat reads them. A
 * `<#ticketId>` token shows as a chip with the key and title snapshot the
 * server took at send time; a ticket key typed as plain text links only when
 * the server listed it in `linkedTickets`, that is when the reader may open
 * it. Ticket URLs stay links, as on the web.
 */

/** A ticket key as the server finds plain keys: a board prefix of at most 10 characters, `-`, a number. */
const TICKET_KEY_PATTERN = /\b[A-Za-z][A-Za-z0-9]{0,9}-\d{1,9}\b/g;

/** A chip shows about one line of a ticket title; the rest is cut with an ellipsis. */
export const CHAT_TICKET_CHIP_TITLE_MAX = 40;

/**
 * Finds the snapshot of a `<#ticketId>` token. A reference whose ticket row
 * was removed has no ID any more; it is found by the position of its token
 * among the message's distinct ticket tokens, which is the order of `refs`.
 */
export function chatTicketRefFinder(
  content: string,
  refs: readonly ChatTicketRefResponse[]
): (ticketId: string) => ChatTicketRefResponse | undefined {
  const byId = new Map<string, ChatTicketRefResponse>();
  for (const ref of refs) {
    if (ref.ticketId) byId.set(ref.ticketId.toLowerCase(), ref);
  }
  let order: string[] | null = null;
  return (ticketId: string): ChatTicketRefResponse | undefined => {
    const id: string = ticketId.toLowerCase();
    const found: ChatTicketRefResponse | undefined = byId.get(id);
    if (found) return found;
    order = order ?? parseChatTokens(content).ticketIds;
    const position: number = order.indexOf(id);
    const byPosition: ChatTicketRefResponse | undefined =
      position >= 0 ? refs[position] : undefined;
    return byPosition && !byPosition.ticketId ? byPosition : undefined;
  };
}

/** A ticket title cut to one chip line. */
export function chatTicketChipTitle(
  title: string,
  max: number = CHAT_TICKET_CHIP_TITLE_MAX
): string {
  const trimmed: string = title.trim();
  if (trimmed.length <= max) return trimmed;
  return `${trimmed.slice(0, max - 1).trimEnd()}…`;
}

/** A run of plain text, or a ticket key the reader can open. */
export type ChatTicketTextPart =
  | { kind: "text"; text: string }
  | { kind: "key"; ticketKey: string };

/** The keys of a message the reader can open, for `splitChatTicketKeys`. */
export function chatLinkedTicketKeys(
  linked: readonly ChatLinkedTicketResponse[]
): ReadonlySet<string> {
  return new Set<string>(linked.map((ticket) => ticket.ticketKey));
}

/**
 * Plain text with the ticket keys the server linked split out. A key the
 * server did not list (the reader cannot open it, or it is no ticket) stays
 * text, and the match is exact, as the web's.
 */
export function splitChatTicketKeys(
  text: string,
  linkedKeys: ReadonlySet<string>
): ChatTicketTextPart[] {
  if (text.length === 0) return [];
  if (linkedKeys.size === 0) return [{ kind: "text", text }];
  const parts: ChatTicketTextPart[] = [];
  let last = 0;
  for (const match of text.matchAll(TICKET_KEY_PATTERN)) {
    if (!linkedKeys.has(match[0])) continue;
    const at: number = match.index ?? 0;
    if (at > last) parts.push({ kind: "text", text: text.slice(last, at) });
    parts.push({ kind: "key", ticketKey: match[0] });
    last = at + match[0].length;
  }
  if (last < text.length) parts.push({ kind: "text", text: text.slice(last) });
  return parts;
}
