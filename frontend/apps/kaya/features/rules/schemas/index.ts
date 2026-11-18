import { z } from "zod";

// ── Enum Schemas ───────────────────────────────────────────────────
/** Where an `AGENTS.md` sits: the repository root, `.agents/`, or the folder a pack is mounted at. */
export const AgentsFilePlacementSchema = z.enum(["ROOT", "AGENTS_DIR", "MOUNT_ROOT"]);
export type AgentsFilePlacement = z.infer<typeof AgentsFilePlacementSchema>;
export const AgentsFilePlacement = AgentsFilePlacementSchema.enum;

// ── Request Schemas ─────────────────────────────────────────────────
/** The list of one kind of agent file: the rows of one to 20 versions. */
export const ruleKindQueryRequestSchema = z.object({
  versionId: z.array(z.string().min(1)).min(1).max(20),
});

// ── Response Schemas ────────────────────────────────────────────────
/** A rule of one version. `path` is below `.agents/rules/`; no `paths` makes it always-on. */
export const ruleListResponseSchema = z.object({
  id: z.string(),
  versionId: z.string(),
  path: z.string(),
  description: z.string().nullish(),
  paths: z.array(z.string()),
  alwaysOn: z.boolean(),
  /** The root rule this rule of a mounted pack narrows. */
  overridesPath: z.string().nullish(),
  /** The convention docs the rule links, each from the repository root. */
  conventions: z.array(z.object({ conventionPath: z.string() })),
  sizeBytes: z.number(),
});

/** An `AGENTS.md` of one version; its path follows from `placement`. */
export const agentsFileListResponseSchema = z.object({
  id: z.string(),
  versionId: z.string(),
  placement: AgentsFilePlacementSchema,
  sizeBytes: z.number(),
});

/** A role of one version; `path` is below `.agents/roles/`. */
export const roleListResponseSchema = z.object({
  id: z.string(),
  versionId: z.string(),
  path: z.string(),
  title: z.string().nullish(),
  sizeBytes: z.number(),
});

/** A subagent of one version; `path` is below `.claude/agents/`. */
export const subagentListResponseSchema = z.object({
  id: z.string(),
  versionId: z.string(),
  path: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  model: z.string().nullish(),
  maxTurns: z.number().int().nullish(),
  /** Null when the frontmatter names no tools, which differs from an empty list. */
  tools: z.array(z.string()).nullish(),
  sizeBytes: z.number(),
});

/** A workflow of one version; `path` is below `.agents/workflows/`, null for one only a skill holds. */
export const workflowListResponseSchema = z.object({
  id: z.string(),
  versionId: z.string(),
  path: z.string().nullish(),
  title: z.string().nullish(),
  sizeBytes: z.number(),
});

/** A template of one version; `path` is below `.agents/templates/`. */
export const templateListResponseSchema = z.object({
  id: z.string(),
  versionId: z.string(),
  path: z.string(),
  title: z.string().nullish(),
  sizeBytes: z.number(),
});

/** A hook script of one version; `path` is below `.agents/hooks/`. */
export const hookListResponseSchema = z.object({
  id: z.string(),
  versionId: z.string(),
  path: z.string(),
  executable: z.boolean(),
  sizeBytes: z.number(),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type RuleKindQueryRequest = z.infer<typeof ruleKindQueryRequestSchema>;
export type RuleListResponse = z.infer<typeof ruleListResponseSchema>;
export type AgentsFileListResponse = z.infer<typeof agentsFileListResponseSchema>;
export type RoleListResponse = z.infer<typeof roleListResponseSchema>;
export type SubagentListResponse = z.infer<typeof subagentListResponseSchema>;
export type WorkflowListResponse = z.infer<typeof workflowListResponseSchema>;
export type TemplateListResponse = z.infer<typeof templateListResponseSchema>;
export type HookListResponse = z.infer<typeof hookListResponseSchema>;
