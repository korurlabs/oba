import { z } from "zod";

// ── Enum Schemas ───────────────────────────────────────────────────
export const VersionStatusSchema = z.enum([
  "DRAFT",
  "IN_REVIEW",
  "APPROVED",
  "REJECTED",
  "SUPERSEDED",
]);
export type VersionStatus = z.infer<typeof VersionStatusSchema>;
export const VersionStatus = VersionStatusSchema.enum;

export const VersionSourceSchema = z.enum(["MANUAL", "CLI", "SEED", "ROLLBACK"]);
export type VersionSource = z.infer<typeof VersionSourceSchema>;
export const VersionSource = VersionSourceSchema.enum;

export const FileModeSchema = z.enum(["FILE", "EXECUTABLE", "SYMLINK"]);
export type FileMode = z.infer<typeof FileModeSchema>;
export const FileMode = FileModeSchema.enum;

export const FileEncodingSchema = z.enum(["UTF_8", "BASE64"]);
export type FileEncoding = z.infer<typeof FileEncodingSchema>;
export const FileEncoding = FileEncodingSchema.enum;

export const EntityKindSchema = z.enum([
  "AGENTS_FILE",
  "RULE",
  "GUARDRAIL",
  "CONVENTION",
  "ROLE",
  "WORKFLOW",
  "TEMPLATE",
  "HOOK",
  "SUBAGENT",
  "SETTINGS",
  "SKILL",
  "SCRIPT",
  "REFERENCE",
  "TOOL_PARAMETER",
]);
export type EntityKind = z.infer<typeof EntityKindSchema>;
export const EntityKind = EntityKindSchema.enum;

export const FileChangeSchema = z.enum(["ADDED", "MODIFIED", "DELETED"]);
export type FileChange = z.infer<typeof FileChangeSchema>;
export const FileChange = FileChangeSchema.enum;

// ── Limits ─────────────────────────────────────────────────────────
// The limits equal the server's; the server stays the authority.
export const DRAFT_LIMITS = {
  pathMax: 500,
  /** 5 MiB of decoded content. */
  contentBytesMax: 5_242_880,
  /** The base64 length of 5 MiB, the longest `content` string the server takes. */
  contentCharsMax: 6_990_508,
  resolutionsMax: 5000,
} as const;

const path = z
  .string()
  .trim()
  .min(1, "Enter a path.")
  .max(DRAFT_LIMITS.pathMax, `A path has at most ${DRAFT_LIMITS.pathMax} characters.`);

/** Bytes of a text once the server decodes it as UTF-8. */
export function utf8Bytes(text: string): number {
  return new TextEncoder().encode(text).length;
}

// ── Request Schemas ─────────────────────────────────────────────────
/** `POST /drafts`: a new draft of one pack, copying its current version. */
export const draftCreateRequestSchema = z.object({ packId: z.string().min(1) });

/**
 * `PUT /drafts/{id}/files`: the whole file as it is to be stored, at most
 * 5 MiB decoded. `executable` is taken only for a hook or a skill script;
 * the server refuses it on any other path.
 */
export const draftFileSaveRequestSchema = z
  .object({
    path,
    encoding: FileEncodingSchema.optional(),
    content: z.string().max(DRAFT_LIMITS.contentCharsMax, "A file holds at most 5 MiB."),
    executable: z.boolean().optional(),
    /**
     * The root path a file of a mounted pack overrides. The server keeps a
     * marker a file already carries but adds none, and it stores what each
     * save sends, so a save of a marked file must send its marker again.
     */
    overridesPath: z.string().nullish(),
  })
  .superRefine((file, context) => {
    if (
      (file.encoding ?? "UTF_8") === "UTF_8" &&
      utf8Bytes(file.content) > DRAFT_LIMITS.contentBytesMax
    ) {
      context.addIssue({
        code: "custom",
        path: ["content"],
        message: "A file holds at most 5 MiB.",
      });
    }
  });

/** `POST /drafts/{id}/rebase`: which side wins for each path both the draft and the current version changed. */
export const draftRebaseRequestSchema = z
  .object({
    keepDraft: z.array(path).max(DRAFT_LIMITS.resolutionsMax).optional(),
    takeCurrent: z.array(path).max(DRAFT_LIMITS.resolutionsMax).optional(),
  })
  .refine(
    (body) => !(body.keepDraft ?? []).some((entry) => (body.takeCurrent ?? []).includes(entry)),
    { message: "A path is resolved one way only.", path: ["takeCurrent"] }
  );

/**
 * `PUT /drafts/{id}/overrides` (the proposer): moves the base of the draft's
 * override of `path` to `baseVersionId`, which must be the current version of
 * the nearest ancestor delivering the item. It keeps an override after a
 * parent update, or keeps an item as own after the parent removed it.
 */
