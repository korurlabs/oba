import { createLoader, parseAsStringLiteral } from "nuqs/server";
import type { Values } from "nuqs";

/** The Templates page's tabs, in the order they are shown. */
export const TEMPLATE_PAGE_TABS = ["documents", "mail"] as const;
export type TemplatePageTab = (typeof TEMPLATE_PAGE_TABS)[number];

export function isTemplatePageTab(value: string): value is TemplatePageTab {
  return (TEMPLATE_PAGE_TABS as readonly string[]).includes(value);
}

/**
 * The Templates page's tab in the URL, so a tab can be reloaded and shared
 * and the Outreaches page can link to the mail templates. Server-safe: the
 * page reads it to prefetch only the tab that shows.
 */
export const templatePageTabParams = {
  tab: parseAsStringLiteral(TEMPLATE_PAGE_TABS).withDefault("documents"),
};

export type TemplatePageTabParams = Values<typeof templatePageTabParams>;

export const loadTemplatePageTabParams = createLoader(templatePageTabParams);
