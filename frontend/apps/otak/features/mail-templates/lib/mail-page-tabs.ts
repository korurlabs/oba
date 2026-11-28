import { createLoader, parseAsStringLiteral } from "nuqs/server";
import type { Values } from "nuqs";

/** The Mail page's tabs, in the order they are shown. */
export const MAIL_PAGE_TABS = ["templates", "senders"] as const;
export type MailPageTab = (typeof MAIL_PAGE_TABS)[number];

export function isMailPageTab(value: string): value is MailPageTab {
  return (MAIL_PAGE_TABS as readonly string[]).includes(value);
}

/**
 * The Mail page's tab in the URL, so a tab can be reloaded and shared, and
 * the send dialog can link straight to the senders. Server-safe: the page
 * reads it to prefetch only the tab that shows.
 */
export const mailPageTabParams = {
  tab: parseAsStringLiteral(MAIL_PAGE_TABS).withDefault("templates"),
};

export type MailPageTabParams = Values<typeof mailPageTabParams>;

export const loadMailPageTabParams = createLoader(mailPageTabParams);
