import { z } from "zod";
import { instantSchema } from "@oba/shared/lib/date-schemas";
import { WEBSITE_PATTERN } from "@/features/relations/schemas";

// ── Enum Schemas ────────────────────────────────────────────────────

/**
 * What kind of address a contact's e-mail is: the person's own address at
 * their company, an inbox others also read (info@, sales@), or a private one.
 */
export const ContactEmailTypeSchema = z.enum(["COMPANY", "SHARED", "PERSONAL"]);
export type ContactEmailType = z.infer<typeof ContactEmailTypeSchema>;
export const ContactEmailType = ContactEmailTypeSchema.enum;

/**
 * Who a contact is: a person at the relation, or the company's general details
 * (info@, sales@). Independent of the e-mail type.
 */
export const ContactKindSchema = z.enum(["PERSON", "BUSINESS"]);
export type ContactKind = z.infer<typeof ContactKindSchema>;
export const ContactKind = ContactKindSchema.enum;

// ── Constants ───────────────────────────────────────────────────────

/** The column limits the server validates; the form checks the same ones first. */
export const CONTACT_LIMITS = {
  nameMax: 200,
  roleMax: 100,
  emailMax: 254,
  phoneMax: 50,
  linkedinUrlMax: 500,
} as const;

/** The server's LinkedIn rule: an http(s) URL with a dotted host, as for a website. */
export const LINKEDIN_URL_PATTERN = WEBSITE_PATTERN;

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * Create and update alike; an update is a full replace, so null clears a field.
 * The kind and both main flags are always sent. True in the flag of the
 * contact's kind marks it main of that kind. False on the current main of the
 * kind is refused (another contact is marked main instead), so an edit sends
 * the stored flag back. A true flag of the other kind is refused too.
 */
export const contactRequestSchema = z.object({
  name: z.string(),
  kind: ContactKindSchema,
  role: z.string().nullable(),
  email: z.string().nullable(),
  emailType: ContactEmailTypeSchema.nullable(),
  phone: z.string().nullable(),
  linkedinUrl: z.string().nullable(),
  decisionMaker: z.boolean(),
  mainPersonContact: z.boolean(),
  mainBusinessContact: z.boolean(),
});

// ── Form Schema ─────────────────────────────────────────────────────

/**
 * Builds the contact form schema with translated messages. `t` is the
 * `contacts.form` translator. Optional fields hold "" for "none";
 * {@link toContactRequest} turns that into the null the server wants.
 */
export const createContactFormSchema = (t: (key: string) => string) =>
  z.object({
    name: z
      .string()
      .trim()
      .min(1, t("errors.nameRequired"))
      .max(CONTACT_LIMITS.nameMax, t("errors.nameMax")),
    kind: ContactKindSchema,
    role: z.string().trim().max(CONTACT_LIMITS.roleMax, t("errors.roleMax")),
    email: z
      .string()
      .trim()
      .max(CONTACT_LIMITS.emailMax, t("errors.emailMax"))
      .refine(
        (value) => value === "" || z.email().safeParse(value).success,
        t("errors.emailInvalid")
      ),
    emailType: z.union([ContactEmailTypeSchema, z.literal("")]),
    phone: z.string().trim().max(CONTACT_LIMITS.phoneMax, t("errors.phoneMax")),
    linkedinUrl: z
      .string()
      .trim()
      .max(CONTACT_LIMITS.linkedinUrlMax, t("errors.linkedinUrlMax"))
      .refine(
        (value) => value === "" || LINKEDIN_URL_PATTERN.test(value),
        t("errors.linkedinUrlInvalid")
      ),
    decisionMaker: z.boolean(),
    mainPersonContact: z.boolean(),
    mainBusinessContact: z.boolean(),
  });

// ── Response Schemas ────────────────────────────────────────────────

/**
 * A row of a relation's contact list; the list puts the main person contact
 * first, then the main business contact.
 */
export const contactListItemSchema = z.object({
  id: z.string(),
  relationId: z.string(),
  name: z.string(),
  kind: ContactKindSchema,
  role: z.string().nullable(),
  email: z.string().nullable(),
  emailType: ContactEmailTypeSchema.nullable(),
  phone: z.string().nullable(),
  linkedinUrl: z.string().nullable(),
  decisionMaker: z.boolean(),
  mainPersonContact: z.boolean(),
  mainBusinessContact: z.boolean(),
  sourceUrl: z.string().nullable(),
});

/** One contact as a create or an update answers it. */
export const contactDetailSchema = contactListItemSchema.extend({
  createdAt: instantSchema,
  updatedAt: instantSchema,
});

// ── Inferred Types ──────────────────────────────────────────────────

export type ContactRequest = z.infer<typeof contactRequestSchema>;
export type ContactFormValues = z.infer<ReturnType<typeof createContactFormSchema>>;
export type ContactListItem = z.infer<typeof contactListItemSchema>;
export type ContactDetail = z.infer<typeof contactDetailSchema>;

// ── Form Mapping ────────────────────────────────────────────────────

function emptyToNull(value: string): string | null {
  return value === "" ? null : value;
}

/** The empty form of a new contact: a person, since most contacts are one. */
export function emptyContactFormValues(): ContactFormValues {
  return {
    name: "",
    kind: ContactKind.PERSON,
    role: "",
    email: "",
    emailType: "",
    phone: "",
    linkedinUrl: "",
    decisionMaker: false,
    mainPersonContact: false,
    mainBusinessContact: false,
  };
}

/** The edit form of a stored contact. */
export function contactToFormValues(contact: ContactListItem): ContactFormValues {
  return {
    name: contact.name,
    kind: contact.kind,
    role: contact.role ?? "",
    email: contact.email ?? "",
    emailType: contact.emailType ?? "",
    phone: contact.phone ?? "",
    linkedinUrl: contact.linkedinUrl ?? "",
    decisionMaker: contact.decisionMaker,
    mainPersonContact: contact.mainPersonContact,
    mainBusinessContact: contact.mainBusinessContact,
  };
}

/**
 * The request the server stores: every optional field, null when empty. The
 * server refuses a type without an address, so no address sends no type. It
 * also refuses a main flag of the other kind, so only the flag of the chosen
 * kind is sent as stored: after a kind change, the old kind's flag goes false.
 */
export function toContactRequest(values: ContactFormValues): ContactRequest {
  const email = emptyToNull(values.email);
  return {
    name: values.name,
    kind: values.kind,
    role: emptyToNull(values.role),
    email,
    emailType: email === null || values.emailType === "" ? null : values.emailType,
    phone: emptyToNull(values.phone),
    linkedinUrl: emptyToNull(values.linkedinUrl),
    decisionMaker: values.decisionMaker,
    mainPersonContact: values.kind === ContactKind.PERSON && values.mainPersonContact,
    mainBusinessContact: values.kind === ContactKind.BUSINESS && values.mainBusinessContact,
  };
}
