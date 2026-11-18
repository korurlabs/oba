import { z } from "zod";
import type { ApiResponse } from "@oba/shared/schema";
import { dateOnlySchema, instantSchema } from "@oba/shared/lib/date-schemas";
import { CountrySchema } from "@/lib/countries";
import { relationPaginationSchema, WEBSITE_PATTERN } from "@/features/relations/schemas";

// ── Enum Schemas ────────────────────────────────────────────────────

export const EventTypeSchema = z.enum(["EXPO", "CONFERENCE", "MEETUP", "WEBINAR", "OTHER"]);
export type EventType = z.infer<typeof EventTypeSchema>;
export const EventType = EventTypeSchema.enum;

/**
 * Upcoming or past on the viewer's calendar day. Only a list query names it;
 * an event stores no period.
 */
export const EventPeriodSchema = z.enum(["UPCOMING", "PAST"]);
export type EventPeriod = z.infer<typeof EventPeriodSchema>;
export const EventPeriod = EventPeriodSchema.enum;

// ── Constants ───────────────────────────────────────────────────────

/** The limits the server validates; the form checks the same ones first. */
export const EVENT_LIMITS = {
  nameMax: 200,
  cityMax: 100,
  websiteMax: 500,
  notesMax: 10000,
  searchMax: 200,
} as const;

/** Rows per page of the event list. */
export const EVENTS_PAGE_SIZE = 25;

/**
 * How many events the relation form's select offers: the most recent ones by
 * start date. The server pages at most 50 rows.
 */
export const EVENT_OPTIONS_LIMIT = 50;

/**
 * True when the range is valid: no end date, or an end on or after the start.
 * `YYYY-MM-DD` values compare as strings in calendar order.
 */
export function isEventDateRangeValid(startDate: string, endDate: string | null): boolean {
  if (!endDate || !startDate) return true;
  return endDate >= startDate;
}

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * Create and update alike; an update is a full replace, so null clears a
 * field. Dates are calendar days (`YYYY-MM-DD`), never instants, so a day
 * means the same for every viewer. An end before the start is refused on
 * `endDate`, as the server does with `OTK_VAL_010`.
 */
export const eventRequestSchema = z
  .object({
    name: z.string().trim().min(1).max(EVENT_LIMITS.nameMax),
    type: EventTypeSchema,
    startDate: dateOnlySchema,
    endDate: dateOnlySchema.nullable(),
    city: z.string().max(EVENT_LIMITS.cityMax).nullable(),
    country: CountrySchema.nullable(),
    website: z.string().max(EVENT_LIMITS.websiteMax).regex(WEBSITE_PATTERN).nullable(),
    notes: z.string().min(1).max(EVENT_LIMITS.notesMax).nullable(),
  })
  .refine((request) => isEventDateRangeValid(request.startDate, request.endDate), {
    path: ["endDate"],
  });

/**
 * The list's query string. A period needs the viewer's `today`, else the
 * server answers `OTK_VAL_011`; without a period it lists every event, the
 * most recent start first.
 */
export const eventQueryRequestSchema = z.object({
  period: EventPeriodSchema.optional(),
  today: dateOnlySchema.optional(),
  search: z.string().optional(),
  page: z.number().int().min(1),
  amount: z.number().int().min(1).max(50),
});

// ── Form Schema ─────────────────────────────────────────────────────

/**
 * Builds the event form schema with translated messages. `t` is the
 * `events.form` translator. Optional fields hold "" for "none";
 * {@link toEventRequest} turns that into the null the server wants. The date
 * inputs give `YYYY-MM-DD` without a time zone, so no viewer sees a day shift.
 */
export const createEventFormSchema = (t: (key: string) => string) =>
  z
    .object({
      name: z
        .string()
        .trim()
        .min(1, t("errors.nameRequired"))
        .max(EVENT_LIMITS.nameMax, t("errors.nameMax")),
      type: z
        .union([EventTypeSchema, z.literal("")])
        // Not a type predicate, so the form's input and output types stay equal.
        .refine((value) => value.length > 0, t("errors.typeRequired")),
      startDate: z
        .string()
        .refine((value) => value !== "", t("errors.startDateRequired"))
        .refine((value) => value === "" || dateOnlySchema.safeParse(value).success, {
          message: t("errors.dateInvalid"),
        }),
      endDate: z
        .string()
        .refine((value) => value === "" || dateOnlySchema.safeParse(value).success, {
          message: t("errors.dateInvalid"),
        }),
      city: z.string().trim().max(EVENT_LIMITS.cityMax, t("errors.cityMax")),
      country: z.union([CountrySchema, z.literal("")]),
      website: z
        .string()
        .trim()
        .max(EVENT_LIMITS.websiteMax, t("errors.websiteMax"))
        .refine((value) => value === "" || WEBSITE_PATTERN.test(value), t("errors.websiteInvalid")),
      notes: z.string().max(EVENT_LIMITS.notesMax, t("errors.notesMax")),
    })
    .refine((values) => isEventDateRangeValid(values.startDate, values.endDate), {
      message: t("errors.dateRange"),
      path: ["endDate"],
    });

// ── Response Schemas ────────────────────────────────────────────────

export const eventListItemSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: EventTypeSchema,
  startDate: dateOnlySchema,
  endDate: dateOnlySchema.nullable(),
  city: z.string().nullable(),
  country: CountrySchema.nullable(),
  website: z.string().nullable(),
});

export const eventDetailSchema = eventListItemSchema.extend({
  notes: z.string().nullable(),
  createdAt: instantSchema,
  updatedAt: instantSchema,
});

// ── Inferred Types ──────────────────────────────────────────────────

export type EventRequest = z.infer<typeof eventRequestSchema>;
export type EventQueryRequest = z.infer<typeof eventQueryRequestSchema>;
export type EventFormValues = z.infer<ReturnType<typeof createEventFormSchema>>;
export type EventListItem = z.infer<typeof eventListItemSchema>;
export type EventDetail = z.infer<typeof eventDetailSchema>;

/** The list envelope, with the API's own paging block. */
export type EventListApiResponse = Omit<ApiResponse<EventListItem[]>, "pagination"> & {
  pagination?: z.infer<typeof relationPaginationSchema>;
};

// ── Form Mapping ────────────────────────────────────────────────────

/** An empty input means "none", which the server takes as null, never "". */
function emptyToNull(value: string): string | null {
  return value === "" ? null : value;
}

/** The empty form of a new event. */
export function emptyEventFormValues(): EventFormValues {
  return {
    name: "",
    type: "",
    startDate: "",
    endDate: "",
    city: "",
    country: "",
    website: "",
    notes: "",
  };
}

/** The edit form of a stored event. */
export function eventToFormValues(event: EventDetail): EventFormValues {
  return {
    name: event.name,
    type: event.type,
    startDate: event.startDate,
    endDate: event.endDate ?? "",
    city: event.city ?? "",
    country: event.country ?? "",
    website: event.website ?? "",
    notes: event.notes ?? "",
  };
}

/**
 * The full request the server writes: every optional field, null when empty.
 * `type` is required, so the form has checked it is set.
 */
export function toEventRequest(values: EventFormValues, type: EventType): EventRequest {
  return {
    name: values.name.trim(),
    type,
    startDate: values.startDate,
    endDate: emptyToNull(values.endDate),
    city: emptyToNull(values.city.trim()),
    country: values.country === "" ? null : values.country,
    website: emptyToNull(values.website.trim()),
    // The server refuses empty notes, and blank ones say nothing.
    notes: values.notes.trim() === "" ? null : values.notes,
  };
}
