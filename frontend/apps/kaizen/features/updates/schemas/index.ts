import { z } from "zod";
import { MissionRankSchema, TicketPrioritySchema } from "@/features/tickets/schemas";

// ── Limits (mirror the daily update requests on the server) ─────────

export const UPDATE_TICKETS_MAX = 30;
export const UPDATE_NOTE_MAX = 500;
export const UPDATE_TEXT_MAX = 2000;
export const UPDATE_PERCENTAGE_MIN = 0;
export const UPDATE_PERCENTAGE_MAX = 100;

/**
 * The zone of the daily posting deadline (10:30) and of the day it applies
 * to. The rule is the same for every reader, so its times are shown in this
 * zone rather than the reader's.
 */
export const UPDATE_DEADLINE_TIME_ZONE = "Europe/Istanbul";

// ── Request Schemas ─────────────────────────────────────────────────

export const updateTicketRequestSchema = z.object({
  ticketId: z.string(),
  percentage: z.number().int().min(UPDATE_PERCENTAGE_MIN).max(UPDATE_PERCENTAGE_MAX),
  note: z.string().max(UPDATE_NOTE_MAX).nullable().optional(),
});

/**
 * Body of create and of the create preview. Blank strings are stored as null
 * by the server; an empty focus or questions renders "- None" in the text.
 */
export const updateCreateRequestSchema = z.object({
  tickets: z.array(updateTicketRequestSchema).min(1).max(UPDATE_TICKETS_MAX),
  focus: z.string().max(UPDATE_TEXT_MAX).nullable().optional(),
  questions: z.string().max(UPDATE_TEXT_MAX).nullable().optional(),
});

/**
 * Body of edit and of the edit preview: the full new content, lines in order.
 * A new `ticketId` adds a line and a missing one removes it.
 */
export const updateEditRequestSchema = updateCreateRequestSchema;

/**
 * Who has not posted. `date` is `YYYY-MM-DD`; null asks for today in
 * `UPDATE_DEADLINE_TIME_ZONE`, so the server decides the day.
 */
export const updateMissingParamsSchema = z.object({
  organizationId: z.string(),
  date: z.string().nullable(),
});

// ── Response Schemas ────────────────────────────────────────────────

export const updateAuthorResponseSchema = z.object({
  userId: z.string(),
  email: z.string(),
  firstName: z.string().nullable().optional(),
  lastName: z.string().nullable().optional(),
});

/**
 * One ticket line. Key, title, board ID and board name are a snapshot from
 * when the line was added. `canOpen` is computed for the reader: true only
 * when the ticket still exists and the reader is a member of its board, so a
 * key links to the ticket only then.
 */
export const updateTicketResponseSchema = z.object({
  /** Null once the ticket row was removed. */
  ticketId: z.string().nullable(),
  boardId: z.string(),
  boardName: z.string(),
  ticketKey: z.string(),
  ticketTitle: z.string(),
  percentage: z.number(),
  note: z.string().nullable().optional(),
  /** The line wrote its percentage to the ticket; in a preview, it would. */
  progressSynced: z.boolean(),
  canOpen: z.boolean(),
  /** The ticket's Mission Rank now; null when it was deleted or the reader cannot open it. */
  missionRank: MissionRankSchema.nullable().optional(),
  /** The ticket's priority now; null when it was deleted or the reader cannot open it. */
  priority: TicketPrioritySchema.nullable().optional(),
});

/**
 * A ticket of the author that is DONE in an ACTIVE sprint, on a board the
 * reader is a member of, so the reader can always open it.
 */
export const updateFinishedTicketResponseSchema = z.object({
  ticketId: z.string(),
  boardId: z.string(),
  boardName: z.string(),
  ticketKey: z.string(),
  ticketTitle: z.string(),
  missionRank: MissionRankSchema.nullable(),
  /** Always sent by the server; optional so a card still renders against an API without it. */
  priority: TicketPrioritySchema.optional(),
});

export const updateResponseSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  /** Time of the author's last edit; null when never edited. */
  editedAt: z.string().nullable(),
  author: updateAuthorResponseSchema,
  focus: z.string().nullable().optional(),
  questions: z.string().nullable().optional(),
  items: z.array(updateTicketResponseSchema),
  /** By ticket key; empty when the author finished nothing this sprint. */
  finishedTickets: z.array(updateFinishedTicketResponseSchema),
  /** The four-part plain text, the same for every reader. */
  summaryText: z.string(),
});

