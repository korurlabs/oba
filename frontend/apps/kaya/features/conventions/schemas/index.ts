import { z } from "zod";

// ── Request Schemas ─────────────────────────────────────────────────
/** `GET /conventions`: the convention docs of one to 20 versions. */
export const conventionQueryRequestSchema = z.object({
  versionId: z.array(z.string().min(1)).min(1).max(20),
});

// ── Response Schemas ────────────────────────────────────────────────
/**
 * A convention doc of one version. `path` is below `docs/private/conventions/`,
 * null for a convention only a skill holds.
 */
export const conventionListResponseSchema = z.object({
  id: z.string(),
  versionId: z.string(),
  path: z.string().nullish(),
  title: z.string().nullish(),
  /** The root doc this doc of a mounted pack replaces. */
  overridesPath: z.string().nullish(),
  sizeBytes: z.number(),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type ConventionQueryRequest = z.infer<typeof conventionQueryRequestSchema>;
export type ConventionListResponse = z.infer<typeof conventionListResponseSchema>;
