import { z } from "zod";

import { chatAttachmentResponseSchema, type ChatLocalFile } from "@/lib/schemas/chat";

/*
 * The fields of `AllTicketListResponse` (`GET /kazan/tickets`) that the app
 * reads. Other fields are dropped by the parse. Enum fields stay strings, for
 * the reason given in lib/schemas/home.ts.
 */

/** `TicketAssigneeResponse` and `TicketReviewerResponse`: `userId` is the user, `id` the link row. */
export const ticketPersonSchema = z.object({
  id: z.string(),
  userId: z.string(),
  email: z.string().nullable().optional(),
  firstName: z.string().nullable().optional(),
  lastName: z.string().nullable().optional(),
});

/** `TicketBoardResponse`. */
export const ticketBoardSchema = z.object({
  id: z.string(),
  name: z.string(),
  prefix: z.string().nullable().optional(),
  slug: z.string(),
  icon: z.string().nullable().optional(),
  color: z.string().nullable().optional(),
});

/** `TicketCreatedByResponse`: the user who reported the ticket. */
export const ticketReporterSchema = z.object({
  userId: z.string(),
  email: z.string().nullable().optional(),
  firstName: z.string().nullable().optional(),
  lastName: z.string().nullable().optional(),
});

/** `TicketCreatedBySystemActorResponse`: an integration that reported the ticket, such as an alert route. */
export const ticketSystemReporterSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: z.string().nullable().optional(),
});

/** `LabelResponse` and `ProjectListResponse` as a ticket carries them. */
export const ticketOptionSchema = z.object({
  id: z.string(),
  name: z.string(),
  color: z.string().nullable().optional(),
});

/**
 * A root ticket of the cross-board list. `dueDate` and `createdAt` are
 * instants. Exactly one of `createdBy` and `createdBySystemActor` is set.
 */
export const ticketListItemSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  title: z.string(),
  key: z.string(),
  type: z.string(),
  status: z.string(),
  priority: z.string().nullable(),
  missionRank: z.string().nullable(),
  dueDate: z.string().nullable(),
  createdBy: ticketReporterSchema.nullable().optional(),
  createdBySystemActor: ticketSystemReporterSchema.nullable().optional(),
  assignees: z.array(ticketPersonSchema).nullable(),
  board: ticketBoardSchema.nullable(),
  labels: z.array(ticketOptionSchema).nullable().optional(),
});

/** The unpaged list body of `GET /kazan/tickets`. */
export const ticketListSchema = z.array(ticketListItemSchema);

export type TicketPerson = z.infer<typeof ticketPersonSchema>;
export type TicketBoard = z.infer<typeof ticketBoardSchema>;
export type TicketListItem = z.infer<typeof ticketListItemSchema>;

/** `TicketMilestoneResponse`. `targetDate` is date-only. */
export const ticketMilestoneSchema = z.object({
  id: z.string(),
  name: z.string(),
  status: z.string().nullable().optional(),
  targetDate: z.string().nullable().optional(),
});

/** `TicketSprintResponse`. */
export const ticketSprintSchema = z.object({
  id: z.string(),
  name: z.string(),
  status: z.string().nullable().optional(),
});

/**
 * A sub-ticket row of the detail, a `TicketListResponse` without its own
 * sub-tickets. `sprint` is the sub-ticket's own sprint, null in the backlog;
 * it can differ from the parent's.
 */
export const subTicketSchema = z.object({
  id: z.string(),
  key: z.string(),
  title: z.string(),
  type: z.string(),
  status: z.string(),
  priority: z.string().nullable().optional(),
  assignees: z.array(ticketPersonSchema).nullable().optional(),
  sprint: ticketSprintSchema.nullable().optional(),
});

/**
 * `TicketDetailResponse` of `GET /kazan/boards/{boardId}/tickets/{ticketId}`.
 * `dueDate`, `createdAt` and `updatedAt` are instants. Exactly one of `sprint`
 * and `backlog` is set. `description` is Markdown, as the web editor writes it.
 * `rca` and `lessonsLearned` are the root cause analysis and lessons learned
 * an incident needs before it can be set to Done; null until written.
 */
export const ticketDetailSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  title: z.string(),
  description: z.string().nullable().optional(),
  key: z.string(),
  type: z.string(),
  status: z.string(),
  priority: z.string().nullable(),
  missionRank: z.string().nullable(),
  dueDate: z.string().nullable(),
  rca: z.string().nullable().optional(),
  lessonsLearned: z.string().nullable().optional(),
  createdBy: ticketReporterSchema.nullable().optional(),
  createdBySystemActor: ticketSystemReporterSchema.nullable().optional(),
  assignees: z.array(ticketPersonSchema).nullable().optional(),
  project: ticketOptionSchema.nullable().optional(),
  milestone: ticketMilestoneSchema.nullable().optional(),
  board: ticketBoardSchema,
  parentTicketId: z.string().nullable().optional(),
  subTickets: z.array(subTicketSchema).nullable().optional(),
  labels: z.array(ticketOptionSchema).nullable().optional(),
  sprint: ticketSprintSchema.nullable().optional(),
  backlog: z.object({ id: z.string() }).nullable().optional(),
});

