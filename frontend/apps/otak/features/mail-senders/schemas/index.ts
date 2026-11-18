import { z } from "zod";
import type { ApiResponse } from "@oba/shared/schema";
import { instantSchema } from "@oba/shared/lib/date-schemas";

// ── Enum Schemas ────────────────────────────────────────────────────

/**
 * How the server finds the mailbox in the Microsoft 365 tenant: a personal
 * mailbox by its account, a shared mailbox by its mail address, which may
 * differ from the account's sign-in name.
 */
export const EmailTypeSchema = z.enum(["PERSONAL", "SHARED"]);
export type EmailType = z.infer<typeof EmailTypeSchema>;
export const EmailType = EmailTypeSchema.enum;

// ── Constants ───────────────────────────────────────────────────────

/** The limits the server validates; the form checks the same ones first. */
export const MAIL_SENDER_LIMITS = {
  mailAddressMax: 254,
} as const;

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * Adding a sender takes its type and mail address; the server checks that a
 * mailbox of that type exists in the Microsoft 365 tenant.
 */
export const mailSenderRequestSchema = z.object({
  type: EmailTypeSchema,
  mailAddress: z.string(),
});

// ── Form Schema ─────────────────────────────────────────────────────

/**
 * Builds the add-sender form schema with translated messages. `t` is the
 * `mailSenders.form` translator. The type is one of the known types; the
 * address is required, a valid mail address and capped at the server's limit.
 */
export const createMailSenderFormSchema = (t: (key: string) => string) =>
  z.object({
    type: EmailTypeSchema,
    mailAddress: z
      .string()
      .trim()
      .min(1, t("errors.mailAddressRequired"))
      .max(MAIL_SENDER_LIMITS.mailAddressMax, t("errors.mailAddressMax"))
      // A blank or too long address already has its own message.
      .refine(
        (value) =>
          value === "" ||
          value.length > MAIL_SENDER_LIMITS.mailAddressMax ||
          z.email().safeParse(value).success,
        t("errors.mailAddressInvalid")
      ),
  });

// ── Response Schemas ────────────────────────────────────────────────

export const mailSenderListItemSchema = z.object({
  id: z.string(),
  mailAddress: z.string(),
  type: EmailTypeSchema,
  addedBy: z.string(),
  createdAt: instantSchema,
});

// ── Inferred Types ──────────────────────────────────────────────────

export type MailSenderRequest = z.infer<typeof mailSenderRequestSchema>;
export type MailSenderFormValues = z.infer<ReturnType<typeof createMailSenderFormSchema>>;
export type MailSenderListItem = z.infer<typeof mailSenderListItemSchema>;

/** The list envelope; the server returns every sender at once, without paging. */
export type MailSenderListApiResponse = ApiResponse<MailSenderListItem[]>;

// ── Form Mapping ────────────────────────────────────────────────────

/** The empty form of a new sender; most senders are personal mailboxes. */
export function emptyMailSenderFormValues(): MailSenderFormValues {
  return { type: EmailType.PERSONAL, mailAddress: "" };
}

/** The request the server checks; the form schema has already trimmed the address. */
export function toMailSenderRequest(values: MailSenderFormValues): MailSenderRequest {
  return { type: values.type, mailAddress: values.mailAddress };
}
