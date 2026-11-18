import { z } from "zod";

import { releaseResponseSchema } from "@/features/releases/schemas";
import { EntityKindSchema } from "@/features/versions/schemas";

// ── Enum Schemas ───────────────────────────────────────────────────
/** Why an inherited item cannot be overridden: its pack is required, or it is a guardrail. */
export const ItemLockSchema = z.enum(["REQUIRED", "GUARDRAIL"]);
export type ItemLock = z.infer<typeof ItemLockSchema>;
export const ItemLock = ItemLockSchema.enum;

/**
 * How an override stands against its parent: the parent's item is still the
 * one it was based on, the parent changed it, or no ancestor delivers it any
 * more.
 */
export const OverrideStatusSchema = z.enum(["CURRENT", "PARENT_UPDATED", "PARENT_REMOVED"]);
export type OverrideStatus = z.infer<typeof OverrideStatusSchema>;
export const OverrideStatus = OverrideStatusSchema.enum;

// ── Limits ─────────────────────────────────────────────────────────
/** First folders that hold the delivered config itself, so no pack may be mounted there. */
export const MOUNT_RESERVED_ROOTS: ReadonlySet<string> = new Set([".agents", ".claude", "docs"]);

/** The limits of a pack and of a repository's pack list, as the server checks them. */
export const PACK_LIMITS = {
  nameMax: 60,
  descriptionMax: 1000,
  rootMax: 200,
  /** Packs one repository composes. */
  repositoryPacksMax: 50,
} as const;

// ── Request Schemas ─────────────────────────────────────────────────
/**
 * A pack's plain-text description. The server refuses an empty string, so a
 * blank one becomes null, which means the pack has none.
 */
export const packDescriptionSchema = z
  .string()
  .trim()
  .max(
    PACK_LIMITS.descriptionMax,
    `A description has at most ${PACK_LIMITS.descriptionMax} characters.`
  )
  .transform((description: string) => (description === "" ? null : description));

/**
 * `POST /packs` (owners only): a name unique in the organization, and for a
 * child pack its parent, which cannot be changed later.
 */
export const packCreateRequestSchema = z.object({
  name: z
    .string()
    .trim()
    .regex(
      /^[a-z0-9-]{1,60}$/,
      `A pack name is 1 to ${PACK_LIMITS.nameMax} lowercase letters, digits or hyphens.`
    ),
  description: packDescriptionSchema.nullish(),
  parentPackId: z.string().min(1).optional(),
});

/**
 * `PATCH /packs/{id}` (owners only): the pack's name and description, a full
 * update, so a null description removes it. The server refuses a new name
 * once the pack is used, by a repository or by an approved version, since the
 * name keys what was delivered; the description may always change.
 */
export const packUpdateRequestSchema = packCreateRequestSchema.pick({ name: true }).extend({
  description: packDescriptionSchema.nullable(),
});

/**
 * A pack's mount folder in a repository. The server also refuses a folder
 * inside or around another mount's folder, which only the whole list shows.
 */
export const mountFolderSchema = z
  .string()
  .trim()
  .min(1, "Enter the folder.")
  .max(PACK_LIMITS.rootMax, `A folder has at most ${PACK_LIMITS.rootMax} characters.`)
  .regex(
    /^[A-Za-z0-9_][A-Za-z0-9._-]*(\/[A-Za-z0-9_][A-Za-z0-9._-]*){0,3}$/,
    "The folder must be 1 to 4 repository-relative segments of letters, digits, '.', '_' or '-', none starting with '.' or '-'."
  )
  .refine(
    (root: string) => !MOUNT_RESERVED_ROOTS.has(root.split("/")[0] ?? ""),
    "The folder cannot start with .agents, .claude or docs, the folders of the delivered config."
  );

/** One pack of a repository's list: at the root (`rootPath` null) or mounted in a folder. */
export const repositoryPackItemRequestSchema = z.object({
  packId: z.string().min(1),
  rootPath: mountFolderSchema.nullable().optional(),
});

/** `PUT /repositories/{id}/packs` (owners only): the whole list, in display order. */
export const repositoryPackUpdateRequestSchema = z.object({
  packs: z
    .array(repositoryPackItemRequestSchema)
    .max(
      PACK_LIMITS.repositoryPacksMax,
      `A repository uses at most ${PACK_LIMITS.repositoryPacksMax} packs.`
    ),
});