/** The card as it will be posted: the author is the reader. */
export const updatePreviewResponseSchema = z.object({
  items: z.array(updateTicketResponseSchema),
  focus: z.string().nullable().optional(),
  questions: z.string().nullable().optional(),
  finishedTickets: z.array(updateFinishedTicketResponseSchema),
  summaryText: z.string(),
});

/** An expected member without an update before the deadline. */
export const updateMissingMemberSchema = z.object({
  userId: z.string(),
  email: z.string(),
  firstName: z.string().nullable(),
  lastName: z.string().nullable(),
  /** Null when nothing was posted that day; else the first update's time, at or after the deadline. */
  postedAt: z.string().nullable(),
});

/**
 * One organization's report for one day. On a Saturday or Sunday `workday`
 * is false and nobody is expected. `members` lists those not posted first,
 * then the late ones by `postedAt`.
 */
export const updateMissingResponseSchema = z.object({
  organizationId: z.string(),
  /** The day the report covers, `YYYY-MM-DD`. */
  date: z.string(),
  /** That day's 10:30 in `UPDATE_DEADLINE_TIME_ZONE`, as an instant. */
  deadline: z.string(),
  workday: z.boolean(),
  deadlinePassed: z.boolean(),
  expectedCount: z.number(),
  members: z.array(updateMissingMemberSchema),
});

// ── Form Schema ─────────────────────────────────────────────────────
// The messages are i18n keys, resolved by `FormMessage`. The limits mirror
// the server, which answers 422 for the same cases.

const percentageMessage = "updates.form.percentageRange";

/**
 * One selected ticket. Key, title and board are what the card shows; only
 * `ticketId`, `percentage` and `note` are sent.
 */
export const updateFormLineSchema = z.object({
  ticketId: z.string(),
  percentage: z
    .number({ error: percentageMessage })
    .int(percentageMessage)
    .min(UPDATE_PERCENTAGE_MIN, percentageMessage)
    .max(UPDATE_PERCENTAGE_MAX, percentageMessage),
  note: z.string().max(UPDATE_NOTE_MAX, "updates.form.noteTooLong"),
  ticketKey: z.string(),
  ticketTitle: z.string(),
  boardId: z.string(),
  boardName: z.string(),
  /** False for a kept line whose board the author has left or whose ticket was deleted. */
  canOpen: z.boolean(),
});

/**
 * What the live preview may render: the author sees the card while still
 * composing, so an empty focus is allowed here. The preview endpoints accept
 * it too and show "None" for it.
 */
export const updateFormDraftSchema = z.object({
  tickets: z
    .array(updateFormLineSchema)
    .min(1, "updates.form.ticketsRequired")
    .max(UPDATE_TICKETS_MAX, "updates.form.ticketsTooMany"),
  focus: z.string().max(UPDATE_TEXT_MAX, "updates.form.textTooLong"),
  questions: z.string().max(UPDATE_TEXT_MAX, "updates.form.textTooLong"),
});

/**
 * What Confirm and Save may post: every update says what its author focuses
 * on today, so the focus must hold text after trimming. The server rejects a
 * blank focus on create and edit with the same rule.
 */
export const updateFormSchema = updateFormDraftSchema.extend({
  focus: z
    .string()
    .trim()
    .min(1, "updates.form.focusRequired")
    .max(UPDATE_TEXT_MAX, "updates.form.textTooLong"),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type UpdateTicketRequest = z.infer<typeof updateTicketRequestSchema>;
export type UpdateCreateRequest = z.infer<typeof updateCreateRequestSchema>;
export type UpdateEditRequest = z.infer<typeof updateEditRequestSchema>;
export type UpdateAuthorResponse = z.infer<typeof updateAuthorResponseSchema>;
export type UpdateTicketResponse = z.infer<typeof updateTicketResponseSchema>;
export type UpdateFinishedTicketResponse = z.infer<typeof updateFinishedTicketResponseSchema>;
export type UpdateResponse = z.infer<typeof updateResponseSchema>;
export type UpdatePreviewResponse = z.infer<typeof updatePreviewResponseSchema>;
/** What a card body renders; both a posted update and a preview carry it. */
export type UpdateCardData = Pick<UpdateResponse, "items" | "focus" | "questions" | "finishedTickets">;
export type UpdateMissingParams = z.infer<typeof updateMissingParamsSchema>;
export type UpdateMissingMember = z.infer<typeof updateMissingMemberSchema>;
export type UpdateMissingResponse = z.infer<typeof updateMissingResponseSchema>;
export type UpdateFormLine = z.infer<typeof updateFormLineSchema>;
export type UpdateFormValues = z.infer<typeof updateFormSchema>;

