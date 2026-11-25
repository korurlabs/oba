import { z } from "zod";
import { dateOnlySchema, instantSchema } from "@oba/shared/lib/date-schemas";

// ── Limits (mirror the check request on the server) ─────────────────

export const CHECK_NAME_MAX_LENGTH = 255;
export const CHECK_TEXT_MAX_LENGTH = 5000;
export const CHECK_WEIGHT_MIN = 1;
export const CHECK_WEIGHT_MAX = 100;
export const CHECK_SORT_ORDER_MIN = 0;
export const CHECK_SORT_ORDER_MAX = 9999;

/** Days of history the overview's trend shows; the server accepts 1 to 365. */
export const CYBERSCORE_TREND_DAYS = 30;

// ── Error Codes ─────────────────────────────────────────────────────

/** Refusals of a check write that the client names in its own words. */
export const CHECK_ERROR_CODES = {
  /** Another check of the organization has this name (409). */
  duplicateName: "KAZ_STA_020",
  /** The check is gone, or belongs to another organization (404). */
  notFound: "KAZ_NOT_020",
  /** The member is not an organization admin (403). */
  notAllowed: "ORG_SEC_003",
  /** A field broke a limit the form did not catch (422). */
  invalid: "GEN_VAL_001",
} as const;

// ── Enum Schemas ────────────────────────────────────────────────────

/** Declaration order is display order, as on the server. */
export const CheckCategorySchema = z.enum([
  "IDENTITY",
  "OFFICE",
  "NETWORK_EDGE",
  "CLOUD_PLATFORM",
  "APPLICATIONS",
  "DATA",
  "DEVICES",
  "DEVELOPMENT_SUPPLY_CHAIN",
  "DETECTION_RESPONSE",
  "COMPLIANCE",
]);
export type CheckCategory = z.infer<typeof CheckCategorySchema>;
export const CheckCategory = CheckCategorySchema.enum;

/** The categories in display order. */
export const CHECK_CATEGORIES: readonly CheckCategory[] = CheckCategorySchema.options;

/**
 * Result of a check. `NO_LICENSE` and `NOT_APPLICABLE` carry no score factor,
 * so they neither raise nor lower the score.
 */
export const CheckOutcomeSchema = z.enum([
  "TODO",
  "PASS",
  "PARTIAL",
  "FAIL",
  "NO_LICENSE",
  "NOT_APPLICABLE",
]);
export type CheckOutcome = z.infer<typeof CheckOutcomeSchema>;
export const CheckOutcome = CheckOutcomeSchema.enum;

export const CHECK_OUTCOMES: readonly CheckOutcome[] = CheckOutcomeSchema.options;

/** Workflow state of a check; it never affects the score. */
export const CheckStatusSchema = z.enum(["NOT_STARTED", "IN_PROGRESS", "DONE", "NOT_APPLICABLE"]);
export type CheckStatus = z.infer<typeof CheckStatusSchema>;
export const CheckStatus = CheckStatusSchema.enum;

export const CHECK_STATUSES: readonly CheckStatus[] = CheckStatusSchema.options;

/**
 * Risk level of a score: a higher percentage is a lower risk. `UNKNOWN` means
 * no check counts toward the score yet.
 */
export const CyberscoreLevelSchema = z.enum([
  "UNKNOWN",
  "CRITICAL",
  "HIGH",
  "MEDIUM",
  "LOW",
  "MINIMAL",
]);
export type CyberscoreLevel = z.infer<typeof CyberscoreLevelSchema>;
export const CyberscoreLevel = CyberscoreLevelSchema.enum;

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * Body of create and of the full edit. On create a missing outcome is `TODO`;
 * on edit a missing outcome keeps the stored one. A missing status is derived
 * from the outcome, or kept on an edit that does not change the outcome.
 */
export const checkRequestSchema = z.object({
  name: z.string().min(1).max(CHECK_NAME_MAX_LENGTH),
  description: z.string().max(CHECK_TEXT_MAX_LENGTH).nullable().optional(),
  expected: z.string().min(1).max(CHECK_TEXT_MAX_LENGTH),
  weight: z.number().int().min(CHECK_WEIGHT_MIN).max(CHECK_WEIGHT_MAX),
  sortOrder: z
    .number()
    .int()
    .min(CHECK_SORT_ORDER_MIN)
    .max(CHECK_SORT_ORDER_MAX)
    .nullable()
    .optional(),
  category: CheckCategorySchema.nullable().optional(),
  outcome: CheckOutcomeSchema.nullable().optional(),
  status: CheckStatusSchema.nullable().optional(),
});

