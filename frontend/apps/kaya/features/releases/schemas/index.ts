import { z } from "zod";

import { FileModeSchema } from "@/features/versions/schemas";

// ── Request Schemas ─────────────────────────────────────────────────
/** `GET /repositories/{repositoryId}/releases`: 1-based page, sorted newest first by the server. */
export const releaseQueryRequestSchema = z.object({
  page: z.number().int().min(1).optional(),
  amount: z.number().int().min(1).max(50).optional(),
});

// ── Response Schemas ────────────────────────────────────────────────
export const releaseResponseSchema = z.object({
  id: z.string(),
  repositoryId: z.string(),
  number: z.number(),
  /** The version whose approval, seed or pack order change made the release. */
  causeVersionId: z.string(),
  manifestSha256: z.string(),
  keyId: z.string(),
  fileCount: z.number(),
  createdAt: z.string(),
});

/** A delivered path of a release; `versionId` is the version of `ownerId` that holds its content. */
export const releaseEntrySchema = z.object({
  path: z.string(),
  sha256: z.string().nullish(),
  mode: FileModeSchema,
  target: z.string().nullish(),
  size: z.number().nullish(),
  alwaysOn: z.boolean(),
  /** The pack that gives the path. */
  ownerId: z.string(),
  versionId: z.string(),
  /** A mounted pack's override marker, kept from before the pack tree; null for every other file. */
  overridesPath: z.string().nullish(),
  /** The mounted pack whose override narrowed this root rule; its bytes are then the narrowed copy. */
  narrowedBy: z.string().nullish(),
});

/** The version of one pack a release composed, in composition order: root packs by depth, then mounts by folder. */
export const packVersionEntrySchema = z.object({
  packId: z.string(),
  /** Null for a pack without a version yet. */
  versionId: z.string().nullish(),
  /** The mount folder; null for a pack at the repository root. */
  rootPath: z.string().nullish(),
});

export const releaseDetailResponseSchema = releaseResponseSchema.extend({
  signature: z.string(),
  files: z.array(releaseEntrySchema),
  /** Not part of the signed manifest. */
  packVersions: z.array(packVersionEntrySchema),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type ReleaseQueryRequest = z.infer<typeof releaseQueryRequestSchema>;
export type ReleaseResponse = z.infer<typeof releaseResponseSchema>;
export type ReleaseEntry = z.infer<typeof releaseEntrySchema>;
export type PackVersionEntry = z.infer<typeof packVersionEntrySchema>;
export type ReleaseDetailResponse = z.infer<typeof releaseDetailResponseSchema>;
