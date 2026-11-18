import { z } from "zod";

/*
 * Wire shapes of daily updates (`/kaizen/updates`), the same as the web app
 * reads. Instants are ISO strings with `Z`; a day is a `YYYY-MM-DD` date in
 * Europe/Istanbul, the zone of the posting deadline. Enum fields stay strings,
 * for the reason given in lib/schemas/home.ts.
 */

// ── Limits (mirror the daily update requests on the server) ─────────

export const UPDATE_TICKETS_MAX = 30;
export const UPDATE_NOTE_MAX = 500;
export const UPDATE_TEXT_MAX = 2000;
export const UPDATE_PERCENTAGE_MIN = 0;
export const UPDATE_PERCENTAGE_MAX = 100;
/** The posting deadline, a wall-clock time in the business zone; the server holds the same rule. */
export const UPDATE_DEADLINE_TIME = "10:30";

// ── Error codes the app reacts to ───────────────────────────────────

export const UPDATE_ERROR_CODES = {
  /** A size or required-field limit of a request (422); `errorValues` maps each field to its message. */
  validation: "GEN_VAL_001",
  /** The caller's role may not read Not posted or nudge (403): MEMBER and GUEST. */
  roleTooLow: "ORG_SEC_003",
  /** The nudged member is the caller (422). */
  nudgeSelf: "DLY_VAL_001",
  /** The nudged member already has an update today (422). */
  nudgeRecipientPosted: "DLY_VAL_002",
  /** The nudged member is not expected to post: not an active owner, admin or member (404). */
  nudgeRecipientNotFound: "DLY_NOT_001",
  /** Someone already nudged the member today in this organization (409). */
  nudgeAlreadySent: "DLY_STA_001",
} as const;

// ── Requests ────────────────────────────────────────────────────────

export const updateTicketRequestSchema = z.object({
  ticketId: z.string(),
  percentage: z.number().int().min(UPDATE_PERCENTAGE_MIN).max(UPDATE_PERCENTAGE_MAX),
  note: z.string().max(UPDATE_NOTE_MAX).nullable(),
});

/**
 * Body of create and of edit: the full content, lines in card order. On edit
 * a new `ticketId` adds a line and a missing one removes it. Blank texts are
 * sent as null; the server renders them as "None".
 */
export const updateWriteRequestSchema = z.object({
  tickets: z.array(updateTicketRequestSchema).min(1).max(UPDATE_TICKETS_MAX),
  focus: z.string().max(UPDATE_TEXT_MAX).nullable(),
  questions: z.string().max(UPDATE_TEXT_MAX).nullable(),
});

// ── Responses ───────────────────────────────────────────────────────

export const updateAuthorResponseSchema = z.object({
  userId: z.string(),
  email: z.string().nullable().optional(),
  firstName: z.string().nullable().optional(),
  lastName: z.string().nullable().optional(),
});

/**
 * One ticket line. Key, title and board are a snapshot from when the line
 * was added. `canOpen` is computed for the reader: the ticket still exists
 * and the reader is a member of its board, so the key opens it only then.
 */
export const updateTicketResponseSchema = z.object({
  /** Null once the ticket was removed. */
  ticketId: z.string().nullable(),
  boardId: z.string(),
  boardName: z.string(),
  ticketKey: z.string(),
  ticketTitle: z.string(),
  percentage: z.number(),
  note: z.string().nullable().optional(),
  /** The line wrote its percentage to the ticket. */
  progressSynced: z.boolean(),
  canOpen: z.boolean(),
  /** The ticket's Mission Rank now; null when it was deleted or the reader cannot open it. */
  missionRank: z.string().nullable().optional(),
});

/** A ticket of the author that is DONE in an active sprint, on a board the reader is a member of. */
export const updateFinishedTicketResponseSchema = z.object({
  ticketId: z.string(),
  boardId: z.string(),
  boardName: z.string(),
  ticketKey: z.string(),
  ticketTitle: z.string(),
  missionRank: z.string().nullable().optional(),
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
  /** The four-part plain text, the same for every reader; what Copy copies. */
  summaryText: z.string(),
});

/**
 * A ticket a new update can start from (`AllTicketListResponse`). The line
 * starts at the ticket's own progress: a percentage left unchanged is written
 * back to the ticket, so starting anywhere else would change its progress.
 */
export const updateTicketOptionSchema = z.object({
  id: z.string(),
  key: z.string(),
  title: z.string(),
  percentage: z.number(),
  parentTicketId: z.string().nullable().optional(),
  board: z.object({ id: z.string(), name: z.string() }).nullable().optional(),
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
  /** That day's deadline in the business zone, as an instant. */
  deadline: z.string(),
  workday: z.boolean(),
  deadlinePassed: z.boolean(),
  expectedCount: z.number(),
  members: z.array(updateMissingMemberSchema),
});

/** The answer to a nudge: whom, for which business day, and when it was sent. */
export const updateNudgeResponseSchema = z.object({
  recipientId: z.string(),
  day: z.string(),
  createdAt: z.string(),
});

// ── Types ───────────────────────────────────────────────────────────

export type UpdateTicketRequest = z.infer<typeof updateTicketRequestSchema>;
export type UpdateWriteRequest = z.infer<typeof updateWriteRequestSchema>;
export type UpdateAuthorResponse = z.infer<typeof updateAuthorResponseSchema>;
export type UpdateTicketResponse = z.infer<typeof updateTicketResponseSchema>;
export type UpdateFinishedTicketResponse = z.infer<typeof updateFinishedTicketResponseSchema>;
export type UpdateResponse = z.infer<typeof updateResponseSchema>;
export type UpdateTicketOption = z.infer<typeof updateTicketOptionSchema>;
export type UpdateMissingMember = z.infer<typeof updateMissingMemberSchema>;
export type UpdateMissingResponse = z.infer<typeof updateMissingResponseSchema>;
export type UpdateNudgeResponse = z.infer<typeof updateNudgeResponseSchema>;

/**
 * What the daily updates channel shows: one business day (`YYYY-MM-DD`) and
 * optionally one author. The day is always set; today is the default.
 */
export interface UpdateChannelFilter {
  day: string;
  personId: string | null;
}
