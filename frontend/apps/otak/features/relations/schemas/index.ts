import { z } from "zod";
import type { ApiResponse } from "@oba/shared/schema";
import { instantSchema } from "@oba/shared/lib/date-schemas";
import { OrganizationRoleSchema } from "@oba/shared/features/organizations/schemas";
import { attachmentResponseSchema } from "@oba/shared/features/attachments/schemas";
import { CountrySchema } from "@/lib/countries";

// ── Enum Schemas ────────────────────────────────────────────────────

export const RelationTypeSchema = z.enum(["LEAD", "SUPPLIER", "CUSTOMER"]);
export type RelationType = z.infer<typeof RelationTypeSchema>;
export const RelationType = RelationTypeSchema.enum;

/**
 * A relation's place in the pipeline. A lead has one of {@link LEAD_STAGES}; a
 * customer has CLOSED (a won lead) or DIRECT (never a lead); a supplier has none.
 */
export const PipelineStageSchema = z.enum([
  "NEW",
  "QUALIFIED",
  "CONTACTED",
  "WARM",
  "PROPOSAL",
  "CLOSED",
  "LOST",
  "PARKED",
  "DIRECT",
]);
export type PipelineStage = z.infer<typeof PipelineStageSchema>;
export const PipelineStage = PipelineStageSchema.enum;

export const RelationSizeSchema = z.enum([
  "SIZE_1_10",
  "SIZE_11_50",
  "SIZE_51_200",
  "SIZE_201_500",
  "SIZE_501_1000",
  "SIZE_1001_5000",
  "SIZE_5001_PLUS",
]);
export type RelationSize = z.infer<typeof RelationSizeSchema>;
export const RelationSize = RelationSizeSchema.enum;

export const RelationSourceSchema = z.enum([
  "NETWORK",
  "LINKEDIN",
  "REFERRAL",
  "INBOUND",
  "PARTNER",
  "EVENT",
  "RESEARCH",
  "OTHER",
]);
export type RelationSource = z.infer<typeof RelationSourceSchema>;
export const RelationSource = RelationSourceSchema.enum;

/** Where a file of a relation is attached: to the relation itself or to one of its mails. */
export const RelationAttachmentSourceSchema = z.enum(["RELATION", "MAIL"]);
export type RelationAttachmentSource = z.infer<typeof RelationAttachmentSourceSchema>;
export const RelationAttachmentSource = RelationAttachmentSourceSchema.enum;

// ── Constants ───────────────────────────────────────────────────────

/** The column limits the server validates; the form checks the same ones first. */
export const RELATION_LIMITS = {
  nameMax: 200,
  websiteMax: 500,
  sectorMax: 100,
  registrationNumberMax: 50,
  vatNumberMax: 50,
  streetMax: 200,
  postalCodeMax: 20,
  cityMax: 100,
  notesMax: 10000,
  searchMax: 200,
  /** `NUMERIC(12,2)`: at most 10 integer digits. */
  expectedValueExclusiveMax: 10_000_000_000,
} as const;

/** The stages a lead is worked in; the battlefield shows one column for each. */
export const OPEN_LEAD_STAGES = [
  PipelineStage.NEW,
  PipelineStage.QUALIFIED,
  PipelineStage.CONTACTED,
  PipelineStage.WARM,
  PipelineStage.PROPOSAL,
] as const satisfies readonly PipelineStage[];

/**
 * The stages of a lead nobody works any more. The leads list hides them
 * unless its stage filter names them.
 */
export const INACTIVE_LEAD_STAGES = [
  PipelineStage.LOST,
  PipelineStage.PARKED,
] as const satisfies readonly PipelineStage[];

/** Every stage a lead can have; CLOSED is not one, since reaching it makes the lead a customer. */
export const LEAD_STAGES = [
  ...OPEN_LEAD_STAGES,
  ...INACTIVE_LEAD_STAGES,
] as const satisfies readonly PipelineStage[];

