import { z } from "zod";

import { EntityKindSchema } from "@/features/versions/schemas";

// ── Enum Schemas ───────────────────────────────────────────────────
/** Whether the scope delivers a link's target: the path itself, or for a directory a file below it. */
export const LinkStatusSchema = z.enum(["FOUND", "BROKEN"]);
export type LinkStatus = z.infer<typeof LinkStatusSchema>;
export const LinkStatus = LinkStatusSchema.enum;

/**
 * Why one file depends on another: a Markdown link, a rule citing a
 * convention, or a skill made of a part.
 */
export const DependencyRelationSchema = z.enum([
  "MARKDOWN_LINK",
  "RULE_CONVENTION",
  "SKILL_PART",
]);
export type DependencyRelation = z.infer<typeof DependencyRelationSchema>;
export const DependencyRelation = DependencyRelationSchema.enum;

// ── Limits ─────────────────────────────────────────────────────────
// The limit equals the server's; the server stays the authority.
export const DEPENDENCY_VERSIONS_MAX = 20;

// ── Request Schemas ─────────────────────────────────────────────────
/**
 * `GET /links`: one version's own files (`versionId`), a repository's
 * newest release (`repositoryId`), or that release with an open draft or
 * change composed in place of its owner's current version (both).
 */
export const linkQueryRequestSchema = z
  .object({
    versionId: z.string().min(1).optional(),
    repositoryId: z.string().min(1).optional(),
  })
  .refine((query) => query.versionId !== undefined || query.repositoryId !== undefined, {
    message: "Name a version or a repository.",
  });

/**
 * `GET /links/dependencies`: one path in the scope of 1 to 20 versions; on a
 * path two versions give, the first one named wins.
 */
export const dependencyQueryRequestSchema = z.object({
  versionId: z.array(z.string().min(1)).min(1).max(DEPENDENCY_VERSIONS_MAX),
  path: z.string().min(1).max(500),
});

// ── Response Schemas ────────────────────────────────────────────────
/**
 * A link from one delivered Markdown file to a delivered path. `targetPath` is
 * repository-relative and ends with `/` for a directory; `line` is 1-based.
 */
export const linkResponseSchema = z.object({
  sourcePath: z.string(),
  targetPath: z.string(),
  line: z.number(),
  status: LinkStatusSchema,
});

/** A file on the other end of a dependency; `versionId` is null when the scope does not deliver it. */
export const dependencySchema = z.object({
  path: z.string(),
  /** Null for a directory or a path no kind takes. */
  kind: EntityKindSchema.nullish(),
  relation: DependencyRelationSchema,
  status: LinkStatusSchema,
  versionId: z.string().nullish(),
});

/** What one file links to and what uses it, each sorted by relation, then path. */
export const dependencyResponseSchema = z.object({
  path: z.string(),
  kind: EntityKindSchema.nullish(),
  linksTo: z.array(dependencySchema),
  usedBy: z.array(dependencySchema),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type LinkQueryRequest = z.infer<typeof linkQueryRequestSchema>;
export type LinkResponse = z.infer<typeof linkResponseSchema>;
export type DependencyQueryRequest = z.infer<typeof dependencyQueryRequestSchema>;
export type Dependency = z.infer<typeof dependencySchema>;
export type DependencyResponse = z.infer<typeof dependencyResponseSchema>;
