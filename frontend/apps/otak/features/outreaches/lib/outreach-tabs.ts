import { createLoader, parseAsStringLiteral } from "nuqs/server";
import type { Values } from "nuqs";

/** The Outreaches page's tabs, in the order they are shown. */
export const OUTREACH_PAGE_TABS = ["outreaches", "senders"] as const;
export type OutreachPageTab = (typeof OUTREACH_PAGE_TABS)[number];

export function isOutreachPageTab(value: string): value is OutreachPageTab {
  return (OUTREACH_PAGE_TABS as readonly string[]).includes(value);
}

/**
 * The Outreaches page's tab in the URL, so a tab can be reloaded and shared.
 * Server-safe: the page reads it to prefetch only the tab that shows.
 */
export const outreachPageTabParams = {
  tab: parseAsStringLiteral(OUTREACH_PAGE_TABS).withDefault("outreaches"),
};

export type OutreachPageTabParams = Values<typeof outreachPageTabParams>;

export const loadOutreachPageTabParams = createLoader(outreachPageTabParams);