/** A customer's stage: CLOSED when it was won as a lead, DIRECT when it never was one. */
export const CUSTOMER_STAGES = [
  PipelineStage.CLOSED,
  PipelineStage.DIRECT,
] as const satisfies readonly PipelineStage[];

/** The most leads the battlefield answers with; the server stops there. */
export const BATTLEFIELD_MAX_LEADS = 500;

/** Rows per page of the relations list. */
export const RELATIONS_PAGE_SIZE = 25;

/** The server's website rule: an http(s) URL with a dotted host. */
export const WEBSITE_PATTERN = /^https?:\/\/[^\s/?#]+\.[^\s]+$/;

// ── Request Schemas ─────────────────────────────────────────────────

export const relationUpdateRequestSchema = z.object({
  name: z.string(),
  website: z.string().nullable(),
  sector: z.string().nullable(),
  size: RelationSizeSchema.nullable(),
  registrationNumber: z.string().nullable(),
  vatNumber: z.string().nullable(),
  street: z.string().nullable(),
  postalCode: z.string().nullable(),
  city: z.string().nullable(),
  country: CountrySchema.nullable(),
  source: RelationSourceSchema.nullable(),
  expectedValue: z.number().nullable(),
  ownerUserId: z.string().nullable(),
  /** The ICP of the organization the relation belongs to; null for none. */
  icpId: z.string().nullable(),
  /** The event of the organization where the relation was met; null for none. */
  eventId: z.string().nullable(),
  notes: z.string().nullable(),
});

/** The update fields plus the type, which only a create sets. */
export const relationCreateRequestSchema = relationUpdateRequestSchema.extend({
  type: RelationTypeSchema,
});

/**
 * The list's query string. `stage` is repeated and applies to leads and
 * customers; a stage foreign to the type matches nothing. Without `type` the
 * server lists every type and every stage and ignores `stage`. `eventId`
 * keeps the relations linked to that event; an unknown id matches nothing.
 */
export const relationQueryRequestSchema = z.object({
  type: RelationTypeSchema.optional(),
  stage: z.array(PipelineStageSchema).optional(),
  ownerUserId: z.string().optional(),
  search: z.string().optional(),
  eventId: z.string().optional(),
  page: z.number().int().min(1),
  amount: z.number().int().min(1).max(50),
});

/**
 * `PATCH .../stage`: a lead's new stage; the same stage again is accepted.
 * CLOSED makes the lead a customer; DIRECT is refused with 409 `OTK_STA_006`.
 */
export const relationStageRequestSchema = z.object({ stage: PipelineStageSchema });

/**
 * `PATCH .../type`: a type other than the current one, else 409 `OTK_STA_003`.
 * The server sets the stage: NEW for a lead, CLOSED for a lead that becomes a
 * customer, DIRECT for a supplier that does, none for a supplier.
 */
export const relationTypeRequestSchema = z.object({ type: RelationTypeSchema });

// ── Form Schema ─────────────────────────────────────────────────────

/** True when a number has at most two fraction digits (cents). */
function hasAtMostTwoDecimals(value: number): boolean {
  return Math.abs(value * 100 - Math.round(value * 100)) < 1e-6;
}

/**
 * Builds the create and edit form schema with translated messages. `t` is
 * the `relations.form` translator. Optional text fields hold "" for "none";
 * {@link toRelationUpdateRequest} turns that into the null the server wants.
 */
export const createRelationFormSchema = (t: (key: string) => string) =>
  z.object({
    name: z
      .string()
      .trim()
      .min(1, t("errors.nameRequired"))
      .max(RELATION_LIMITS.nameMax, t("errors.nameMax")),
    type: z
      .union([RelationTypeSchema, z.literal("")])
      // Not a type predicate, so the form's input and output types stay equal.
      .refine((value) => value.length > 0, t("errors.typeRequired")),
    website: z
      .string()
      .trim()
      .max(RELATION_LIMITS.websiteMax, t("errors.websiteMax"))
      .refine((value) => value === "" || WEBSITE_PATTERN.test(value), t("errors.websiteInvalid")),
    sector: z.string().trim().max(RELATION_LIMITS.sectorMax, t("errors.sectorMax")),
    size: z.union([RelationSizeSchema, z.literal("")]),
    registrationNumber: z
      .string()
      .trim()
      .max(RELATION_LIMITS.registrationNumberMax, t("errors.registrationNumberMax")),
    vatNumber: z.string().trim().max(RELATION_LIMITS.vatNumberMax, t("errors.vatNumberMax")),
    street: z.string().trim().max(RELATION_LIMITS.streetMax, t("errors.streetMax")),
    postalCode: z.string().trim().max(RELATION_LIMITS.postalCodeMax, t("errors.postalCodeMax")),
    city: z.string().trim().max(RELATION_LIMITS.cityMax, t("errors.cityMax")),
    country: z.union([CountrySchema, z.literal("")]),
    source: z.union([RelationSourceSchema, z.literal("")]),
    expectedValue: z
      .number()
      .min(0, t("errors.expectedValueNegative"))
      .lt(RELATION_LIMITS.expectedValueExclusiveMax, t("errors.expectedValueTooLarge"))
      .refine(hasAtMostTwoDecimals, t("errors.expectedValueDecimals"))
      .nullable(),
    ownerUserId: z.string(),
    icpId: z.string(),
    eventId: z.string(),
    notes: z.string().max(RELATION_LIMITS.notesMax, t("errors.notesMax")),
  });

// ── Response Schemas ────────────────────────────────────────────────

export const relationOwnerSchema = z.object({
  id: z.string(),
  name: z.string(),
});

export const relationListItemSchema = z.object({
  id: z.string(),
  name: z.string(),
  website: z.string().nullable(),
  type: RelationTypeSchema,
  stage: PipelineStageSchema.nullable(),
  owner: relationOwnerSchema.nullable(),
  /** The name of the relation's main person contact; null while it has none. */
  mainPersonContactName: z.string().nullable(),
  /** The email of the main person contact; null without that contact or its email. */
  mainPersonContactEmail: z.string().nullable(),
  /** The email of the main business contact; null without that contact or its email. */
  mainBusinessContactEmail: z.string().nullable(),
  expectedValue: z.number().nullable(),
  updatedAt: instantSchema,
});

export const relationDetailSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: RelationTypeSchema,
  stage: PipelineStageSchema.nullable(),
  website: z.string().nullable(),
  sector: z.string().nullable(),
  size: RelationSizeSchema.nullable(),
  registrationNumber: z.string().nullable(),
  vatNumber: z.string().nullable(),
  street: z.string().nullable(),
  postalCode: z.string().nullable(),
  city: z.string().nullable(),
  country: CountrySchema.nullable(),
  source: RelationSourceSchema.nullable(),
  expectedValue: z.number().nullable(),
  owner: relationOwnerSchema.nullable(),
  icpId: z.string().nullable(),
  eventId: z.string().nullable(),
  notes: z.string().nullable(),
  createdAt: instantSchema,
  updatedAt: instantSchema,
});