export type TicketDetail = z.infer<typeof ticketDetailSchema>;
export type SubTicket = z.infer<typeof subTicketSchema>;
export type TicketReporter = z.infer<typeof ticketReporterSchema>;

/** The longest ticket title the API accepts (`TicketCreateRequest.title`). */
export const TICKET_TITLE_MAX = 255;

/** The longest ticket description the API accepts (`TicketCreateRequest.description`). */
export const TICKET_DESCRIPTION_MAX = 10_000;

/**
 * The longest root cause analysis or lessons learned the API accepts
 * (`TicketUpdateRequest.rca` and `.lessonsLearned`).
 */
export const INCIDENT_TEXT_MAX = 10_000;

/**
 * The 422 for a write that gives a top-level backlog ticket a status other
 * than To Do: a backlog only holds unstarted work, so the ticket must be
 * planned into a sprint before its status can change.
 */
export const TICKET_BACKLOG_STATUS_CODE = "KAZ_VAL_062";

/**
 * The 422 for a write that sets an incident to Done while its root cause
 * analysis or lessons learned is blank. `errorValues.missingFields` names the
 * blank fields (`rca`, `lessonsLearned`).
 */
export const INCIDENT_CLOSE_REQUIRES_RCA_CODE = "KAZ_VAL_063";

/** The longest comment the API accepts (`CommentCreateRequest.content`). */
export const COMMENT_CONTENT_MAX = 10_000;

/** `CommentDetailResponse`. `createdAt` and `updatedAt` are instants. */
export const commentSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  content: z.string(),
  ticketId: z.string(),
  authorId: z.string(),
  authorEmail: z.string().nullable().optional(),
  authorFirstName: z.string().nullable().optional(),
  authorLastName: z.string().nullable().optional(),
});

export const commentListSchema = z.array(commentSchema);

export type Comment = z.infer<typeof commentSchema>;

/**
 * `TicketBulkMoveResponse` of `POST .../tickets/bulk-move`: how many of the
 * requested tickets moved, and the keys (after the move) of those that lost
 * their milestone because they left for a board unrelated to its board.
 */
export const ticketBulkMoveResultSchema = z.object({
  movedCount: z.number(),
  milestoneClearedTicketKeys: z.array(z.string()).nullable().optional(),
});

export type TicketBulkMoveResult = z.infer<typeof ticketBulkMoveResultSchema>;

// ── Attachments ─────────────────────────────────────────────────────

/*
 * A ticket's files and its description images are both attachments of the
 * ticket (`entityType` TICKET) in the storage module. The limits mirror the
 * web's (`attachment-upload.tsx`, `markdown-editor.tsx`) and the server's
 * multipart settings, so a refusal is explained before the upload instead of
 * arriving as an opaque 413.
 */

/** The types the attachments section accepts: PNG, JPEG and PDF, as on the web. */
export const TICKET_ATTACHMENT_TYPES: readonly string[] = [
  "image/png",
  "image/jpeg",
  "image/jpg",
  "application/pdf",
];

/**
 * The server's multipart limit, both per file and per request, so it caps
 * each staged file and the whole staged batch.
 */
export const TICKET_ATTACHMENT_MAX_BYTES = 12 * 1024 * 1024;

/** The largest image the description editor takes before resizing, as the web's editor. */
export const DESCRIPTION_IMAGE_MAX_BYTES = 10 * 1024 * 1024;

/** Description images wider than this are scaled down to it, which keeps a ticket light on mobile data. */
export const DESCRIPTION_IMAGE_MAX_WIDTH = 1200;

/**
 * 400 on an upload whose content is already stored: TICKET content is unique
 * across every ticket of the organization. `errorValues.fileName` names the file.
 */
export const ATTACHMENT_DUPLICATE_CODE = "STG_VAL_005";

/** 400 on an upload of an empty file; `errorValues.fileName` names the file. */
export const ATTACHMENT_EMPTY_FILE_CODE = "STG_VAL_002";

/** 413 on a request over the server's multipart limit. */
export const ATTACHMENT_TOO_LARGE_CODE = "GEN_API_008";

/** A ticket's attachments, oldest first, as `GET /storage/attachments` and the upload return them. */
export const ticketAttachmentListSchema = z.array(chatAttachmentResponseSchema);

/** The answer of `POST /storage/attachments/exists`. */
export const attachmentExistsSchema = z.object({ exists: z.boolean() });

export type AttachmentExists = z.infer<typeof attachmentExistsSchema>;

/** The `src` prefix of an image in the description that is not uploaded yet. */
export const PENDING_IMAGE_SRC_PREFIX = "kazan-pending:";

/**
 * An image added to a description and not saved with it yet. `src` is
 * `kazan-pending:{key}`, the tag the editor holds until the image is
 * uploaded; it outlives the editor's WebView, unlike a `blob:` URL.
 * `uploaded` is set once the upload succeeded, so a save that failed later
 * does not upload the image again.
 */
export interface PendingImage {
  key: string;
  file: ChatLocalFile;
  src: string;
  alt: string;
  width: number;
  height: number;
  uploaded?: { id: string; url: string };
}
