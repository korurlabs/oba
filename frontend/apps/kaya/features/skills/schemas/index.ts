import { z } from "zod";

// ── Request Schemas ─────────────────────────────────────────────────
/** `GET /skills`: the skills of one to 20 versions. */
export const skillQueryRequestSchema = z.object({
  versionId: z.array(z.string().min(1)).min(1).max(20),
});

// ── Response Schemas ────────────────────────────────────────────────
/**
 * A skill of one version, from its `SKILL.md` frontmatter. `path` is its
 * directory below `.agents/skills/`, which is also its name.
 */
export const skillListResponseSchema = z.object({
  id: z.string(),
  versionId: z.string(),
  path: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  /** The size of `SKILL.md`. */
  sizeBytes: z.number(),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type SkillQueryRequest = z.infer<typeof skillQueryRequestSchema>;
export type SkillListResponse = z.infer<typeof skillListResponseSchema>;
