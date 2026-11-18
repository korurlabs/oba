import { z } from "zod";

// ── Limits ─────────────────────────────────────────────────────────
// The limits equal the server's; the server stays the authority.
export const LESSON_LIMITS = {
  pageSizeMax: 100,
  searchMax: 200,
  titleMax: 300,
  bodyMax: 4000,
} as const;

// ── Request Schemas ─────────────────────────────────────────────────
/**
 * `GET /lessons`: 1-based page, newest first. `organizationWide` true keeps
 * only the lessons of no repository; `sessionId` is a Claude Code session id,
 * and one Kaya has not seen yet matches nothing rather than failing.
 */
export const lessonQueryRequestSchema = z.object({
  page: z.number().int().min(1).optional(),
  amount: z.number().int().min(1).max(LESSON_LIMITS.pageSizeMax).optional(),
  repositoryId: z.string().optional(),
  organizationWide: z.boolean().optional(),
  userId: z.string().optional(),
  sessionId: z.string().optional(),
  search: z.string().max(LESSON_LIMITS.searchMax).optional(),
});

/**
 * A lesson as a member writes it. An update replaces every field, so the
 * repository is always stated: null applies the lesson to the whole organization.
 */
export const lessonRequestSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, "Enter a title.")
    .max(LESSON_LIMITS.titleMax, `A title has at most ${LESSON_LIMITS.titleMax} characters.`),
  body: z
    .string()
    .trim()
    .min(1, "Enter the text.")
    .max(LESSON_LIMITS.bodyMax, `The text has at most ${LESSON_LIMITS.bodyMax} characters.`),
  repositoryId: z.string().nullable(),
});

// ── Response Schemas ────────────────────────────────────────────────
/** A repository or user; `name` is null for a user who left the organization. */
export const lessonReferenceResponseSchema = z.object({
  id: z.string(),
  name: z.string().nullish(),
});

export const lessonSessionResponseSchema = z.object({
  /** Row id of the session. */
  id: z.string(),
  /** Claude Code session id, the id of the session page. */
  sessionId: z.string(),
  title: z.string().nullish(),
});

export const lessonListResponseSchema = z.object({
  id: z.string(),
  /** The rule as a sentence. */
  title: z.string(),
  /** Markdown written by members and agents, so it is untrusted. */
  body: z.string(),
  /** Null for a lesson of the whole organization. */
  repository: lessonReferenceResponseSchema.nullish(),
  /** The author. */
  user: lessonReferenceResponseSchema,
  /** The Claude Code session that recorded it; Kaya may not know that session. */
  claudeSessionId: z.string().nullish(),
  /** Null when no session recorded the lesson or Kaya does not know it, so no link can show. */
  session: lessonSessionResponseSchema.nullish(),
  createdAt: z.string(),
  updatedAt: z.string(),
  /** The caller is the author, an ADMIN or an OWNER. */
  canEdit: z.boolean(),
});

/** The values the list's selects offer: only those the organization's lessons hold. */
export const lessonFiltersResponseSchema = z.object({
  repositories: z.array(lessonReferenceResponseSchema),
  users: z.array(lessonReferenceResponseSchema),
});

export const lessonDetailResponseSchema = lessonListResponseSchema;

// ── Inferred Types ──────────────────────────────────────────────────
export type LessonQueryRequest = z.infer<typeof lessonQueryRequestSchema>;
export type LessonRequest = z.output<typeof lessonRequestSchema>;
export type LessonReferenceResponse = z.infer<typeof lessonReferenceResponseSchema>;
export type LessonListResponse = z.infer<typeof lessonListResponseSchema>;
export type LessonFiltersResponse = z.infer<typeof lessonFiltersResponseSchema>;
export type LessonSessionResponse = z.infer<typeof lessonSessionResponseSchema>;
export type LessonDetailResponse = z.infer<typeof lessonDetailResponseSchema>;
