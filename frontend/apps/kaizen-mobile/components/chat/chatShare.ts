import { chatTicketPick, type ChatComposerPick } from "@/components/chat/chatMentions";
import { chatSwitcherChannels } from "@/components/chat/chatScreenModel";
import type { ChatChannelResponse } from "@/lib/schemas/chat";

/*
 * Share to chat: a ticket goes into a channel's composer as a ticket chip,
 * and nothing is sent until the reader taps Send. The channel picker and the
 * composer are screens of the app stack, above the tabs, so Back from the
 * composer returns to the screen the share started from (the ticket) and
 * never through the Chat tab.
 */

/** The ticket a share carries: its ID makes the token, its key is what the composer shows. */
export interface ChatSharedTicket {
  id: string;
  key: string;
  /** Shown in the channel picker; optional, the chip itself needs only the ID and key. */
  title?: string;
}

/** The app path of the channel picker sheet. */
export const CHAT_SHARE_TICKET_PATHNAME = "/share/ticket" as const;

/** The app path of a channel's screen opened to share into it. */
export const CHAT_SHARE_CHANNEL_PATHNAME = "/share/channel/[channelId]" as const;

/**
 * The one entry point of Share to chat: push this from the screen that holds
 * the ticket. It opens the channel picker; the chosen channel opens with the
 * ticket in its composer.
 */
export function chatShareTicketHref(ticket: ChatSharedTicket) {
  return {
    pathname: CHAT_SHARE_TICKET_PATHNAME,
    params: { ticketId: ticket.id, ticketKey: ticket.key, ticketTitle: ticket.title ?? "" },
  };
}

/** The channel screen that shares `ticket`; the picker replaces itself with it. */
export function chatShareChannelHref(channelId: string, ticket: ChatSharedTicket) {
  return {
    pathname: CHAT_SHARE_CHANNEL_PATHNAME,
    params: { channelId, ticketId: ticket.id, ticketKey: ticket.key },
  };
}

const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
/** A ticket key as the server generates it: a board prefix of at most 10 characters, `-`, a number. */
const TICKET_KEY = /^[A-Za-z][A-Za-z0-9]{0,9}-\d{1,9}$/;

type ShareParams = {
  ticketId?: string | string[];
  ticketKey?: string | string[];
  ticketTitle?: string | string[];
};

function single(value: string | string[] | undefined): string {
  return (Array.isArray(value) ? value[0] : value) ?? "";
}

/**
 * The ticket of a share route's params, or null when they do not name one:
 * a link with a missing or malformed ID or key shares nothing.
 */
export function chatSharedTicketFromParams(params: ShareParams): ChatSharedTicket | null {
  const id: string = single(params.ticketId);
  const key: string = single(params.ticketKey);
  if (!UUID.test(id) || !TICKET_KEY.test(key)) return null;
  const title: string = single(params.ticketTitle);
  return title ? { id, key, title } : { id, key };
}

/** What a composer starts with to share a ticket. */
export interface ChatComposerInitialDraft {
  text: string;
  picks: ChatComposerPick[];
}

/**
 * The composer's text for a shared ticket: the ticket's key as a pick, the
 * same pick `#` makes, so it is sent as the `<#ticketId>` token the web
 * renders as its ticket chip, followed by a space for the reader's text.
 */
export function chatShareTicketDraft(ticket: ChatSharedTicket): ChatComposerInitialDraft {
  const pick: ChatComposerPick = chatTicketPick({ id: ticket.id, key: ticket.key });
  return { text: `${pick.display} `, picks: [pick] };
}

/**
 * The channels a ticket can be shared to, as the share sheet's grid shows
 * them: the general channels the reader belongs to, in the reader's order.
 * A search keeps the channels whose name holds the text, ignoring case, and
 * puts the names that start with it first, so typing a channel's first
 * letters brings it to the top row.
 */
export function chatShareTargets(
  ordered: readonly ChatChannelResponse[],
  search: string
): ChatChannelResponse[] {
  const text: string = search.trim().toLowerCase();
  const members: ChatChannelResponse[] = chatSwitcherChannels(ordered, text).filter(
    (channel) => channel.member
  );
  if (!text) return members;
  const starts: ChatChannelResponse[] = members.filter((channel) =>
    channel.name.toLowerCase().startsWith(text)
  );
  const contains: ChatChannelResponse[] = members.filter(
    (channel) => !channel.name.toLowerCase().startsWith(text)
  );
  return [...starts, ...contains];
}
