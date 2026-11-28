import { z } from "zod";
import type { ApiResponse } from "@oba/shared/schema";
import { dateOnlySchema, instantSchema } from "@oba/shared/lib/date-schemas";
import { DocumentLanguageSchema } from "@/features/document-templates/schemas";
import { WEBSITE_PATTERN } from "@/features/relations/schemas";

// A campaign is an umbrella of the organization, such as the post "De wacht".
// Its contents are what is actually shared: one channel, one language, one
// text, published by one legal entity, with its own status and images. Oba
// does not post: a member posts by hand and marks the content published.

// ── Enum Schemas ────────────────────────────────────────────────────

export const CampaignChannelSchema = z.enum(["LINKEDIN", "X", "NEWSLETTER"]);
export type CampaignChannel = z.infer<typeof CampaignChannelSchema>;
export const CampaignChannel = CampaignChannelSchema.enum;

/**
 * Where a content stands. A SCHEDULED one has a planned moment; a DRAFT or
 * SCHEDULED one is not out yet, so it has no published moment. Any status
 * may follow any other.
 */
export const CampaignStatusSchema = z.enum(["DRAFT", "SCHEDULED", "PUBLISHED", "ARCHIVED"]);
export type CampaignStatus = z.infer<typeof CampaignStatusSchema>;
export const CampaignStatus = CampaignStatusSchema.enum;

// ── Constants ───────────────────────────────────────────────────────

/**
 * The length a post on the channel may have, shown next to the text as a
 * hint only: the server stores longer texts, since the platforms change
 * their limits. A newsletter has none.
 */
export const CHANNEL_TEXT_LIMITS: Partial<Record<CampaignChannel, number>> = {
  LINKEDIN: 3000,
  X: 280,
};

// ── Rules shared by the request and the form ────────────────────────

/** True when the range is valid: a missing day, or an end on or after the start. */
export function isCampaignDateRangeValid(startDate: string | null, endDate: string | null): boolean {
  if (!startDate || !endDate) return true;
  return endDate >= startDate;
}

/** A rule of the status and its moments that a content breaks. */
export type CampaignStatusDatesIssue =
  | "plannedAtRequired"
  | "publishedAtNotAllowed"
  | "publishedAtInFuture";

/**
 * The first status rule the moments break, as the server checks them, or
 * null: a SCHEDULED content needs a planned moment, a DRAFT or SCHEDULED one
 * takes no published moment, and a published moment is not after `now`, since
 * a post that goes out later is scheduled instead.
 */
export function campaignStatusDatesIssue(
  status: CampaignStatus,
  plannedAt: string | null,
  publishedAt: string | null,
  now: Date
): CampaignStatusDatesIssue | null {
  if (status === CampaignStatus.SCHEDULED && plannedAt === null) {
    return "plannedAtRequired";
  }
  if (publishedAt === null) {
    return null;
  }
  if (status === CampaignStatus.DRAFT || status === CampaignStatus.SCHEDULED) {
    return "publishedAtNotAllowed";
  }
  if (new Date(publishedAt).getTime() > now.getTime()) {
    return "publishedAtInFuture";
  }
  return null;
}

/** The form field each status rule is shown on. */
const STATUS_DATES_ISSUE_FIELDS: Record<CampaignStatusDatesIssue, "plannedAt" | "publishedAt"> = {
  plannedAtRequired: "plannedAt",
  publishedAtNotAllowed: "publishedAt",
  publishedAtInFuture: "publishedAt",
};

// ── Date and time inputs ────────────────────────────────────────────

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

/**
 * An API instant as the value of a `datetime-local` input: the viewer's own
 * wall-clock time to the minute, `YYYY-MM-DDTHH:mm`. Null gives "".
 */