export const itemOverrideRequestSchema = z.object({
  path: z.string().trim().min(1).max(DRAFT_LIMITS.pathMax),
  baseVersionId: z.string().min(1),
});

// ── Response Schemas ────────────────────────────────────────────────
/** A version of one pack. */
export const versionResponseSchema = z.object({
  id: z.string(),
  packId: z.string(),
  number: z.number(),
  status: VersionStatusSchema,
  baseVersionId: z.string().nullish(),
  /** The user who proposed it. */
  proposedBy: z.string(),
  source: VersionSourceSchema,
  reason: z.string().nullish(),
  executableChange: z.boolean(),
  manifestSha256: z.string().nullish(),
  submittedAt: z.string().nullish(),
  decidedAt: z.string().nullish(),
  createdAt: z.string(),
  updatedAt: z.string().nullish(),
});

export const versionFileResponseSchema = z.object({
  path: z.string(),
  sha256: z.string().nullish(),
  mode: FileModeSchema,
  size: z.number().nullish(),
  symlinkTarget: z.string().nullish(),
  /** In a mounted pack's version only: the root path this file replaces in the mount folder. */
  overridesPath: z.string().nullish(),
});

export const versionDetailResponseSchema = versionResponseSchema.extend({
  /** The rendered file tree, ordered by path code point by code point, the order of the signed manifest. */
  files: z.array(versionFileResponseSchema),
});

/** One file's content: text when it is valid UTF-8, base64 otherwise; null for a symlink. */
export const versionFileContentResponseSchema = z.object({
  versionId: z.string(),
  path: z.string(),
  mode: FileModeSchema,
  sha256: z.string().nullish(),
  size: z.number().nullish(),
  symlinkTarget: z.string().nullish(),
  encoding: FileEncodingSchema.nullish(),
  content: z.string().nullish(),
  overridesPath: z.string().nullish(),
});

/** How one path differs; `unifiedDiff` is null for binary content or a symlink on either side. */
export const fileDiffResponseSchema = z.object({
  path: z.string(),
  change: FileChangeSchema,
  oldMode: FileModeSchema.nullish(),
  newMode: FileModeSchema.nullish(),
  oldSha256: z.string().nullish(),
  newSha256: z.string().nullish(),
  oldSymlinkTarget: z.string().nullish(),
  newSymlinkTarget: z.string().nullish(),
  binary: z.boolean(),
  unifiedDiff: z.string().nullish(),
});

/** The file a draft save stored; the editor keeps its own text, which is the stored file. */
export const draftFileSaveResponseSchema = z.object({
  path: z.string(),
  kind: EntityKindSchema,
  entityId: z.string(),
  sha256: z.string(),
  sizeBytes: z.number(),
  executable: z.boolean(),
  overridesPath: z.string().nullish(),
});

/** A path both the draft and the current version changed since the draft's base (KAY_CFG_002). */
export const rebaseConflictSchema = z.object({
  path: z.string(),
  /** Null when a CLI proposal leaves the path as the release delivered it. */
  draftChange: FileChangeSchema.nullish(),
  currentChange: FileChangeSchema.nullish(),
});

/** The override record of a draft item after `PUT /drafts/{id}/overrides`. */
export const itemOverrideResponseSchema = z.object({
  versionId: z.string(),
  /** A file path, or a skill directory such as `.agents/skills/kaizen/`. */
  path: z.string(),
  basePackId: z.string(),
  baseVersionId: z.string().nullish(),
  createdAt: z.string(),
  updatedAt: z.string().nullish(),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type DraftCreateRequest = z.infer<typeof draftCreateRequestSchema>;
export type DraftFileSaveRequest = z.input<typeof draftFileSaveRequestSchema>;
export type DraftFileSaveResponse = z.infer<typeof draftFileSaveResponseSchema>;
export type DraftRebaseRequest = z.infer<typeof draftRebaseRequestSchema>;
export type VersionResponse = z.infer<typeof versionResponseSchema>;
export type VersionFileResponse = z.infer<typeof versionFileResponseSchema>;
export type VersionDetailResponse = z.infer<typeof versionDetailResponseSchema>;
export type VersionFileContentResponse = z.infer<typeof versionFileContentResponseSchema>;
export type FileDiffResponse = z.infer<typeof fileDiffResponseSchema>;
export type RebaseConflict = z.infer<typeof rebaseConflictSchema>;
export type ItemOverrideRequest = z.output<typeof itemOverrideRequestSchema>;
export type ItemOverrideResponse = z.infer<typeof itemOverrideResponseSchema>;
