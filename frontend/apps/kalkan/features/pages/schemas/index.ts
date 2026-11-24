import { z } from "zod";
import { instantSchema } from "@oba/shared/lib/date-schemas";

// ── Enum Schemas ────────────────────────────────────────────────────

/** The content languages of a page; a page holds at most one translation per locale. */
export const KalkanLocaleSchema = z.enum(["TR", "EN"]);
export type KalkanLocale = z.infer<typeof KalkanLocaleSchema>;
export const KalkanLocale = KalkanLocaleSchema.enum;

/**
 * Access level on a root page, inherited by its whole subtree. Each level
 * includes the ones before it: EDIT can read, MANAGE can edit and also
 * changes the root's permissions.
 */
export const KalkanLevelSchema = z.enum(["VIEW", "EDIT", "MANAGE"]);
export type KalkanLevel = z.infer<typeof KalkanLevelSchema>;
export const KalkanLevel = KalkanLevelSchema.enum;

/** Whether `level` grants at least `minimum`, as the server compares them. */
export function isLevelAtLeast(level: KalkanLevel | null | undefined, minimum: KalkanLevel) {
  if (!level) return false;
  const order = KalkanLevelSchema.options;
  return order.indexOf(level) >= order.indexOf(minimum);
}

// ── Constants ───────────────────────────────────────────────────────

/**
 * A slug starts with a letter or digit and may hold dots, so control ids such
 * as A.5.1 stay as they are. The server checks the same pattern.
 */
export const PAGE_SLUG_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/;

/** The limits the server validates; a form checks the same ones first. */
export const PAGE_LIMITS = {
  titleMax: 200,
  bodyMax: 500000,
  maxDepth: 10,
} as const;

/** How many pages the recent list asks for by default, and the range the server accepts. */
export const RECENT_LIMITS = { default: 20, min: 1, max: 100 } as const;

// ── Request Schemas ─────────────────────────────────────────────────

/** The title and Markdown body of a page in one locale. */
export const pageTranslationRequestSchema = z.object({
  locale: KalkanLocaleSchema,
  title: z.string().min(1).max(PAGE_LIMITS.titleMax),
  body: z.string().max(PAGE_LIMITS.bodyMax),
});

/**
 * A new page: a root page when `parentId` is null (the server requires an
 * organization ADMIN for that), else a child of the parent.
 */
export const pageCreateRequestSchema = z.object({
  parentId: z.guid().nullable(),
  slug: z.string().regex(PAGE_SLUG_PATTERN),
  /** Order among the siblings; null appends after the last. */
  position: z.number().int().min(0).nullable().optional(),
  compliance: z.boolean(),
  translations: z.array(pageTranslationRequestSchema).min(1),
});

/**
 * A change of a page against the version the editor loaded. An omitted or
 * null field keeps the stored value; only the given locales are written.
 */
export const pageUpdateRequestSchema = z.object({
  slug: z.string().regex(PAGE_SLUG_PATTERN).nullable().optional(),
  compliance: z.boolean().nullable().optional(),
  version: z.number().int(),
  translations: z.array(pageTranslationRequestSchema).nullable().optional(),
});

/** A new parent and position; a root page keeps no parent and only changes its position. */
export const pageMoveRequestSchema = z.object({
  parentId: z.guid().nullable(),
  position: z.number().int().min(0),
  version: z.number().int(),
});

/**
 * One grant of a root page's permission list; the PUT body is a list of these
 * and replaces every live grant. Group subjects are refused until
 * organization groups exist, so a grant names a `userId`.
 */
export const pagePermissionRequestSchema = z.object({
  userId: z.guid().nullable(),
  groupId: z.guid().nullable().optional(),
  level: KalkanLevelSchema,
});

// ── Response Schemas ────────────────────────────────────────────────

/** One page of the tree, without its bodies. */
export const pageListItemSchema = z.object({
  id: z.guid(),
  parentId: z.guid().nullable(),
  rootId: z.guid().nullable(),
  slug: z.string(),
  path: z.string(),
  position: z.number().int(),
  compliance: z.boolean(),
  /** Title per locale of the live translations. */
  titles: z.partialRecord(KalkanLocaleSchema, z.string()),
  /** The caller's level; set on root pages only. */
  level: KalkanLevelSchema.nullable(),
});

/** A page's live translation in one locale, with who changed it last. */
export const pageTranslationSchema = z.object({
  locale: KalkanLocaleSchema,
  title: z.string(),
  body: z.string(),
  updatedAt: instantSchema,
  updatedByName: z.string().nullable(),
});

/** A page with its translations and the caller's level on its root. */
export const pageDetailSchema = z.object({
  id: z.guid(),
  parentId: z.guid().nullable(),
  rootId: z.guid().nullable(),
  slug: z.string(),
  path: z.string(),
  compliance: z.boolean(),
  /** Version to send with the next update or move. */
  version: z.number().int(),
  level: KalkanLevelSchema,
  translations: z.array(pageTranslationSchema),
  updatedAt: instantSchema,
});

/** A live grant of a root page with the display name of its subject. */
export const pagePermissionSchema = z.object({
  id: z.guid(),
  userId: z.guid().nullable(),
  groupId: z.guid().nullable(),
  displayName: z.string(),
  level: KalkanLevelSchema,
});

/** One entry of a page's history, without its body; the list comes newest first. */
export const pageRevisionListItemSchema = z.object({
  id: z.guid(),
  locale: KalkanLocaleSchema,
  title: z.string(),
  version: z.number().int(),
  createdAt: instantSchema,
  createdByName: z.string().nullable(),
});

/** One entry of a page's history with the Markdown body the write gave. */
export const pageRevisionDetailSchema = pageRevisionListItemSchema.extend({
  body: z.string(),
});

/**
 * The top page of one delete the caller can MANAGE; a restore brings it back
 * with the pages the delete took. The list comes newest first.
 */
export const pageTrashItemSchema = z.object({
  id: z.guid(),
  /** In the request locale, else the first translation; null when the page had none. */
  title: z.string().nullable(),
  path: z.string(),
  deletedAt: instantSchema,
  deletedByName: z.string().nullable(),
  descendantCount: z.number().int(),
});

/** A live page the caller can VIEW, among the most recently changed ones. */
export const pageRecentItemSchema = z.object({
  id: z.guid(),
  /** In the request locale, else the first translation; null when the page has none. */
  title: z.string().nullable(),
  path: z.string(),
  /** Slug of the page's root, which names its section. */
  rootSlug: z.string(),
  updatedAt: instantSchema,
  updatedByName: z.string().nullable(),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type PageTranslationRequest = z.infer<typeof pageTranslationRequestSchema>;
export type PageCreateRequest = z.infer<typeof pageCreateRequestSchema>;
export type PageUpdateRequest = z.infer<typeof pageUpdateRequestSchema>;
export type PageMoveRequest = z.infer<typeof pageMoveRequestSchema>;
export type PagePermissionRequest = z.infer<typeof pagePermissionRequestSchema>;
export type PageListItem = z.infer<typeof pageListItemSchema>;
export type PageTranslation = z.infer<typeof pageTranslationSchema>;
export type PageDetail = z.infer<typeof pageDetailSchema>;
export type PagePermission = z.infer<typeof pagePermissionSchema>;
export type PageRevisionListItem = z.infer<typeof pageRevisionListItemSchema>;
export type PageRevisionDetail = z.infer<typeof pageRevisionDetailSchema>;
export type PageTrashItem = z.infer<typeof pageTrashItemSchema>;
export type PageRecentItem = z.infer<typeof pageRecentItemSchema>;
