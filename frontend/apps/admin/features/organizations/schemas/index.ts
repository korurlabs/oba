import { z } from "zod";
import { instantSchema } from "@oba/shared/lib/date-schemas";

/**
 * Legal entities: the companies an organization issues documents as. They are
 * read by every member and written by an OWNER or ADMIN, through
 * `/legal-entities` with the organization in `X-Organization-ID`.
 *
 * The organization schemas themselves live in `@oba/shared`, because the
 * account app reads them too; legal entities are managed only here.
 */

// ── Field limits ───────────────────────────────────────────────────

/** The backend's column and bean validation limits, so the form refuses what the API would. */
export const LEGAL_ENTITY_LIMITS = {
  shortName: 100,
  legalName: 200,
  country: 3,
  registrationNumber: 50,
  vatNumber: 50,
  street: 200,
  postalCode: 20,
  city: 100,
  governingLaw: 200,
  court: 200,
} as const;

/** The fields that may be empty; the API stores them as null. */
export const LEGAL_ENTITY_OPTIONAL_FIELDS = [
  "registrationNumber",
  "vatNumber",
  "street",
  "postalCode",
  "city",
  "governingLaw",
  "court",
] as const;

export type LegalEntityOptionalField = (typeof LEGAL_ENTITY_OPTIONAL_FIELDS)[number];

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * Body of both POST /legal-entities and PUT /legal-entities/{legalEntityId}.
 * The update is a full replace: every field is sent on every call, and null
 * clears an optional field. A blank optional value is refused by the backend.
 */
export const legalEntityRequestSchema = z.object({
  shortName: z.string(),
  legalName: z.string(),
  country: z.string(),
  registrationNumber: z.string().nullable(),
  vatNumber: z.string().nullable(),
  street: z.string().nullable(),
  postalCode: z.string().nullable(),
  city: z.string().nullable(),
  governingLaw: z.string().nullable(),
  court: z.string().nullable(),
});

// ── Form Schemas ────────────────────────────────────────────────────

const optionalText = (max: number, message: string) => z.string().trim().max(max, message);

const requiredText = (max: number, requiredMessage: string, maxMessage: string) =>
  z.string().trim().min(1, requiredMessage).max(max, maxMessage);

/**
 * Create and edit form of a legal entity. An empty optional field is held as
 * "" here and converted to null by toLegalEntityRequest. Values are checked
 * trimmed, because they are sent trimmed.
 */
export const legalEntityFormSchema = (t: (key: string) => string) =>
  z.object({
    shortName: requiredText(
      LEGAL_ENTITY_LIMITS.shortName,
      t("legalEntities.shortNameRequired"),
      t("legalEntities.shortNameMaxLength")
    ),
    legalName: requiredText(
      LEGAL_ENTITY_LIMITS.legalName,
      t("legalEntities.legalNameRequired"),
      t("legalEntities.legalNameMaxLength")
    ),
    country: requiredText(
      LEGAL_ENTITY_LIMITS.country,
      t("legalEntities.countryRequired"),
      t("legalEntities.countryMaxLength")
    ),
    registrationNumber: optionalText(
      LEGAL_ENTITY_LIMITS.registrationNumber,
      t("legalEntities.registrationNumberMaxLength")
    ),
    vatNumber: optionalText(LEGAL_ENTITY_LIMITS.vatNumber, t("legalEntities.vatNumberMaxLength")),
    street: optionalText(LEGAL_ENTITY_LIMITS.street, t("legalEntities.streetMaxLength")),
    postalCode: optionalText(
      LEGAL_ENTITY_LIMITS.postalCode,
      t("legalEntities.postalCodeMaxLength")
    ),
    city: optionalText(LEGAL_ENTITY_LIMITS.city, t("legalEntities.cityMaxLength")),
    governingLaw: optionalText(
      LEGAL_ENTITY_LIMITS.governingLaw,
      t("legalEntities.governingLawMaxLength")
    ),
    court: optionalText(LEGAL_ENTITY_LIMITS.court, t("legalEntities.courtMaxLength")),
  });

// ── Response Schemas ────────────────────────────────────────────────

/** A row of GET /legal-entities, sorted by short name with case ignored. */
export const legalEntityListItemSchema = z.object({
  id: z.guid(),
  shortName: z.string(),
  legalName: z.string(),
  country: z.string(),
  registrationNumber: z.string().nullable(),
  updatedAt: instantSchema,
});

export const legalEntityDetailSchema = z.object({
  id: z.guid(),
  shortName: z.string(),
  legalName: z.string(),
  country: z.string(),
  registrationNumber: z.string().nullable(),
  vatNumber: z.string().nullable(),
  street: z.string().nullable(),
  postalCode: z.string().nullable(),
  city: z.string().nullable(),
  governingLaw: z.string().nullable(),
  court: z.string().nullable(),
  createdAt: instantSchema,
  updatedAt: instantSchema,
});

// ── Inferred Types ──────────────────────────────────────────────────

export type LegalEntityRequest = z.infer<typeof legalEntityRequestSchema>;
export type LegalEntityFormValues = z.infer<ReturnType<typeof legalEntityFormSchema>>;
export type LegalEntityListItem = z.infer<typeof legalEntityListItemSchema>;
export type LegalEntityDetail = z.infer<typeof legalEntityDetailSchema>;
