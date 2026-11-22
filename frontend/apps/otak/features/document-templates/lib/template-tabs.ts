import { createLoader, parseAsStringLiteral } from "nuqs/server";
import type { Values } from "nuqs";

/**
 * The sections the Templates page once had as tabs. Mail templates moved to
 * their own page; the value stays readable so a saved or linked
 * `/templates?tab=mail` still reaches them.
 */
export const TEMPLATE_PAGE_TABS = ["documents", "mail"] as const;
export type TemplatePageTab = (typeof TEMPLATE_PAGE_TABS)[number];

export function isTemplatePageTab(value: string): value is TemplatePageTab {
  return (TEMPLATE_PAGE_TABS as readonly string[]).includes(value);
}

/**
 * The former tab in the URL. Server-safe: the Templates page reads it and
 * sends `tab=mail` on to the mail page.
 */
export const templatePageTabParams = {
  tab: parseAsStringLiteral(TEMPLATE_PAGE_TABS).withDefault("documents"),
};

export type TemplatePageTabParams = Values<typeof templatePageTabParams>;

export const loadTemplatePageTabParams = createLoader(templatePageTabParams);
