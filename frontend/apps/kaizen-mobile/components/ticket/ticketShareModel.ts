/*
 * What the system share menu receives for a ticket. No React and no native
 * module, so `bun test` covers both platforms.
 */

/** The ticket a share names: its key, and its title when known. */
export interface TicketShareSubject {
  key: string;
  title?: string;
}

/** The content handed to React Native's `Share.share`. */
export interface TicketShareContent {
  /** The subject line of mail-like targets; Android also titles its chooser with it. */
  title: string;
  message: string;
  /** iOS only: the link as its own item, so targets show a link preview. */
  url?: string;
}

/**
 * The share content of a ticket: "KEY Title" and the ticket's web link. iOS
 * takes the link as a separate item next to the text. Android shares only
 * the message, so there the link goes on its own line under the text.
 */
export function ticketShareContent(
  ticket: TicketShareSubject,
  url: string,
  platform: string
): TicketShareContent {
  const title: string = ticket.title?.trim() ? `${ticket.key} ${ticket.title.trim()}` : ticket.key;
  if (platform === "ios") return { title, message: title, url };
  return { title, message: `${title}\n${url}` };
}