/**
 * A member who may be picked as owner: every active member of the
 * organization. Only a member with Otak access is accepted as a new owner;
 * a missing `otakAccess` counts as no access, so the select never offers a
 * member the server may refuse.
 */
export const relationOwnerCandidateSchema = z.object({
  userId: z.string(),
  email: z.string(),
  firstName: z.string().nullable(),
  lastName: z.string().nullable(),
  /** The member's role in the organization. */
  role: OrganizationRoleSchema,
  otakAccess: z.boolean().optional(),
});

/**
 * The paging block of the Oba API (`PaginationDetailsResponse`): 1-based
 * `currentPage` and `pageSize`, unlike the shared `Pagination` type.
 */
export const relationPaginationSchema = z.object({
  totalElements: z.number(),
  totalPages: z.number(),
  currentPage: z.number(),
  pageSize: z.number(),
  first: z.boolean(),
  last: z.boolean(),
  empty: z.boolean(),
});

/**
 * A file of the relation or of one of its mails. `mailId` and
 * `outreachTitle` are null for a file of the relation itself; `outreachTitle`
 * is also null for a mail no outreach names.
 */
export const relationAttachmentSchema = attachmentResponseSchema.extend({
  source: RelationAttachmentSourceSchema,
  mailId: z.guid().nullable(),
  outreachTitle: z.string().nullable(),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type RelationCreateRequest = z.infer<typeof relationCreateRequestSchema>;
export type RelationUpdateRequest = z.infer<typeof relationUpdateRequestSchema>;
export type RelationQueryRequest = z.infer<typeof relationQueryRequestSchema>;
export type RelationStageRequest = z.infer<typeof relationStageRequestSchema>;
export type RelationTypeRequest = z.infer<typeof relationTypeRequestSchema>;
export type RelationFormValues = z.infer<ReturnType<typeof createRelationFormSchema>>;
export type RelationOwner = z.infer<typeof relationOwnerSchema>;
export type RelationOwnerCandidate = z.infer<typeof relationOwnerCandidateSchema>;
export type RelationListItem = z.infer<typeof relationListItemSchema>;
export type RelationDetail = z.infer<typeof relationDetailSchema>;
export type RelationPagination = z.infer<typeof relationPaginationSchema>;
export type RelationAttachment = z.infer<typeof relationAttachmentSchema>;

/** The list envelope, with the API's own paging block. */
export type RelationListApiResponse = Omit<ApiResponse<RelationListItem[]>, "pagination"> & {
  pagination?: RelationPagination;
};

// ── Form Mapping ────────────────────────────────────────────────────

/** An empty input means "none", which the server takes as null, never "". */
function emptyToNull(value: string): string | null {
  return value === "" ? null : value;
}

/** The empty form of a new relation, of the given type when one is known. */
export function emptyRelationFormValues(type: RelationType | "" = ""): RelationFormValues {
  return {
    name: "",
    type,
    website: "",
    sector: "",
    size: "",
    registrationNumber: "",
    vatNumber: "",
    street: "",
    postalCode: "",
    city: "",
    country: "",
    source: "",
    expectedValue: null,
    ownerUserId: "",
    icpId: "",
    eventId: "",
    notes: "",
  };
}

/** The edit form of a stored relation. */
export function relationToFormValues(relation: RelationDetail): RelationFormValues {
  return {
    name: relation.name,
    type: relation.type,
    website: relation.website ?? "",
    sector: relation.sector ?? "",
    size: relation.size ?? "",
    registrationNumber: relation.registrationNumber ?? "",
    vatNumber: relation.vatNumber ?? "",
    street: relation.street ?? "",
    postalCode: relation.postalCode ?? "",
    city: relation.city ?? "",
    country: relation.country ?? "",
    source: relation.source ?? "",
    expectedValue: relation.expectedValue,
    ownerUserId: relation.owner?.id ?? "",
    icpId: relation.icpId ?? "",
    eventId: relation.eventId ?? "",
    notes: relation.notes ?? "",
  };
}

/** The full update the server writes: every optional field, null when empty. */
export function toRelationUpdateRequest(values: RelationFormValues): RelationUpdateRequest {
  return {
    name: values.name,
    website: emptyToNull(values.website),
    sector: emptyToNull(values.sector),
    size: values.size === "" ? null : values.size,
    registrationNumber: emptyToNull(values.registrationNumber),
    vatNumber: emptyToNull(values.vatNumber),
    street: emptyToNull(values.street),
    postalCode: emptyToNull(values.postalCode),
    city: emptyToNull(values.city),
    country: values.country === "" ? null : values.country,
    source: values.source === "" ? null : values.source,
    expectedValue: values.expectedValue,
    ownerUserId: emptyToNull(values.ownerUserId),
    icpId: emptyToNull(values.icpId),
    eventId: emptyToNull(values.eventId),
    notes: emptyToNull(values.notes.trim()) === null ? null : values.notes,
  };
}

/** The create request; `type` is required, so the form has checked it is set. */
export function toRelationCreateRequest(
  values: RelationFormValues,
  type: RelationType
): RelationCreateRequest {
  return { ...toRelationUpdateRequest(values), type };
}