export function instantToLocalInput(value: string | null): string {
  if (value === null) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

/**
 * The value of a `datetime-local` input as a UTC instant. The input has no
 * zone, so it is read as the viewer's wall-clock time. "" and a value that is
 * not a moment give null.
 */
export function localInputToInstant(value: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(value)) return null;
  // A date and time without an offset is read in the local zone.
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

// ── Request Schemas ─────────────────────────────────────────────────

/**
 * Campaign create and update alike; an update is a full replace, so null
 * clears a field. The days are calendar days (`YYYY-MM-DD`), never instants.
 */
export const campaignRequestSchema = z
  .object({
    name: z.string().trim().min(1),
    description: z.string().nullable(),
    startDate: dateOnlySchema.nullable(),
    endDate: dateOnlySchema.nullable(),
    ownerUserId: z.string().nullable(),
    eventId: z.string().nullable(),
    icpId: z.string().nullable(),
  })
  .refine((request) => isCampaignDateRangeValid(request.startDate, request.endDate), {
    path: ["endDate"],
  });

/**
 * Content create and update alike; an update is a full replace and needs a
 * status. The status rules are checked against the browser's clock, as the
 * server checks them against its own.
 */
export const campaignContentRequestSchema = z
  .object({
    legalEntityId: z.string().min(1),
    channel: CampaignChannelSchema,
    language: DocumentLanguageSchema,
    text: z.string().refine((text) => text.trim().length > 0),
    status: CampaignStatusSchema,
    plannedAt: instantSchema.nullable(),
    publishedAt: instantSchema.nullable(),
    postUrl: z.string().regex(WEBSITE_PATTERN).nullable(),
  })
  .superRefine((request, ctx) => {
    const issue = campaignStatusDatesIssue(
      request.status,
      request.plannedAt,
      request.publishedAt,
      new Date()
    );
    if (issue !== null) {
      ctx.addIssue({ code: "custom", message: issue, path: [STATUS_DATES_ISSUE_FIELDS[issue]] });
    }
  });

/** The list's query string. The server answers every match, unpaged, newest change first. */
export const campaignQueryRequestSchema = z.object({
  search: z.string().min(1).optional(),
  eventId: z.string().optional(),
});

// ── Form Schemas ────────────────────────────────────────────────────

/** "" or a calendar day, as a `date` input gives it. */
function optionalDayField(message: string) {
  return z.string().refine((value) => value === "" || dateOnlySchema.safeParse(value).success, {
    message,
  });
}

/** "" or a moment, as a `datetime-local` input gives it. */
function optionalMomentField(message: string) {
  return z.string().refine((value) => value === "" || localInputToInstant(value) !== null, {
    message,
  });
}

/**
 * Builds the campaign form schema with translated messages. `t` is the
 * `campaigns.form` translator. Optional fields hold "" for "none";
 * {@link toCampaignRequest} turns them into the nulls the server wants.
 */
export const createCampaignFormSchema = (t: (key: string) => string) =>
  z
    .object({
      name: z.string().trim().min(1, t("errors.nameRequired")),
      description: z.string(),
      startDate: optionalDayField(t("errors.dateInvalid")),
      endDate: optionalDayField(t("errors.dateInvalid")),
      ownerUserId: z.string(),
      eventId: z.string(),
      icpId: z.string(),
    })
    .refine(
      (values) => isCampaignDateRangeValid(values.startDate || null, values.endDate || null),
      { message: t("errors.dateRange"), path: ["endDate"] }
    );

/**
 * Builds the content form schema with translated messages. `t` is the
 * `campaigns.contentForm` translator. The selects hold "" until one is
 * picked; the moments hold `datetime-local` values in the viewer's zone.
 * `now` is read per check, so a form left open does not judge a published
 * moment against the time it was opened.
 */
export const createCampaignContentFormSchema = (
  t: (key: string) => string,
  now: () => Date = () => new Date()
) =>
  z
    .object({
      legalEntityId: z.string().min(1, t("errors.legalEntityRequired")),
      // A length check, not `!== ""`: TypeScript would infer a type predicate
      // from that and the form's input and output types would differ.
      channel: z
        .union([CampaignChannelSchema, z.literal("")])
        .refine((value) => value.length > 0, t("errors.channelRequired")),
      language: z
        .union([DocumentLanguageSchema, z.literal("")])
        .refine((value) => value.length > 0, t("errors.languageRequired")),
      text: z.string().refine((value) => value.trim() !== "", t("errors.textRequired")),
      status: CampaignStatusSchema,
      plannedAt: optionalMomentField(t("errors.dateTimeInvalid")),
      publishedAt: optionalMomentField(t("errors.dateTimeInvalid")),
      postUrl: z
        .string()
        .trim()
        .refine((value) => value === "" || WEBSITE_PATTERN.test(value), t("errors.postUrlInvalid")),
    })
    .superRefine((values, ctx) => {
      const issue = campaignStatusDatesIssue(
        values.status,
        localInputToInstant(values.plannedAt),
        localInputToInstant(values.publishedAt),
        now()
      );
      if (issue !== null) {
        ctx.addIssue({
          code: "custom",
          message: t(`errors.${issue}`),
          path: [STATUS_DATES_ISSUE_FIELDS[issue]],
        });
      }
    });

// ── Response Schemas ────────────────────────────────────────────────

export const campaignOwnerSchema = z.object({
  id: z.string(),
  name: z.string(),
});

export const campaignListItemSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  startDate: dateOnlySchema.nullable(),
  endDate: dateOnlySchema.nullable(),
  owner: campaignOwnerSchema.nullable(),
  eventId: z.string().nullable(),
  icpId: z.string().nullable(),
  contentCount: z.number(),
  createdAt: instantSchema,
  updatedAt: instantSchema,
});

/** The same fields as the list; the contents come from their own list. */
export const campaignDetailSchema = campaignListItemSchema;

export const campaignContentSchema = z.object({
  id: z.string(),
  campaignId: z.string(),
  legalEntityId: z.string(),
  legalEntityShortName: z.string(),
  channel: CampaignChannelSchema,
  language: DocumentLanguageSchema,
  text: z.string(),
  status: CampaignStatusSchema,
  plannedAt: instantSchema.nullable(),
  publishedAt: instantSchema.nullable(),
  /** The address of the post once it is live. */
  postUrl: z.string().nullable(),
  createdAt: instantSchema,
  updatedAt: instantSchema,
});

