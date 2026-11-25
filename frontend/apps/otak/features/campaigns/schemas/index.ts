import { z } from "zod";
import type { ApiResponse } from "@oba/shared/schema";
import { instantSchema } from "@oba/shared/lib/date-schemas";
import {
  DocumentLanguageSchema,
  type DocumentLanguage,
} from "@/features/document-templates/schemas";
import { WEBSITE_PATTERN } from "@/features/relations/schemas";

// A campaign is one post or mailing of the organization on one channel, with
// its text in up to three languages and its images as attachments. Oba does
// not post it: a member plans it, posts it by hand and marks it published.

// ── Enum Schemas ────────────────────────────────────────────────────

export const CampaignChannelSchema = z.enum(["LINKEDIN", "X", "NEWSLETTER"]);
export type CampaignChannel = z.infer<typeof CampaignChannelSchema>;
export const CampaignChannel = CampaignChannelSchema.enum;

/**
 * Where a campaign stands. A SCHEDULED one has a planned moment; a DRAFT or
 * SCHEDULED one is not out yet, so it has no published moment. Any status
 * may follow any other.
 */
export const CampaignStatusSchema = z.enum(["DRAFT", "SCHEDULED", "PUBLISHED", "ARCHIVED"]);
export type CampaignStatus = z.infer<typeof CampaignStatusSchema>;
export const CampaignStatus = CampaignStatusSchema.enum;

// ── Constants ───────────────────────────────────────────────────────

/** The limits the server validates; the form checks the same ones first. */
export const CAMPAIGN_LIMITS = {
  nameMax: 200,
  postUrlMax: 500,
  textMax: 20000,
  searchMax: 200,
} as const;

/** The languages a campaign text is written in, in the order they are shown. */
export const CAMPAIGN_LANGUAGES = DocumentLanguageSchema.options;

/**
 * The length a post on the channel may have, shown next to each text as a
 * hint only: the server stores longer texts, since the platforms change
 * their limits. A newsletter has none.
 */
export const CHANNEL_TEXT_LIMITS: Partial<Record<CampaignChannel, number>> = {
  LINKEDIN: 3000,
  X: 280,
};

/** A rule of the status and its moments that a campaign breaks. */
export type CampaignStatusDatesIssue =
  | "plannedAtRequired"
  | "publishedAtNotAllowed"
  | "publishedAtInFuture";

/**
 * The first status rule the moments break, as the server checks them, or
 * null: a SCHEDULED campaign needs a planned moment, a DRAFT or SCHEDULED one
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
  return new Date(publishedAt).getTime() > now.getTime() ? "publishedAtInFuture" : null;
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

export const campaignContentRequestSchema = z.object({
  language: DocumentLanguageSchema,
  text: z
    .string()
    .max(CAMPAIGN_LIMITS.textMax)
    .refine((text) => text.trim().length > 0),
});

/**
 * Create and update alike; an update is a full replace, so null clears a
 * field and the contents replace the stored ones. One text per language. The
 * status rules are checked against the browser's clock, as the server checks
 * them against its own.
 */
