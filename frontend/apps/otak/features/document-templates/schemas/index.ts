import { z } from "zod";
import type { ApiResponse } from "@oba/shared/schema";
import { StorageEntityType } from "@oba/shared/features/attachments/schemas";
import { instantSchema } from "@oba/shared/lib/date-schemas";
import { relationPaginationSchema } from "@/features/relations/schemas";

// ── Enum Schemas ────────────────────────────────────────────────────

/**
 * The kind of document template: each is an HTML file with the PDF rendered
 * from it. Reusable mails are mail templates.
 */
export const DocumentTypeSchema = z.enum([
  "PROPOSAL",
  "NDA",
  "TERMS",
  "REPORT",
  "SERVICE_SHEET",
  "PRESENTATION",
  "OTHER",
]);
export type DocumentType = z.infer<typeof DocumentTypeSchema>;
export const DocumentType = DocumentTypeSchema.enum;

/** The language of the content; document templates, mail templates and mails share the same languages. */
export const DocumentLanguageSchema = z.enum(["NL", "TR", "EN"]);
export type DocumentLanguage = z.infer<typeof DocumentLanguageSchema>;
export const DocumentLanguage = DocumentLanguageSchema.enum;

// ── Constants ───────────────────────────────────────────────────────

/** The limits the server validates; the form checks the same ones first. */
export const DOCUMENT_TEMPLATE_LIMITS = {
  nameMax: 100,
  searchMax: 100,
  /** The server takes at most 12 MB per request, both files together. */
  filesMaxBytes: 12 * 1024 * 1024,
} as const;

/** Rows per page of the template list. */
export const DOCUMENT_TEMPLATES_PAGE_SIZE = 25;

/** The media types the server accepts for the two file parts. */
export const HTML_MEDIA_TYPE = "text/html";
export const PDF_MEDIA_TYPE = "application/pdf";

// ── Request Schemas ─────────────────────────────────────────────────

/** Create and update alike; an update replaces every field, the type included, and keeps the files. */
export const documentTemplateRequestSchema = z.object({
  type: DocumentTypeSchema,
  name: z.string(),
  language: DocumentLanguageSchema,
});

/** The list's query string; the server searches the name and orders by name, then language. */
export const documentTemplateQueryRequestSchema = z.object({
  search: z.string().optional(),
  type: DocumentTypeSchema.optional(),
  language: DocumentLanguageSchema.optional(),
  page: z.number().int().min(1),
  amount: z.number().int().min(1).max(50),
});

/**
 * The parents a template's PDF can be attached to: a mail or a relation. The
 * server refuses any other entity type.
 */
export const DocumentTemplateAttachmentEntityTypeSchema = z.enum([
  StorageEntityType.MAIL,
  StorageEntityType.RELATION,
]);
export type DocumentTemplateAttachmentEntityType = z.infer<
  typeof DocumentTemplateAttachmentEntityTypeSchema
>;

/** Attaches a copy of the template's PDF to the mail or relation `entityId`. */
export const documentTemplateAttachmentRequestSchema = z.object({
  entityType: DocumentTemplateAttachmentEntityTypeSchema,
  entityId: z.string(),
});

// ── Form Schema ─────────────────────────────────────────────────────

function hasExtension(file: File, extensions: readonly string[]): boolean {
  const name = file.name.toLowerCase();
  return extensions.some((extension) => name.endsWith(extension));
}

/** True for a file the picker or the browser names as HTML. */
export function isHtmlFile(file: File): boolean {
  return file.type === HTML_MEDIA_TYPE || hasExtension(file, [".html", ".htm"]);
}

/** True for a file the picker or the browser names as PDF. */
export function isPdfFile(file: File): boolean {
  return file.type === PDF_MEDIA_TYPE || hasExtension(file, [".pdf"]);
}

/**
 * Builds the template form schema with translated messages. `t` is the
 * `documentTemplates.form` translator. The name is required. The HTML and
 * PDF file go together or not at all, as the server stores them as a pair;
 * without files the template is saved but not ready.
 */
