import { parseAsString } from "nuqs/server";
import type { Values } from "nuqs";
import type { MailSenderListItem } from "../schemas";

/**
 * The sender list's search in the URL, so a narrowed list can be reloaded
 * and shared. The server lists every sender at once and has no search, so
 * the list is narrowed in the browser.
 */
export const mailSenderFilterParams = {
  search: parseAsString,
};

export type MailSenderFilterParams = Values<typeof mailSenderFilterParams>;

/** True when the search narrows the list. */
export function hasActiveMailSenderFilter(filters: MailSenderFilterParams): boolean {
  return !!filters.search?.trim();
}

/** The senders whose mail address contains the search, ignoring case. */
export function filterMailSenders(
  senders: MailSenderListItem[],
  filters: MailSenderFilterParams
): MailSenderListItem[] {
  const search = filters.search?.trim().toLowerCase();
  if (!search) return senders;
  return senders.filter((sender) => sender.mailAddress.toLowerCase().includes(search));
}
