import { z } from "zod";
import type { ApiResponse } from "@oba/shared/schema";
import { instantSchema } from "@oba/shared/lib/date-schemas";
import {
  DOCUMENT_TEMPLATE_LIMITS,
  DocumentLanguage,
  DocumentLanguageSchema,
  DocumentType,
  DocumentTypeSchema,
  isHtmlFile,
  isPdfFile,
} from "@/features/document-templates/schemas";
import { relationPaginationSchema } from "@/features/relations/schemas";

// ── Enum Schemas ────────────────────────────────────────────────────

/** The kind of owner of a document; a relation is the only one so far. */
export const DocumentEntityTypeSchema = z.enum(["RELATION"]);
export type DocumentEntityType = z.infer<typeof DocumentEntityTypeSchema>;
export const DocumentEntityType = DocumentEntityTypeSchema.enum;

// ── Constants ───────────────────────────────────────────────────────

/** The limits the server validates; the forms check the same ones first. */
export const DOCUMENT_LIMITS = {
  titleMax: 200,
  searchMax: 100,
  /** The server takes at most 12 MB per request, both files together. */
  filesMaxBytes: DOCUMENT_TEMPLATE_LIMITS.filesMaxBytes,
} as const;

/** Rows per page of the document list. */
export const DOCUMENTS_PAGE_SIZE = 25;

/** The most rows a searchable picker shows at once; its search narrows a longer list. */
export const PICKER_SIZE = 20;

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * The list's query string. The server searches the title and orders by the
 * last change, newest first. Every filter is optional.
 */
export const documentQueryRequestSchema = z.object({
  relationId: z.string().optional(),
  legalEntityId: z.string().optional(),
  language: DocumentLanguageSchema.optional(),
  type: DocumentTypeSchema.optional(),
  search: z.string().optional(),
  page: z.number().int().min(1),
  amount: z.number().int().min(1).max(50),
});

/**
 * A new document of a relation. With a template that has its files, the
 * server copies them to the document; otherwise the document has no files
 * until they are uploaded.
 */
export const documentCreateRequestSchema = z.object({
  entityType: DocumentEntityTypeSchema,
  entityId: z.string(),
  legalEntityId: z.string(),
  title: z.string(),
  type: DocumentTypeSchema,
  language: DocumentLanguageSchema,
  templateId: z.string().nullable(),
});

/** Replaces every field; the owner and the files stay. */
export const documentUpdateRequestSchema = z.object({
  title: z.string(),
  type: DocumentTypeSchema,
  language: DocumentLanguageSchema,
  legalEntityId: z.string(),
});

// ── Form Schemas ────────────────────────────────────────────────────

/** The title rule both document forms share. */
function titleSchema(t: (key: string) => string) {
  return z
    .string()
    .trim()
    .min(1, t("errors.titleRequired"))
    .max(DOCUMENT_LIMITS.titleMax, t("errors.titleMax"));
}

/**
 * Builds the new document form schema with translated messages. `t` is the
 * `documents.form` translator. The relation, the legal entity and the title
 * are required; the template is optional.
 */
export const createDocumentCreateFormSchema = (t: (key: string) => string) =>
  z.object({
    relationId: z.string().min(1, t("errors.relationRequired")),
    legalEntityId: z.string().min(1, t("errors.legalEntityRequired")),
    title: titleSchema(t),
    type: DocumentTypeSchema,
    language: DocumentLanguageSchema,
    /** Empty for a document without a template. */
    templateId: z.string(),
  });

/** Builds the edit form schema of a stored document; `t` is the `documents.form` translator. */
export const createDocumentEditFormSchema = (t: (key: string) => string) =>
  z.object({
    legalEntityId: z.string().min(1, t("errors.legalEntityRequired")),
    title: titleSchema(t),
    type: DocumentTypeSchema,
    language: DocumentLanguageSchema,
  });

/**
 * Builds the file replacement form schema: the HTML file and the PDF made
 * from it, both required, as the server stores them as a pair. `t` is the
 * `documents.form` translator.
 */
export const createDocumentFilesFormSchema = (t: (key: string) => string) =>
  z
    .object({
      htmlFile: z.custom<File>((value) => value instanceof File).nullable(),
      pdfFile: z.custom<File>((value) => value instanceof File).nullable(),
    })
    .superRefine(({ htmlFile, pdfFile }, context) => {
      if (!htmlFile) {
        context.addIssue({
          code: "custom",
          path: ["htmlFile"],
          message: t("errors.htmlFileRequired"),
        });
      } else if (!isHtmlFile(htmlFile)) {
        context.addIssue({ code: "custom", path: ["htmlFile"], message: t("errors.htmlFileType") });
      }
      if (!pdfFile) {
        context.addIssue({
          code: "custom",
          path: ["pdfFile"],
          message: t("errors.pdfFileRequired"),
        });
      } else if (!isPdfFile(pdfFile)) {
        context.addIssue({ code: "custom", path: ["pdfFile"], message: t("errors.pdfFileType") });
      }
      if (htmlFile && pdfFile && htmlFile.size + pdfFile.size > DOCUMENT_LIMITS.filesMaxBytes) {
        context.addIssue({ code: "custom", path: ["pdfFile"], message: t("errors.filesTooLarge") });
      }
    });

