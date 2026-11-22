import { z } from "zod";
import { isApiError } from "@oba/shared/api/axios";
import type { SelectOption } from "@oba/shared/features/form";
import { childrenOf, pageTitle } from "../lib/page-tree";
import {
  isLevelAtLeast,
  KalkanLevel,
  KalkanLocaleSchema,
  type KalkanLocale,
  type PageListItem,
  PAGE_LIMITS,
  PAGE_SLUG_PATTERN,
  type PageCreateRequest,
  type PageDetail,
  type PageTranslationRequest,
  type PageUpdateRequest,
} from "./index";

// ── Form Schema ─────────────────────────────────────────────────────

/** The content locales in the order the editor shows their tabs. */
export const PAGE_FORM_LOCALES = KalkanLocaleSchema.options;

const translationFormSchema = (t: (key: string) => string) =>
  z.object({
    title: z.string().trim().max(PAGE_LIMITS.titleMax, t("errors.titleMax")),
    body: z.string().max(PAGE_LIMITS.bodyMax, t("errors.bodyMax")),
  });

/**
 * The page editor's form. A page needs a title in at least one language; a
 * body without a title cannot be saved, as the server stores a translation
 * only with its title. `requiredLocales` are the languages the page already
 * has: an update can write a translation but never remove one, so their
 * titles must stay filled. `requireParent` is set for a new page, which is
 * always created under a parent: a root page is a section, set up on its own.
 */
export const createPageFormSchema = (
  t: (key: string) => string,
  {
    requiredLocales = [],
    requireParent = false,
  }: { requiredLocales?: readonly KalkanLocale[]; requireParent?: boolean } = {}
) =>
  z
    .object({
      /** The parent page; "" until one is picked. Only a new page sends it. */
      parentId: z.string(),
      slug: z
        .string()
        .trim()
        .min(1, t("errors.slugRequired"))
        .regex(PAGE_SLUG_PATTERN, t("errors.slugInvalid")),
      compliance: z.boolean(),
      translations: z.object({
        TR: translationFormSchema(t),
        EN: translationFormSchema(t),
      }),
    })
    .superRefine((values, context) => {
      if (requireParent && values.parentId === "") {
        context.addIssue({
          code: "custom",
          path: ["parentId"],
          message: t("errors.parentRequired"),
        });
      }
      const titled = PAGE_FORM_LOCALES.filter(
        (locale) => values.translations[locale].title.trim() !== ""
      );
      if (titled.length === 0) {
        // On every title, so the tab the user is on shows it.
        for (const locale of PAGE_FORM_LOCALES) {
          context.addIssue({
            code: "custom",
            path: ["translations", locale, "title"],
            message: t("errors.titleRequired"),
          });
        }
        return;
      }
      for (const locale of PAGE_FORM_LOCALES) {
        const translation = values.translations[locale];
        if (translation.title.trim() !== "") continue;
        if (requiredLocales.includes(locale)) {
          context.addIssue({
            code: "custom",
            path: ["translations", locale, "title"],
            message: t("errors.titleKept"),
          });
        } else if (translation.body.trim() !== "") {
          context.addIssue({
            code: "custom",
            path: ["translations", locale, "title"],
            message: t("errors.titleForBody"),
          });
        }
      }
    });

export type PageFormValues = z.infer<ReturnType<typeof createPageFormSchema>>;

// ── Slug ────────────────────────────────────────────────────────────

/** Turkish letters that do not decompose into a Latin letter and a mark. */
const TURKISH_LETTERS: Readonly<Record<string, string>> = {
  ı: "i",
  İ: "i",
  ş: "s",
  Ş: "s",
  ğ: "g",
  Ğ: "g",
  ç: "c",
  Ç: "c",
  ö: "o",
  Ö: "o",
  ü: "u",
  Ü: "u",
};

/**
 * The slug a title suggests: lower case Latin letters, digits, dots and
 * hyphens, so "A.5.1 Bilgi güvenliği" gives "a.5.1-bilgi-guvenligi". Dots
 * stay because control ids use them. Empty when the title has nothing usable.
 */
export function slugFromTitle(title: string): string {
  const latin = title
    .replace(/[ıİşŞğĞçÇöÖüÜ]/g, (letter) => TURKISH_LETTERS[letter] ?? letter)
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase();
  return latin
    .replace(/[^a-z0-9.]+/g, "-")
    .replace(/-{2,}/g, "-")
    .replace(/^[-.]+/, "")
    .slice(0, 100)
    .replace(/-+$/, "");
}

