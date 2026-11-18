import { z } from "zod";

import { AgentsFilePlacementSchema } from "@/features/rules/schemas";

// ── Enum Schemas ───────────────────────────────────────────────────
/**
 * The kind a delivered path belongs to. SKILL_LINK is rendered from the skill
 * and never stored; TOOL_LINK is one of the three fixed links a tool reads.
 */
export const FileKindSchema = z.enum([
  "AGENTS_FILE",
  "RULE",
  "CONVENTION",
  "ROLE",
  "WORKFLOW",
  "TEMPLATE",
  "HOOK",
  "SUBAGENT",
  "SETTINGS",
  "SKILL",
  "SKILL_LINK",
  "TOOL_LINK",
]);
export type FileKind = z.infer<typeof FileKindSchema>;
export const FileKind = FileKindSchema.enum;

/** The part a file of a skill directory is, by the folder it sits in. */
export const SkillPartKindSchema = z.enum([
  "WORKFLOW",
  "SCRIPT",
  "CONVENTION",
  "REFERENCE",
  "TOOL_PARAMETER",
]);
export type SkillPartKind = z.infer<typeof SkillPartKindSchema>;
export const SkillPartKind = SkillPartKindSchema.enum;

/** What a file at a path holds: MARKDOWN carries YAML frontmatter, ANY is any text or binary. */
export const FileFormatSchema = z.enum(["MARKDOWN", "JSON", "YAML", "ANY", "SYMLINK"]);
export type FileFormat = z.infer<typeof FileFormatSchema>;
export const FileFormat = FileFormatSchema.enum;

/** The three links a repository keeps so every tool finds the sources under `.agents/`. */
export const ToolLinkNameSchema = z.enum(["ROOT_AGENTS_FILE", "CLAUDE_RULES", "CLAUDE_TEMPLATES"]);
export type ToolLinkName = z.infer<typeof ToolLinkNameSchema>;
export const ToolLinkName = ToolLinkNameSchema.enum;

/** The AI tools the tool view describes, in the server's order. */
export const AgentToolSchema = z.enum(["CLAUDE_CODE", "CODEX", "CURSOR", "GEMINI_CLI"]);
export type AgentTool = z.infer<typeof AgentToolSchema>;
export const AgentTool = AgentToolSchema.enum;

/** When a tool loads a path: every session, when it uses it, or when another file names it. */
export const ToolLoadingSchema = z.enum(["ALWAYS", "ON_DEMAND", "ON_REFERENCE"]);
export type ToolLoading = z.infer<typeof ToolLoadingSchema>;
export const ToolLoading = ToolLoadingSchema.enum;

/** Whether the scope gives a tool a path it reads. */
export const ToolPathStateSchema = z.enum(["DIRECT", "LINKED", "LINK_MISSING", "NOT_DELIVERED"]);
export type ToolPathState = z.infer<typeof ToolPathStateSchema>;
export const ToolPathState = ToolPathStateSchema.enum;

// ── Request Schemas ─────────────────────────────────────────────────
/** `GET /path-mappings`: the scope's versions, 0 to 20; none gives the rows without counts. */
export const pathMappingQueryRequestSchema = z.object({
  versionId: z.array(z.string().min(1)).max(20),
});

// ── Response Schemas ────────────────────────────────────────────────
/**
 * Where one kind lives in a repository. `part` is set on skill parts only,
 * `placement` on agents files only, `link` on tool links only; `count` is
 * null when no version is named.
 */
export const pathMappingResponseSchema = z.object({
  kind: FileKindSchema,
  part: SkillPartKindSchema.nullish(),
  placement: AgentsFilePlacementSchema.nullish(),
  link: ToolLinkNameSchema.nullish(),
  pathPattern: z.string(),
  /** Where a pack mounted in a folder delivers the kind; null when a mounted pack may not deliver it. */
  mountedPattern: z.string().nullish(),
  formats: z.array(FileFormatSchema),
  executable: z.boolean(),
  note: z.string().nullish(),
  count: z.number().int().nullish(),
});

/**
 * One path a tool reads. `linkPath` and `linkTarget` are null when the tool
 * reads the source directly; `kind`, `part` and `placement` name the path
 * mapping row of the source; `state` and `count` are null when no version is named.
 */
export const toolPathResponseSchema = z.object({
  readPath: z.string(),
  loading: ToolLoadingSchema,
  linkPath: z.string().nullish(),
  linkTarget: z.string().nullish(),
  sourcePattern: z.string(),
  kind: FileKindSchema,
  part: SkillPartKindSchema.nullish(),
  placement: AgentsFilePlacementSchema.nullish(),
  note: z.string().nullish(),
  state: ToolPathStateSchema.nullish(),
  count: z.number().int().nullish(),
});

/** A path a tool reads that Kaya does not deliver; a leading `~` means the user's home. */
export const toolGapResponseSchema = z.object({
  path: z.string(),
  note: z.string(),
});

/** Everything one tool reads, in the order it loads them, and what Kaya leaves out. */
export const toolMappingResponseSchema = z.object({
  tool: AgentToolSchema,
  name: z.string(),
  summary: z.string(),
  reads: z.array(toolPathResponseSchema),
  notDelivered: z.array(toolGapResponseSchema),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type PathMappingQueryRequest = z.infer<typeof pathMappingQueryRequestSchema>;
export type PathMappingResponse = z.infer<typeof pathMappingResponseSchema>;
export type ToolPathResponse = z.infer<typeof toolPathResponseSchema>;
export type ToolGapResponse = z.infer<typeof toolGapResponseSchema>;
export type ToolMappingResponse = z.infer<typeof toolMappingResponseSchema>;