// ── Response Schemas ────────────────────────────────────────────────
/** `GET /packs`: a pack of the organization, a node of its pack tree. */
export const packResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  /** What the pack is for, in plain text; null when it has none. */
  description: z.string().nullish(),
  /** Null for a root pack. Fixed at creation. */
  parentPackId: z.string().nullish(),
  /** Null until a first version is approved or seeded. */
  currentVersionId: z.string().nullish(),
  /** Composed into every repository of the organization; its items are locked for descendants. */
  required: z.boolean(),
  /** Every folder the pack is mounted at, in any repository, sorted; empty when it is only used at roots. */
  mountPaths: z.array(z.string()),
  createdAt: z.string(),
  updatedAt: z.string().nullish(),
});

/** A pack of a repository's list, with where the repository uses it. */
export const repositoryPackEntryResponseSchema = packResponseSchema
  .omit({ mountPaths: true })
  .extend({
    /** The mount folder; null for a pack composed at the repository root. */
    rootPath: z.string().nullish(),
  });

/** `GET`/`PUT /repositories/{id}/packs`: the required packs first, then the repository's own by position, and its newest release. */
export const repositoryPackResponseSchema = z.object({
  repositoryId: z.string(),
  packs: z.array(repositoryPackEntryResponseSchema),
  release: releaseResponseSchema.nullish(),
});

/** A repository that composes a pack, with where it mounts it and its newest release. */
export const packRepositoryResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  remoteUrl: z.string(),
  status: z.string(),
  /** The mount folder; null for a pack composed at the repository root. */
  rootPath: z.string().nullish(),
  /** Null while the repository has no release. */
  latestReleaseNumber: z.number().nullish(),
});

/**
 * `GET /packs/{id}`: a pack with the repositories that name it in their pack
 * list, by name; for a required pack every active repository.
 */
export const packDetailResponseSchema = packResponseSchema.extend({
  repositories: z.array(packRepositoryResponseSchema),
});

/** The base and the parent of an own item that an ancestor also delivers. */
export const effectiveOverrideResponseSchema = z.object({
  basePackId: z.string(),
  /** Null when the base is unknown, which counts as a parent update. */
  baseVersionId: z.string().nullish(),
  baseItemVersion: z.number().nullish(),
  /** The current version of the nearest ancestor delivering the item; null once none does. */
  parentVersionId: z.string().nullish(),
  parentItemVersion: z.number().nullish(),
  status: OverrideStatusSchema,
});

/** `GET /packs/{id}/effective`: one item of a pack, own or inherited, with its source. */
export const effectiveItemResponseSchema = z.object({
  /** A file path, or a skill directory such as `.agents/skills/kaizen/`. */
  path: z.string(),
  kind: EntityKindSchema,
  sourcePackId: z.string(),
  sourceVersionId: z.string().nullish(),
  own: z.boolean(),
  locked: ItemLockSchema.nullish(),
  override: effectiveOverrideResponseSchema.nullish(),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type PackCreateRequest = z.output<typeof packCreateRequestSchema>;
export type PackUpdateRequest = z.output<typeof packUpdateRequestSchema>;
export type RepositoryPackItemRequest = z.output<typeof repositoryPackItemRequestSchema>;
export type RepositoryPackUpdateRequest = z.output<typeof repositoryPackUpdateRequestSchema>;
export type PackResponse = z.infer<typeof packResponseSchema>;
export type PackRepositoryResponse = z.infer<typeof packRepositoryResponseSchema>;
export type PackDetailResponse = z.infer<typeof packDetailResponseSchema>;
export type RepositoryPackEntryResponse = z.infer<typeof repositoryPackEntryResponseSchema>;
export type RepositoryPackResponse = z.infer<typeof repositoryPackResponseSchema>;
export type EffectiveOverrideResponse = z.infer<typeof effectiveOverrideResponseSchema>;
export type EffectiveItemResponse = z.infer<typeof effectiveItemResponseSchema>;

/** `GET /packs/{id}/effective` parameters: an own draft or version (default the current one), and whether inherited items are listed. */
export type EffectiveQuery = { versionId?: string; inherited: boolean };

// ── UI Types ────────────────────────────────────────────────────────
/** A pack as the pages show it: the owner of every version, a node of the pack tree. */
export type Owner = {
  id: string;
  name: string;
  parentPackId: string | null;
  currentVersionId: string | null;
  required: boolean;
  /** The folders it is mounted at, in any repository; empty when it is used at roots only. */
  mountPaths: string[];
  /** The repositories whose pack list names it, at the root or mounted; required packs are named by every one. */
  repositoryIds: string[];
};
