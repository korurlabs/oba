import { z } from "zod";
import type { ApiResponse } from "@oba/shared/schema";
import { instantSchema } from "@oba/shared/lib/date-schemas";
import { DocumentLanguage, DocumentLanguageSchema } from "@/features/document-templates/schemas";
import { relationPaginationSchema } from "@/features/relations/schemas";

// An outreach is one reach-out to one contact of a relation, on one channel.
// A MAIL outreach owns its mail (a copy of a mail template or written by
// hand) with its own attachments, and is sent from Oba. A WHATSAPP or CALL outreach has no mail: it happens outside Oba and
// is recorded as done.

// ── Enum Schemas ────────────────────────────────────────────────────

/** The channel an outreach reaches a contact on; it can not change once saved. */
export const OutreachTypeSchema = z.enum(["MAIL", "WHATSAPP", "CALL"]);
export type OutreachType = z.infer<typeof OutreachTypeSchema>;
export const OutreachType = OutreachTypeSchema.enum;

/**
 * Where an outreach stands. A DRAFT or FAILED one can change and be sent or
 * marked as done; a SENT one is final, and so are the attachments of its
 * mail.
 */
export const OutreachStatusSchema = z.enum(["DRAFT", "SENT", "FAILED"]);
export type OutreachStatus = z.infer<typeof OutreachStatusSchema>;
export const OutreachStatus = OutreachStatusSchema.enum;

// ── Constants ───────────────────────────────────────────────────────

/** The limits the server validates; the forms check the same ones first. */
export const OUTREACH_LIMITS = {
  titleMax: 200,
  subjectMax: 255,
  bodyMax: 10000,
  /** The server refuses attachments that together are larger than this when sending. */
  attachmentsMaxBytes: 3 * 1024 * 1024,
} as const;

/** Rows per page of an outreach list. */
export const OUTREACHES_PAGE_SIZE = 25;

/** The language of a mail written by hand when the user picks none. */
export const DEFAULT_MAIL_LANGUAGE: DocumentLanguage = DocumentLanguage.NL;

// ── Request Schemas ─────────────────────────────────────────────────

/** A mail written or edited by hand; the server refuses one without a language. */
export const outreachMailRequestSchema = z.object({
  subject: z.string(),
  body: z.string(),
  language: DocumentLanguageSchema,
});

/**
 * Create. A MAIL outreach gives exactly one of `mailTemplateId` (the server
 * copies the mail template into the outreach's own mail) and `mail`, and
 * may give `documentTemplateIds`, whose PDFs the server attaches to the new
 * mail; a WHATSAPP or CALL outreach gives neither and no template ids.
 */
export const outreachRequestSchema = z.object({
  relationId: z.string(),
  contactId: z.string(),
  title: z.string(),
  type: OutreachTypeSchema,
  mailTemplateId: z.string().nullable(),
  mail: outreachMailRequestSchema.nullable(),
  documentTemplateIds: z.array(z.string()),
});

/**
 * Replaces the contact, the title and, for a MAIL outreach, its own mail.
 * The mail is required for MAIL and null for WHATSAPP and CALL.
 */
export const outreachUpdateRequestSchema = z.object({
  contactId: z.string(),
  title: z.string(),
  mail: outreachMailRequestSchema.nullable(),
});

/** Sends the outreach from one of the organization's mail senders. */
export const outreachSendRequestSchema = z.object({
  mailSenderId: z.string(),
});

/** The list's query string; the server orders newest first. */
export const outreachQueryRequestSchema = z.object({
  relationId: z.string().optional(),
  status: OutreachStatusSchema.optional(),
  page: z.number().int().min(1),
  amount: z.number().int().min(1).max(50),
});

/** One page of a relation's outreaches, as the relation page's card shows them. */
export function relationOutreachesPageQuery(
  relationId: string,
  page: number
): OutreachQueryRequest {
  return { relationId, page, amount: OUTREACHES_PAGE_SIZE };
}

/** The first page, which the relation page prefetches and the card shows first. */
export function firstRelationOutreachesPage(relationId: string): OutreachQueryRequest {
  return relationOutreachesPageQuery(relationId, 1);
}

// ── Form Schema ─────────────────────────────────────────────────────

/** The mail fields of the reach-out dialog and the edit form; `t` is the `outreaches.form` translator. */
export const createOutreachMailFormSchema = (t: (key: string) => string) =>
  z.object({
    subject: z
      .string()
      .refine((value) => value.trim().length > 0, t("errors.subjectRequired"))
      .refine((value) => value.length <= OUTREACH_LIMITS.subjectMax, t("errors.subjectMax")),
    body: z
      .string()
      .refine((value) => value.trim().length > 0, t("errors.bodyRequired"))
      .refine((value) => value.length <= OUTREACH_LIMITS.bodyMax, t("errors.bodyMax")),
    language: DocumentLanguageSchema,
  });

/** The title the reach-out dialog asks for last and the edit form shows first. */
export const createOutreachTitleSchema = (t: (key: string) => string) =>
  z
    .string()
    .trim()
    .min(1, t("errors.titleRequired"))
    .max(OUTREACH_LIMITS.titleMax, t("errors.titleMax"));

/**
 * The edit form of a DRAFT or FAILED outreach. A MAIL outreach checks its
 * mail fields; a WHATSAPP or CALL outreach has none to check, so they stay
 * unchecked and are not sent.
 */
