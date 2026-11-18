import { z } from "zod";

// ── Enum Schemas ───────────────────────────────────────────────────

export const StorageEntityTypeSchema = z.enum([
  "ORGANIZATION",
  "USER",
  "INVOICE",
  "TICKET",
  "CHAT_MESSAGE",
  "KALKAN_PAGE",
  "MAIL",
  "RELATION",
]);
export type StorageEntityType = z.infer<typeof StorageEntityTypeSchema>;
export const StorageEntityType = StorageEntityTypeSchema.enum;

// ── Request Schemas ─────────────────────────────────────────────────

export const attachmentCreateMetadataSchema = z.object({
  fileName: z.string().min(1),
  entityType: StorageEntityTypeSchema,
  entityId: z.guid(),
});

// ── Response Schemas ────────────────────────────────────────────────

/**
 * Ids are checked with `z.guid()`, not `z.uuid()`: the backend accepts any
 * 8-4-4-4-12 hex id, and seeded rows such as `b0000000-0000-0000-0000-000000000001`
 * carry no RFC 9562 version, which `z.uuid()` rejects.
 */
export const attachmentResponseSchema = z.object({
  id: z.guid(),
  url: z.string(),
  fileName: z.string(),
  contentType: z.string().nullable().optional(),
  fileSize: z.number().nullable().optional(),
  entityType: StorageEntityTypeSchema,
  entityId: z.guid(),
  createdAt: z.string(),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type AttachmentCreateMetadata = z.infer<
  typeof attachmentCreateMetadataSchema
>;
export type AttachmentResponse = z.infer<typeof attachmentResponseSchema>;