// ── Response Schemas ────────────────────────────────────────────────

/** The user who changed a document last; `displayName` is null once the user is gone. */
export const documentUserSchema = z.object({
  id: z.string(),
  displayName: z.string().nullable(),
});

export const documentListItemSchema = z.object({
  id: z.string(),
  title: z.string(),
  type: DocumentTypeSchema,
  language: DocumentLanguageSchema,
  entityType: DocumentEntityTypeSchema,
  entityId: z.string(),
  relationId: z.string().nullable(),
  relationName: z.string().nullable(),
  legalEntityId: z.string(),
  legalEntityShortName: z.string(),
  /** The template the document was made from; null for a local file or a deleted template. */
  templateId: z.string().nullable(),
  /** True once both files are stored. */
  ready: z.boolean(),
  /** How many PDF snapshots of the document were attached to mails. */
  snapshotCount: z.number(),
  updatedBy: documentUserSchema.nullable(),
  updatedAt: instantSchema,
  createdAt: instantSchema,
});

/** A PDF copy of the document attached to a mail; it stays as it was when the document changes. */
export const documentSnapshotSchema = z.object({
  attachmentId: z.string(),
  mailId: z.string(),
  /** Null for a mail no outreach names. */
  outreachTitle: z.string().nullable(),
  createdAt: instantSchema,
});

/** `htmlUrl` and `pdfUrl` are short-lived signed read URLs, null before upload. */
export const documentDetailSchema = documentListItemSchema.extend({
  htmlUrl: z.string().nullable(),
  pdfUrl: z.string().nullable(),
  /** Newest first. */
  snapshots: z.array(documentSnapshotSchema),
});

/** A legal company of the organization that issues documents; the pickers show its short name. */
export const legalEntityListItemSchema = z.object({
  id: z.string(),
  shortName: z.string(),
  legalName: z.string(),
  country: z.string(),
  registrationNumber: z.string().nullable(),
  updatedAt: instantSchema,
});

// ── Inferred Types ──────────────────────────────────────────────────

export type DocumentQueryRequest = z.infer<typeof documentQueryRequestSchema>;
export type DocumentCreateRequest = z.infer<typeof documentCreateRequestSchema>;
export type DocumentUpdateRequest = z.infer<typeof documentUpdateRequestSchema>;
export type DocumentCreateFormValues = z.infer<ReturnType<typeof createDocumentCreateFormSchema>>;
export type DocumentEditFormValues = z.infer<ReturnType<typeof createDocumentEditFormSchema>>;
export type DocumentFilesFormValues = z.infer<ReturnType<typeof createDocumentFilesFormSchema>>;
export type DocumentUser = z.infer<typeof documentUserSchema>;
export type DocumentListItem = z.infer<typeof documentListItemSchema>;
export type DocumentSnapshot = z.infer<typeof documentSnapshotSchema>;
export type DocumentDetail = z.infer<typeof documentDetailSchema>;
export type LegalEntityListItem = z.infer<typeof legalEntityListItemSchema>;

/** The list envelope, with the API's own paging block. */
export type DocumentListApiResponse = Omit<ApiResponse<DocumentListItem[]>, "pagination"> & {
  pagination?: z.infer<typeof relationPaginationSchema>;
};

// ── Form Mapping ────────────────────────────────────────────────────

/**
 * The empty form of a new document. `relationId` preselects the relation,
 * as when the list is narrowed to one; a single legal entity is preselected,
 * as there is nothing to choose.
 */
export function emptyDocumentCreateFormValues(
  relationId: string | null,
  legalEntityId: string | null
): DocumentCreateFormValues {
  return {
    relationId: relationId ?? "",
    legalEntityId: legalEntityId ?? "",
    title: "",
    type: DocumentType.PROPOSAL,
    language: DocumentLanguage.NL,
    templateId: "",
  };
}

/** The request a new document sends; its owner is the picked relation. */
export function toDocumentCreateRequest(values: DocumentCreateFormValues): DocumentCreateRequest {
  return {
    entityType: DocumentEntityType.RELATION,
    entityId: values.relationId,
    legalEntityId: values.legalEntityId,
    title: values.title,
    type: values.type,
    language: values.language,
    templateId: values.templateId === "" ? null : values.templateId,
  };
}

/** The edit form of a stored document. */
export function documentToEditFormValues(document: DocumentDetail): DocumentEditFormValues {
  return {
    legalEntityId: document.legalEntityId,
    title: document.title,
    type: document.type,
    language: document.language,
  };
}

/** The request an edit sends. */
export function toDocumentUpdateRequest(values: DocumentEditFormValues): DocumentUpdateRequest {
  return {
    title: values.title,
    type: values.type,
    language: values.language,
    legalEntityId: values.legalEntityId,
  };
}