export const createOutreachFormSchema = (t: (key: string) => string, type: OutreachType) => {
  const ownFields = {
    title: createOutreachTitleSchema(t),
    contactId: z.string().min(1, t("errors.contactRequired")),
  };
  if (type === OutreachType.MAIL) {
    return createOutreachMailFormSchema(t).extend(ownFields);
  }
  return z.object({
    subject: z.string(),
    body: z.string(),
    language: DocumentLanguageSchema,
    ...ownFields,
  });
};

// ── Response Schemas ────────────────────────────────────────────────

/** The contact an outreach goes to; null once the contact was deleted. */
export const outreachContactSchema = z.object({
  id: z.string(),
  name: z.string(),
});

export const outreachListItemSchema = z.object({
  id: z.string(),
  title: z.string(),
  type: OutreachTypeSchema,
  status: OutreachStatusSchema,
  relationId: z.string(),
  contact: outreachContactSchema.nullable(),
  /** The mail address (MAIL) or phone number (WHATSAPP, CALL) it went to; null until sent or done. */
  sentTo: z.string().nullable(),
  sentAt: instantSchema.nullable(),
  /** The sender's address; null until sent or once the sender was deleted. */
  mailSenderAddress: z.string().nullable(),
  updatedAt: instantSchema,
  createdAt: instantSchema,
});

/**
 * The outreach's own mail; `mailTemplateId` names the mail template it was
 * copied from, if any. Its attachments are listed through the attachments
 * API under the mail's `id`.
 */
export const outreachMailSchema = z.object({
  id: z.string(),
  mailTemplateId: z.string().nullable(),
  subject: z.string(),
  body: z.string(),
  language: DocumentLanguageSchema,
});

/** The list item plus the mail. A WHATSAPP or CALL outreach has no mail. */
export const outreachDetailSchema = outreachListItemSchema.extend({
  mail: outreachMailSchema.nullable(),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type OutreachMailRequest = z.infer<typeof outreachMailRequestSchema>;
export type OutreachRequest = z.infer<typeof outreachRequestSchema>;
export type OutreachUpdateRequest = z.infer<typeof outreachUpdateRequestSchema>;
export type OutreachSendRequest = z.infer<typeof outreachSendRequestSchema>;
export type OutreachQueryRequest = z.infer<typeof outreachQueryRequestSchema>;
export type OutreachMailFormValues = z.infer<ReturnType<typeof createOutreachMailFormSchema>>;
export type OutreachFormValues = z.infer<ReturnType<typeof createOutreachFormSchema>>;
export type OutreachContact = z.infer<typeof outreachContactSchema>;
export type OutreachListItem = z.infer<typeof outreachListItemSchema>;
export type OutreachMail = z.infer<typeof outreachMailSchema>;
export type OutreachDetail = z.infer<typeof outreachDetailSchema>;

/** The list envelope, with the API's own paging block. */
export type OutreachListApiResponse = Omit<ApiResponse<OutreachListItem[]>, "pagination"> & {
  pagination?: z.infer<typeof relationPaginationSchema>;
};

// ── Helpers ─────────────────────────────────────────────────────────

/** A SENT outreach is final: no edit, no new attachments, no second send or done, no delete. */
export function isOutreachEditable(outreach: Pick<OutreachListItem, "status">): boolean {
  return outreach.status !== OutreachStatus.SENT;
}

/** Only a MAIL outreach has a mail and attachments, and is sent from Oba. */
export function isMailOutreach(outreach: Pick<OutreachListItem, "type">): boolean {
  return outreach.type === OutreachType.MAIL;
}

/** A DRAFT or FAILED MAIL outreach can be sent. */
export function canSendOutreach(outreach: Pick<OutreachListItem, "type" | "status">): boolean {
  return isMailOutreach(outreach) && isOutreachEditable(outreach);
}

/** A WHATSAPP or CALL outreach that is not done yet can be marked as done. */
export function canCompleteOutreach(outreach: Pick<OutreachListItem, "type" | "status">): boolean {
  return !isMailOutreach(outreach) && isOutreachEditable(outreach);
}

/** The edit form of a stored outreach; one without a mail starts with empty mail fields. */
export function outreachToFormValues(outreach: OutreachDetail): OutreachFormValues {
  return {
    title: outreach.title,
    contactId: outreach.contact?.id ?? "",
    subject: outreach.mail?.subject ?? "",
    body: outreach.mail?.body ?? "",
    language: outreach.mail?.language ?? DEFAULT_MAIL_LANGUAGE,
  };
}

/**
 * The update request. A MAIL outreach sends its subject and body as typed,
 * because the server stores them as sent; it trims only the title. A
 * WHATSAPP or CALL outreach sends no mail, which the server requires.
 */
export function toOutreachUpdateRequest(
  values: OutreachFormValues,
  type: OutreachType
): OutreachUpdateRequest {
  return {
    contactId: values.contactId,
    title: values.title,
    mail:
      type === OutreachType.MAIL
        ? { subject: values.subject, body: values.body, language: values.language }
        : null,
  };
}

/**
 * Whether the user may change the attachments of the outreach's mail: an
 * admin, while the outreach is not sent. The server refuses a member's
 * writes and any write once the outreach is SENT.
 */
export function canWriteMailAttachments(
  outreach: Pick<OutreachListItem, "status">,
  isAdmin: boolean
): boolean {
  return isAdmin && isOutreachEditable(outreach);
}
