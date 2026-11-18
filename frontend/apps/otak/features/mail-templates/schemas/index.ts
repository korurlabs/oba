import { z } from "zod";
import type { ApiResponse } from "@oba/shared/schema";
import { instantSchema } from "@oba/shared/lib/date-schemas";
import { DocumentLanguage, DocumentLanguageSchema } from "@/features/document-templates/schemas";
import { relationPaginationSchema } from "@/features/relations/schemas";

// A mail template is a reusable mail of the organization: a subject and a
// plain-text body in one language. It has no files, so it is usable as soon
// as it is saved. It shares the languages of the document templates.

// ── Constants ───────────────────────────────────────────────────────

/** The limits the server validates; the form checks the same ones first. */
export const MAIL_TEMPLATE_LIMITS = {
  nameMax: 100,
  subjectMax: 255,
  bodyMax: 10000,
  searchMax: 100,
} as const;

/** Rows per page of the mail template list. */
export const MAIL_TEMPLATES_PAGE_SIZE = 25;

// ── Request Schemas ─────────────────────────────────────────────────

/** Create and update alike; an update replaces every field. */
export const mailTemplateRequestSchema = z.object({
  name: z.string(),
  language: DocumentLanguageSchema,
  subject: z.string(),
  body: z.string(),
});

/** The list's query string; the server searches the name and orders by name, then language. */
export const mailTemplateQueryRequestSchema = z.object({
  search: z.string().optional(),
  language: DocumentLanguageSchema.optional(),
  page: z.number().int().min(1),
  amount: z.number().int().min(1).max(50),
});

// ── Form Schema ─────────────────────────────────────────────────────

/**
 * Builds the mail template form schema with translated messages. `t` is the
 * `mailTemplates.form` translator. Name, subject and body are required
 * within the server's limits.
 */
export const createMailTemplateFormSchema = (t: (key: string) => string) =>
  z.object({
    name: z
      .string()
      .trim()
      .min(1, t("errors.nameRequired"))
      .max(MAIL_TEMPLATE_LIMITS.nameMax, t("errors.nameMax")),
    language: DocumentLanguageSchema,
    subject: z
      .string()
      .refine((value) => value.trim().length > 0, t("errors.subjectRequired"))
      .refine((value) => value.length <= MAIL_TEMPLATE_LIMITS.subjectMax, t("errors.subjectMax")),
    body: z
      .string()
      .refine((value) => value.trim().length > 0, t("errors.bodyRequired"))
      .refine((value) => value.length <= MAIL_TEMPLATE_LIMITS.bodyMax, t("errors.bodyMax")),
  });

// ── Response Schemas ────────────────────────────────────────────────

export const mailTemplateListItemSchema = z.object({
  id: z.string(),
  name: z.string(),
  language: DocumentLanguageSchema,
  subject: z.string(),
  updatedAt: instantSchema,
});

export const mailTemplateDetailSchema = mailTemplateListItemSchema.extend({
  body: z.string(),
  createdAt: instantSchema,
});

// ── Inferred Types ──────────────────────────────────────────────────

export type MailTemplateRequest = z.infer<typeof mailTemplateRequestSchema>;
export type MailTemplateQueryRequest = z.infer<typeof mailTemplateQueryRequestSchema>;
export type MailTemplateFormValues = z.infer<ReturnType<typeof createMailTemplateFormSchema>>;
export type MailTemplateListItem = z.infer<typeof mailTemplateListItemSchema>;
export type MailTemplateDetail = z.infer<typeof mailTemplateDetailSchema>;

/** The list envelope, with the API's own paging block. */
export type MailTemplateListApiResponse = Omit<
  ApiResponse<MailTemplateListItem[]>,
  "pagination"
> & {
  pagination?: z.infer<typeof relationPaginationSchema>;
};

// ── Form Mapping ────────────────────────────────────────────────────

/** The empty form of a new mail template. */
export function emptyMailTemplateFormValues(): MailTemplateFormValues {
  return { name: "", language: DocumentLanguage.NL, subject: "", body: "" };
}

/** The edit form of a stored mail template. */
export function mailTemplateToFormValues(template: MailTemplateDetail): MailTemplateFormValues {
  return {
    name: template.name,
    language: template.language,
    subject: template.subject,
    body: template.body,
  };
}

/**
 * The request the server writes. The subject and body are sent as typed,
 * because a mail goes out as written; the server trims only the name.
 */
export function toMailTemplateRequest(values: MailTemplateFormValues): MailTemplateRequest {
  return {
    name: values.name,
    language: values.language,
    subject: values.subject,
    body: values.body,
  };
}
