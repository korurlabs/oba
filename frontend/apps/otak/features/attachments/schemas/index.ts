import { z } from "zod";
import { StorageEntityType } from "@oba/shared/features/attachments/schemas";
import { createDocumentCreateFormSchema } from "@/features/documents/schemas";

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

// ── Form Schemas ────────────────────────────────────────────────────

/**
 * Builds the form of a new document made for a mail: the new document form
 * with the template required, since only a template gives the document the
 * PDF the mail carries. The relation is the mail's own and is not shown.
 * `tForm` is the `documents.form` translator, `t` the
 * `outreaches.mailAttachments.fromTemplate` one.
 */
export const createDocumentFromTemplateFormSchema = (
  tForm: (key: string) => string,
  t: (key: string) => string
) =>
  createDocumentCreateFormSchema(tForm).extend({
    templateId: z.string().min(1, t("errors.templateRequired")),
  });

// ── Inferred Types ──────────────────────────────────────────────────

export type DocumentSnapshotRequest = z.infer<typeof documentSnapshotRequestSchema>;
export type DocumentFromTemplateFormValues = z.infer<
  ReturnType<typeof createDocumentFromTemplateFormSchema>
>;
