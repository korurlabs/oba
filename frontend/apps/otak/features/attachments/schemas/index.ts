import { z } from "zod";
import { StorageEntityType } from "@oba/shared/features/attachments/schemas";

// ── Enum Schemas ────────────────────────────────────────────────────

/** The parents a document snapshot can be attached to; the server takes a mail only. */
export const DocumentSnapshotEntityTypeSchema = z.enum([StorageEntityType.MAIL]);
export type DocumentSnapshotEntityType = z.infer<typeof DocumentSnapshotEntityTypeSchema>;
export const DocumentSnapshotEntityType = DocumentSnapshotEntityTypeSchema.enum;

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * Attaches a copy of the document's PDF to the mail `entityId`. The copy
 * keeps the document's id, and a later change to the document leaves it as
 * it is.
 */
export const documentSnapshotRequestSchema = z.object({
  entityType: DocumentSnapshotEntityTypeSchema,
  entityId: z.string(),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type DocumentSnapshotRequest = z.infer<typeof documentSnapshotRequestSchema>;