// ── Inferred Types ──────────────────────────────────────────────────

export type CampaignRequest = z.infer<typeof campaignRequestSchema>;
export type CampaignContentRequest = z.infer<typeof campaignContentRequestSchema>;
export type CampaignQueryRequest = z.infer<typeof campaignQueryRequestSchema>;
export type CampaignFormValues = z.infer<ReturnType<typeof createCampaignFormSchema>>;
export type CampaignContentFormValues = z.infer<ReturnType<typeof createCampaignContentFormSchema>>;
export type CampaignOwner = z.infer<typeof campaignOwnerSchema>;
export type CampaignListItem = z.infer<typeof campaignListItemSchema>;
export type CampaignDetail = z.infer<typeof campaignDetailSchema>;
export type CampaignContent = z.infer<typeof campaignContentSchema>;

/** The list envelopes: every match, with no paging block. */
export type CampaignListApiResponse = ApiResponse<CampaignListItem[]>;
export type CampaignContentListApiResponse = ApiResponse<CampaignContent[]>;

// ── Campaign form mapping ───────────────────────────────────────────

/** An empty input means "none", which the server takes as null, never "". */
function emptyToNull(value: string): string | null {
  return value === "" ? null : value;
}

export function emptyCampaignFormValues(): CampaignFormValues {
  return {
    name: "",
    description: "",
    startDate: "",
    endDate: "",
    ownerUserId: "",
    eventId: "",
    icpId: "",
  };
}

export function campaignToFormValues(campaign: CampaignDetail): CampaignFormValues {
  return {
    name: campaign.name,
    description: campaign.description ?? "",
    startDate: campaign.startDate ?? "",
    endDate: campaign.endDate ?? "",
    ownerUserId: campaign.owner?.id ?? "",
    eventId: campaign.eventId ?? "",
    icpId: campaign.icpId ?? "",
  };
}

/** The full request the server writes: every optional field null when empty. */
export function toCampaignRequest(values: CampaignFormValues): CampaignRequest {
  return {
    name: values.name.trim(),
    description: emptyToNull(values.description.trim()),
    startDate: emptyToNull(values.startDate),
    endDate: emptyToNull(values.endDate),
    ownerUserId: emptyToNull(values.ownerUserId),
    eventId: emptyToNull(values.eventId),
    icpId: emptyToNull(values.icpId),
  };
}

// ── Content form mapping ────────────────────────────────────────────

/**
 * The empty form of a new content: a draft. `legalEntityId` is the one
 * legal entity of the organization when it has only one, else "".
 */
export function emptyCampaignContentFormValues(legalEntityId: string): CampaignContentFormValues {
  return {
    legalEntityId,
    channel: "",
    language: "",
    text: "",
    status: CampaignStatus.DRAFT,
    plannedAt: "",
    publishedAt: "",
    postUrl: "",
  };
}

/** The edit form of a stored content, its moments in the viewer's zone. */
export function campaignContentToFormValues(content: CampaignContent): CampaignContentFormValues {
  return {
    legalEntityId: content.legalEntityId,
    channel: content.channel,
    language: content.language,
    text: content.text,
    status: content.status,
    plannedAt: instantToLocalInput(content.plannedAt),
    publishedAt: instantToLocalInput(content.publishedAt),
    postUrl: content.postUrl ?? "",
  };
}

/**
 * The full request the server writes: the moments as UTC instants and the
 * text kept as typed. The schema refuses an empty channel or language, so a
 * checked form passes both here.
 */
export function toCampaignContentRequest(
  values: CampaignContentFormValues,
  channel: CampaignChannel,
  language: CampaignContentRequest["language"]
): CampaignContentRequest {
  return {
    legalEntityId: values.legalEntityId,
    channel,
    language,
    text: values.text,
    status: values.status,
    plannedAt: localInputToInstant(values.plannedAt),
    publishedAt: localInputToInstant(values.publishedAt),
    postUrl: emptyToNull(values.postUrl.trim()),
  };
}

/** A DRAFT or SCHEDULED content is not out yet, so it can be marked as published. */
export function canMarkContentPublished(content: Pick<CampaignContent, "status">): boolean {
  return content.status === CampaignStatus.DRAFT || content.status === CampaignStatus.SCHEDULED;
}

/**
 * The update that marks a stored content as published after it was posted
 * by hand. No published moment is sent, so the server stores its own now.
 */
export function toMarkPublishedRequest(content: CampaignContent): CampaignContentRequest {
  return {
    legalEntityId: content.legalEntityId,
    channel: content.channel,
    language: content.language,
    text: content.text,
    status: CampaignStatus.PUBLISHED,
    plannedAt: content.plannedAt,
    publishedAt: null,
    postUrl: content.postUrl,
  };
}