export const createDocumentTemplateFormSchema = (t: (key: string) => string) =>
  z
    .object({
      type: DocumentTypeSchema,
      name: z
        .string()
        .trim()
        .min(1, t("errors.nameRequired"))
        .max(DOCUMENT_TEMPLATE_LIMITS.nameMax, t("errors.nameMax")),
      language: DocumentLanguageSchema,
      htmlFile: z.custom<File>((value) => value instanceof File).nullable(),
      pdfFile: z.custom<File>((value) => value instanceof File).nullable(),
    })
    .superRefine((values, context) => {
      const { htmlFile, pdfFile } = values;
      if (htmlFile && !isHtmlFile(htmlFile)) {
        context.addIssue({ code: "custom", path: ["htmlFile"], message: t("errors.htmlFileType") });
      }
      if (pdfFile && !isPdfFile(pdfFile)) {
        context.addIssue({ code: "custom", path: ["pdfFile"], message: t("errors.pdfFileType") });
      }
      if (htmlFile && !pdfFile) {
        context.addIssue({
          code: "custom",
          path: ["pdfFile"],
          message: t("errors.pdfFileRequired"),
        });
      }
      if (pdfFile && !htmlFile) {
        context.addIssue({
          code: "custom",
          path: ["htmlFile"],
          message: t("errors.htmlFileRequired"),
        });
      }
      if (
        htmlFile &&
        pdfFile &&
        htmlFile.size + pdfFile.size > DOCUMENT_TEMPLATE_LIMITS.filesMaxBytes
      ) {
        context.addIssue({ code: "custom", path: ["pdfFile"], message: t("errors.filesTooLarge") });
      }
    });

// ── Response Schemas ────────────────────────────────────────────────

export const documentTemplateListItemSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: DocumentTypeSchema,
  language: DocumentLanguageSchema,
  /** True once both files are stored. */
  ready: z.boolean(),
  updatedAt: instantSchema,
});

/** `htmlUrl` and `pdfUrl` are short-lived signed read URLs, null before upload. */
export const documentTemplateDetailSchema = documentTemplateListItemSchema.extend({
  htmlUrl: z.string().nullable(),
  pdfUrl: z.string().nullable(),
  createdAt: instantSchema,
});

// ── Inferred Types ──────────────────────────────────────────────────

export type DocumentTemplateRequest = z.infer<typeof documentTemplateRequestSchema>;
export type DocumentTemplateQueryRequest = z.infer<typeof documentTemplateQueryRequestSchema>;
export type DocumentTemplateAttachmentRequest = z.infer<
  typeof documentTemplateAttachmentRequestSchema
>;
export type DocumentTemplateFormValues = z.infer<
  ReturnType<typeof createDocumentTemplateFormSchema>
>;
export type DocumentTemplateListItem = z.infer<typeof documentTemplateListItemSchema>;
export type DocumentTemplateDetail = z.infer<typeof documentTemplateDetailSchema>;

/** The two files of a template, uploaded together. */
export interface DocumentTemplateFiles {
  html: File;
  pdf: File;
}

/** The list envelope, with the API's own paging block. */
export type DocumentTemplateListApiResponse = Omit<
  ApiResponse<DocumentTemplateListItem[]>,
  "pagination"
> & {
  pagination?: z.infer<typeof relationPaginationSchema>;
};

// ── Form Mapping ────────────────────────────────────────────────────

/** The empty form of a new template. */
export function emptyDocumentTemplateFormValues(): DocumentTemplateFormValues {
  return {
    type: DocumentType.PROPOSAL,
    name: "",
    language: DocumentLanguage.NL,
    htmlFile: null,
    pdfFile: null,
  };
}

/** The edit form of a stored template; files are only picked to replace the stored ones. */
export function documentTemplateToFormValues(
  template: DocumentTemplateDetail
): DocumentTemplateFormValues {
  return {
    type: template.type,
    name: template.name,
    language: template.language,
    htmlFile: null,
    pdfFile: null,
  };
}

/** The request the server writes; the files go in a separate upload. */
export function toDocumentTemplateRequest(
  values: DocumentTemplateFormValues
): DocumentTemplateRequest {
  return { type: values.type, name: values.name, language: values.language };
}

/** The files to upload after a save: both when both are picked, else none. */
export function toDocumentTemplateFiles(
  values: DocumentTemplateFormValues
): DocumentTemplateFiles | null {
  if (!values.htmlFile || !values.pdfFile) {
    return null;
  }
  return { html: values.htmlFile, pdf: values.pdfFile };
}