// ── Parent choice ───────────────────────────────────────────────────

/** How deep a page sits: a root page is at depth 1. */
function depthOf(page: Pick<PageListItem, "path">): number {
  return page.path.split("/").length;
}

/**
 * The pages a new page can go under, section by section in tree order: every
 * page of a root the caller can EDIT that is not already at the deepest level
 * the server allows. Each option carries its depth for the indent and is
 * grouped under its section's title.
 */
export function parentOptions(pages: readonly PageListItem[], uiLocale: string): SelectOption[] {
  const roots = pages
    .filter((page) => page.parentId === null && isLevelAtLeast(page.level, KalkanLevel.EDIT))
    .sort((a, b) => a.position - b.position);
  const options: SelectOption[] = [];
  const visit = (page: PageListItem, group: string) => {
    if (depthOf(page) >= PAGE_LIMITS.maxDepth) return;
    options.push({
      value: page.id,
      label: pageTitle(page, uiLocale),
      group,
      meta: { depth: depthOf(page) - 1 },
    });
    for (const child of childrenOf(pages, page.id)) {
      visit(child, group);
    }
  };
  for (const root of roots) {
    visit(root, pageTitle(root, uiLocale));
  }
  return options;
}

// ── Form Mapping ────────────────────────────────────────────────────

/** The empty form of a new page under `parentId` ("" when none is chosen yet). */
export function emptyPageFormValues(parentId: string): PageFormValues {
  return {
    parentId,
    slug: "",
    compliance: false,
    translations: {
      TR: { title: "", body: "" },
      EN: { title: "", body: "" },
    },
  };
}

/** The form of a stored page, with an empty title and body for a missing language. */
export function pageFormValuesFromDetail(page: PageDetail): PageFormValues {
  const values = emptyPageFormValues(page.parentId ?? "");
  values.slug = page.slug;
  values.compliance = page.compliance;
  for (const translation of page.translations) {
    values.translations[translation.locale] = {
      title: translation.title,
      body: translation.body,
    };
  }
  return values;
}

/** The languages a stored page has; the editor must keep their titles. */
export function storedLocales(page: PageDetail): KalkanLocale[] {
  return PAGE_FORM_LOCALES.filter((locale) =>
    page.translations.some((translation) => translation.locale === locale)
  );
}

/** The translations to send: every language with a title. */
function translationRequests(values: PageFormValues): PageTranslationRequest[] {
  return PAGE_FORM_LOCALES.filter((locale) => values.translations[locale].title.trim() !== "").map(
    (locale) => ({
      locale,
      title: values.translations[locale].title.trim(),
      body: values.translations[locale].body,
    })
  );
}

export function toPageCreateRequest(values: PageFormValues): PageCreateRequest {
  return {
    parentId: values.parentId,
    slug: values.slug.trim(),
    compliance: values.compliance,
    translations: translationRequests(values),
  };
}

/**
 * The update of a page against `version`, the version the editor loaded. A
 * stale version is refused (`KLK_STA_002`), so a change made meanwhile by
 * someone else is never overwritten.
 */
export function toPageUpdateRequest(values: PageFormValues, version: number): PageUpdateRequest {
  return {
    slug: values.slug.trim(),
    compliance: values.compliance,
    version,
    translations: translationRequests(values),
  };
}

/**
 * Whether a refused save means someone else saved the page after the editor
 * loaded it. The editor then keeps the user's text and offers to reload.
 */
export function isVersionConflict(error: unknown): boolean {
  return isApiError(error, "KLK_STA_002");
}

// ── Attachment links ────────────────────────────────────────────────

/**
 * A Markdown link to an uploaded file, as the body stores it. Brackets in the
 * name are escaped and spaces and parentheses in the address encoded, so
 * neither ends the link early.
 */
export function markdownLink(fileName: string, url: string): string {
  const label = fileName.replace(/([\\[\]])/g, "\\$1");
  const target = url.replace(/ /g, "%20").replace(/\(/g, "%28").replace(/\)/g, "%29");
  return `[${label}](${target})`;
}

/** `body` with `link` added as a paragraph of its own at the end. */
export function appendToBody(body: string, link: string): string {
  const trimmed = body.replace(/\s+$/, "");
  return trimmed === "" ? link : `${trimmed}\n\n${link}`;
}