export const checkOutcomeRequestSchema = z.object({
  outcome: CheckOutcomeSchema,
});

export const checkStatusRequestSchema = z.object({
  status: CheckStatusSchema,
});

/** Query of the check list; no status lists every check. */
export const checkListParamsSchema = z.object({
  status: CheckStatusSchema.optional(),
});

// ── Form Schema ─────────────────────────────────────────────────────

/**
 * Builds the create and edit form schema with translated messages. `t` is the
 * `cyberscore` translator. Weight and sort order come from number inputs as
 * strings, so they are coerced; an empty category is `null`.
 */
export const createCheckFormSchema = (t: (key: string) => string) =>
  z.object({
    name: z
      .string()
      .trim()
      .min(1, t("errors.nameRequired"))
      .max(CHECK_NAME_MAX_LENGTH, t("errors.nameTooLong")),
    description: z.string().trim().max(CHECK_TEXT_MAX_LENGTH, t("errors.descriptionTooLong")),
    expected: z
      .string()
      .trim()
      .min(1, t("errors.expectedRequired"))
      .max(CHECK_TEXT_MAX_LENGTH, t("errors.expectedTooLong")),
    weight: z.coerce
      .number<string | number>(t("errors.weightRange"))
      .int(t("errors.weightRange"))
      .min(CHECK_WEIGHT_MIN, t("errors.weightRange"))
      .max(CHECK_WEIGHT_MAX, t("errors.weightRange")),
    sortOrder: z.coerce
      .number<string | number>(t("errors.sortOrderRange"))
      .int(t("errors.sortOrderRange"))
      .min(CHECK_SORT_ORDER_MIN, t("errors.sortOrderRange"))
      .max(CHECK_SORT_ORDER_MAX, t("errors.sortOrderRange")),
    category: CheckCategorySchema.nullable(),
    outcome: CheckOutcomeSchema,
    status: CheckStatusSchema,
  });

// ── Response Schemas ────────────────────────────────────────────────

export const checkSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  expected: z.string(),
  weight: z.number(),
  sortOrder: z.number(),
  category: CheckCategorySchema.nullable(),
  outcome: CheckOutcomeSchema,
  status: CheckStatusSchema,
  updatedAt: instantSchema,
});

/**
 * The organization's current score. Before the first scoring change it is
 * `0`, `UNKNOWN`, an empty breakdown, no scorable check and a null
 * `lastCalculatedAt`. The breakdown is keyed by category name and holds only
 * categories with at least one scorable check.
 */
export const cyberscoreSchema = z.object({
  scorePercentage: z.number(),
  scoreLevel: CyberscoreLevelSchema,
  breakdownByCategory: z.partialRecord(CheckCategorySchema, z.number()),
  scorableCheckCount: z.number(),
  lastCalculatedAt: instantSchema.nullable(),
});

/**
 * One business day of the trend, oldest first. The first point carries the
 * score held before the window, the last one is today; `UNKNOWN` days are
 * left out, so the list can be shorter than the window or empty.
 */
export const cyberscoreTrendPointSchema = z.object({
  date: dateOnlySchema,
  scorePercentage: z.number(),
  scoreLevel: CyberscoreLevelSchema,
});

// ── Inferred Types ──────────────────────────────────────────────────

export type CheckRequest = z.infer<typeof checkRequestSchema>;
export type CheckOutcomeRequest = z.infer<typeof checkOutcomeRequestSchema>;
export type CheckStatusRequest = z.infer<typeof checkStatusRequestSchema>;
export type CheckListParams = z.infer<typeof checkListParamsSchema>;
/** What the form fields hold: number inputs may still be strings. */
export type CheckFormInput = z.input<ReturnType<typeof createCheckFormSchema>>;
/** What the form submits after validation. */
export type CheckFormValues = z.output<ReturnType<typeof createCheckFormSchema>>;
export type Check = z.infer<typeof checkSchema>;
export type Cyberscore = z.infer<typeof cyberscoreSchema>;
export type CyberscoreTrendPoint = z.infer<typeof cyberscoreTrendPointSchema>;
