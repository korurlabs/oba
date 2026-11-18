import { z } from "zod";
import type { ApiResponse } from "@oba/shared/schema";
import { instantSchema } from "@oba/shared/lib/date-schemas";
import { CountrySchema } from "@/lib/countries";
import {
  relationPaginationSchema,
  RelationSizeSchema,
  type RelationSize,
} from "@/features/relations/schemas";

// ── Enum Schemas ────────────────────────────────────────────────────

export const IndustrySchema = z.enum([
  "SOFTWARE",
  "IT_SERVICES",
  "TELECOM",
  "FINANCE",
  "INSURANCE",
  "HEALTHCARE",
  "MANUFACTURING",
  "LOGISTICS",
  "RETAIL",
  "ECOMMERCE",
  "ENERGY",
  "CONSTRUCTION",
  "REAL_ESTATE",
  "LEGAL",
  "ACCOUNTING",
  "EDUCATION",
  "GOVERNMENT",
  "NON_PROFIT",
  "HOSPITALITY",
  "MEDIA",
  "AUTOMOTIVE",
  "AGRICULTURE",
  "DEFENSE",
  "OTHER",
]);
export type Industry = z.infer<typeof IndustrySchema>;
export const Industry = IndustrySchema.enum;

export const TargetPersonRoleSchema = z.enum([
  "OWNER",
  "CEO",
  "CTO",
  "CIO",
  "CISO",
  "IT_MANAGER",
  "OPERATIONS",
  "FINANCE",
  "OTHER",
]);
export type TargetPersonRole = z.infer<typeof TargetPersonRoleSchema>;
export const TargetPersonRole = TargetPersonRoleSchema.enum;

// ── Constants ───────────────────────────────────────────────────────

/** The limits the server validates; the form checks the same ones first. */
export const ICP_LIMITS = {
  nameMax: 100,
  descriptionMax: 10000,
  searchMax: 100,
} as const;

/** Rows per page of the ICP list. */
export const ICPS_PAGE_SIZE = 25;

/** How many ICPs the relation form's select offers: the first ones by name. */
export const ICP_OPTIONS_LIMIT = 50;

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * Create and update alike; an update is a full replace, so null clears a
 * field. The server refuses an empty description: send null instead.
 */
export const icpRequestSchema = z.object({
  name: z.string(),
  industry: IndustrySchema,
  country: CountrySchema.nullable(),
  companySizeMin: RelationSizeSchema.nullable(),
  companySizeMax: RelationSizeSchema.nullable(),
  targetPersonRole: TargetPersonRoleSchema.nullable(),
  description: z.string().nullable(),
});

/** The list's query string; the server always orders by name. */
export const icpQueryRequestSchema = z.object({
  search: z.string().optional(),
  page: z.number().int().min(1),
  amount: z.number().int().min(1).max(50),
});

// ── Form Schema ─────────────────────────────────────────────────────

/** True when both sizes are set and the minimum band lies above the maximum. */
function isSizeRangeInverted(min: RelationSize | "", max: RelationSize | ""): boolean {
  if (min === "" || max === "") return false;
  const order = RelationSizeSchema.options;
  return order.indexOf(min) > order.indexOf(max);
}

/**
 * Builds the ICP form schema with translated messages. `t` is the
 * `icps.form` translator. Optional fields hold "" for "none";
 * {@link toIcpRequest} turns that into the null the server wants. A size
 * range with the minimum above the maximum is shown on `companySizeMax`, the
 * field the server's `OTK_VAL_001` maps to as well.
 */
export const createIcpFormSchema = (t: (key: string) => string) =>
  z
    .object({
      name: z
        .string()
        .trim()
        .min(1, t("errors.nameRequired"))
        .max(ICP_LIMITS.nameMax, t("errors.nameMax")),
      industry: z
        .union([IndustrySchema, z.literal("")])
        // Not a type predicate, so the form's input and output types stay equal.
        .refine((value) => value.length > 0, t("errors.industryRequired")),
      country: z.union([CountrySchema, z.literal("")]),
      companySizeMin: z.union([RelationSizeSchema, z.literal("")]),
      companySizeMax: z.union([RelationSizeSchema, z.literal("")]),
      targetPersonRole: z.union([TargetPersonRoleSchema, z.literal("")]),
      description: z.string().max(ICP_LIMITS.descriptionMax, t("errors.descriptionMax")),
    })
    .refine((values) => !isSizeRangeInverted(values.companySizeMin, values.companySizeMax), {
      message: t("errors.sizeRange"),
      path: ["companySizeMax"],
    });

// ── Response Schemas ────────────────────────────────────────────────

export const icpListItemSchema = z.object({
  id: z.string(),
  name: z.string(),
  industry: IndustrySchema,
  country: CountrySchema.nullable(),
  companySizeMin: RelationSizeSchema.nullable(),
  companySizeMax: RelationSizeSchema.nullable(),
  targetPersonRole: TargetPersonRoleSchema.nullable(),
  updatedAt: instantSchema,
});

export const icpDetailSchema = icpListItemSchema.extend({
  description: z.string().nullable(),
  createdAt: instantSchema,
});

// ── Inferred Types ──────────────────────────────────────────────────

export type IcpRequest = z.infer<typeof icpRequestSchema>;
export type IcpQueryRequest = z.infer<typeof icpQueryRequestSchema>;
export type IcpFormValues = z.infer<ReturnType<typeof createIcpFormSchema>>;
export type IcpListItem = z.infer<typeof icpListItemSchema>;
export type IcpDetail = z.infer<typeof icpDetailSchema>;

/** The list envelope, with the API's own paging block. */
export type IcpListApiResponse = Omit<ApiResponse<IcpListItem[]>, "pagination"> & {
  pagination?: z.infer<typeof relationPaginationSchema>;
};

// ── Form Mapping ────────────────────────────────────────────────────

/** The empty form of a new ICP. */
export function emptyIcpFormValues(): IcpFormValues {
  return {
    name: "",
    industry: "",
    country: "",
    companySizeMin: "",
    companySizeMax: "",
    targetPersonRole: "",
    description: "",
  };
}

/** The edit form of a stored ICP. */
export function icpToFormValues(icp: IcpDetail): IcpFormValues {
  return {
    name: icp.name,
    industry: icp.industry,
    country: icp.country ?? "",
    companySizeMin: icp.companySizeMin ?? "",
    companySizeMax: icp.companySizeMax ?? "",
    targetPersonRole: icp.targetPersonRole ?? "",
    description: icp.description ?? "",
  };
}

/**
 * The full request the server writes: every optional field, null when empty.
 * `industry` is required, so the form has checked it is set.
 */
export function toIcpRequest(values: IcpFormValues, industry: Industry): IcpRequest {
  return {
    name: values.name,
    industry,
    country: values.country === "" ? null : values.country,
    companySizeMin: values.companySizeMin === "" ? null : values.companySizeMin,
    companySizeMax: values.companySizeMax === "" ? null : values.companySizeMax,
    targetPersonRole: values.targetPersonRole === "" ? null : values.targetPersonRole,
    // The server refuses an empty description, and a blank one says nothing.
    description: values.description.trim() === "" ? null : values.description,
  };
}