export const campaignRequestSchema = z
  .object({
    name: z.string().trim().min(1).max(CAMPAIGN_LIMITS.nameMax),
    channel: CampaignChannelSchema,
    status: CampaignStatusSchema,
    plannedAt: instantSchema.nullable(),
    publishedAt: instantSchema.nullable(),
    postUrl: z.string().max(CAMPAIGN_LIMITS.postUrlMax).regex(WEBSITE_PATTERN).nullable(),
    ownerUserId: z.string().nullable(),
    eventId: z.string().nullable(),
    icpId: z.string().nullable(),
    contents: z
      .array(campaignContentRequestSchema)
      .max(CAMPAIGN_LANGUAGES.length)
      .refine(
        (contents) => new Set(contents.map((content) => content.language)).size === contents.length
      ),
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

/**
 * The list's query string. The server answers every match, unpaged: a
 * SCHEDULED list soonest planned first, any other newest change first.
 */
export const campaignQueryRequestSchema = z.object({
  status: CampaignStatusSchema.optional(),
  channel: CampaignChannelSchema.optional(),
  search: z.string().min(1).max(CAMPAIGN_LIMITS.searchMax).optional(),
});

// ── Form Schema ─────────────────────────────────────────────────────

const campaignTextsShape = (t: (key: string) => string) =>
  z.object(
    Object.fromEntries(
      CAMPAIGN_LANGUAGES.map((language) => [
        language,
        z.string().max(CAMPAIGN_LIMITS.textMax, t("errors.textMax")),
      ])
    ) as Record<DocumentLanguage, z.ZodString>
  );

/**
 * Builds the campaign form schema with translated messages. `t` is the
 * `campaigns.form` translator. Optional fields hold "" for "none"; the
 * moments hold `datetime-local` values in the viewer's zone, and a blank text
 * means no text in that language. {@link toCampaignRequest} turns the values
 * into the request the server wants. `now` is read per check, so a form left
 * open does not judge a published moment against the time it was opened.
 */
export const createCampaignFormSchema = (
  t: (key: string) => string,
  now: () => Date = () => new Date()
) =>
  z
    .object({
      name: z
        .string()
        .trim()
        .min(1, t("errors.nameRequired"))
        .max(CAMPAIGN_LIMITS.nameMax, t("errors.nameMax")),
      channel: z
        .union([CampaignChannelSchema, z.literal("")])
        // Not a type predicate, so the form's input and output types stay equal.
        .refine((value) => value.length > 0, t("errors.channelRequired")),
      status: CampaignStatusSchema,
      plannedAt: z.string().refine((value) => value === "" || localInputToInstant(value) !== null, {
        message: t("errors.dateTimeInvalid"),
      }),
      publishedAt: z
        .string()
        .refine((value) => value === "" || localInputToInstant(value) !== null, {
          message: t("errors.dateTimeInvalid"),
        }),
      postUrl: z
        .string()
        .trim()
        .max(CAMPAIGN_LIMITS.postUrlMax, t("errors.postUrlMax"))
        .refine((value) => value === "" || WEBSITE_PATTERN.test(value), t("errors.postUrlInvalid")),
      ownerUserId: z.string(),
      eventId: z.string(),
      icpId: z.string(),
      texts: campaignTextsShape(t),
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
  channel: CampaignChannelSchema,
  status: CampaignStatusSchema,
  plannedAt: instantSchema.nullable(),
  publishedAt: instantSchema.nullable(),
  /** The languages the campaign has a text in, in the order NL, TR, EN. */
  languages: z.array(DocumentLanguageSchema),
  owner: campaignOwnerSchema.nullable(),
  eventId: z.string().nullable(),
  icpId: z.string().nullable(),
  createdAt: instantSchema,
  updatedAt: instantSchema,
});

export const campaignContentSchema = z.object({
  language: DocumentLanguageSchema,
  text: z.string(),
  updatedAt: instantSchema,
});

export const campaignDetailSchema = campaignListItemSchema.extend({
  /** The address of the post once it is live. */
  postUrl: z.string().nullable(),
  contents: z.array(campaignContentSchema),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type CampaignContentRequest = z.infer<typeof campaignContentRequestSchema>;
export type CampaignRequest = z.infer<typeof campaignRequestSchema>;
export type CampaignQueryRequest = z.infer<typeof campaignQueryRequestSchema>;
export type CampaignFormValues = z.infer<ReturnType<typeof createCampaignFormSchema>>;
export type CampaignOwner = z.infer<typeof campaignOwnerSchema>;
export type CampaignListItem = z.infer<typeof campaignListItemSchema>;
export type CampaignContent = z.infer<typeof campaignContentSchema>;
export type CampaignDetail = z.infer<typeof campaignDetailSchema>;

/** The list envelope: every match, with no paging block. */
export type CampaignListApiResponse = ApiResponse<CampaignListItem[]>;

// ── Form Mapping ────────────────────────────────────────────────────

/** An empty input means "none", which the server takes as null, never "". */
function emptyToNull(value: string): string | null {
  return value === "" ? null : value;
}

function emptyTexts(): Record<DocumentLanguage, string> {
  return { NL: "", TR: "", EN: "" };
}

/** The empty form of a new campaign: a draft. */
export function emptyCampaignFormValues(): CampaignFormValues {
  return {
    name: "",
    channel: "",
    status: CampaignStatus.DRAFT,
    plannedAt: "",
    publishedAt: "",
    postUrl: "",
    ownerUserId: "",
    eventId: "",
    icpId: "",
    texts: emptyTexts(),
  };
}

/** The edit form of a stored campaign, its moments in the viewer's zone. */
export function campaignToFormValues(campaign: CampaignDetail): CampaignFormValues {
  const texts = emptyTexts();
  for (const content of campaign.contents) {
    texts[content.language] = content.text;
  }
  return {
    name: campaign.name,
    channel: campaign.channel,
    status: campaign.status,
    plannedAt: instantToLocalInput(campaign.plannedAt),
    publishedAt: instantToLocalInput(campaign.publishedAt),
    postUrl: campaign.postUrl ?? "",
    ownerUserId: campaign.owner?.id ?? "",
    eventId: campaign.eventId ?? "",
    icpId: campaign.icpId ?? "",
    texts,
  };
}

/**
 * The full request the server writes: every optional field null when empty,
 * the moments as UTC instants, and one content per language with a text that
 * is not blank, kept as typed. `channel` is required, so the form has checked
 * it is set.
 */
export function toCampaignRequest(
  values: CampaignFormValues,
  channel: CampaignChannel
): CampaignRequest {
  return {
    name: values.name.trim(),
    channel,
    status: values.status,
    plannedAt: localInputToInstant(values.plannedAt),
    publishedAt: localInputToInstant(values.publishedAt),
    postUrl: emptyToNull(values.postUrl.trim()),
    ownerUserId: emptyToNull(values.ownerUserId),
    eventId: emptyToNull(values.eventId),
    icpId: emptyToNull(values.icpId),
    contents: CAMPAIGN_LANGUAGES.filter((language) => values.texts[language].trim() !== "").map(
      (language) => ({ language, text: values.texts[language] })
    ),
  };
}

/** The stored campaign as a full update that changes nothing. */
export function campaignToRequest(campaign: CampaignDetail): CampaignRequest {
  return {
    name: campaign.name,
    channel: campaign.channel,
    status: campaign.status,
    plannedAt: campaign.plannedAt,
    publishedAt: campaign.publishedAt,
    postUrl: campaign.postUrl,
    ownerUserId: campaign.owner?.id ?? null,
    eventId: campaign.eventId,
    icpId: campaign.icpId,
    contents: campaign.contents.map(({ language, text }) => ({ language, text })),
  };
}

/** A DRAFT or SCHEDULED campaign is not out yet, so it can be marked as published. */
export function canMarkCampaignPublished(campaign: Pick<CampaignListItem, "status">): boolean {
  return campaign.status === CampaignStatus.DRAFT || campaign.status === CampaignStatus.SCHEDULED;
}

/**
 * The update that marks a campaign as published after it was posted by
 * hand. No published moment is sent, so the server stores its own now.
 */
export function toMarkPublishedRequest(campaign: CampaignDetail): CampaignRequest {
  return { ...campaignToRequest(campaign), status: CampaignStatus.PUBLISHED, publishedAt: null };
}
