import { isMissionRank } from "@/components/kaizen/ticketMetaModel";
import {
  commitPendingImages,
  MissingPendingImageError,
  pendingImageSrcs,
  rewritePendingImageTag,
  type UploadedImage,
} from "@/components/ticket/descriptionImagesModel";
import {
  BACKLOG_PLACEMENT,
  descriptionTooLong,
  statusForPlacement,
  titleProblem,
  type TitleProblem,
} from "@/components/ticket/newTicketModel";
import { dueDateInstant } from "@/components/ticket/dueDateModel";
import { assigneesFor, type AssigneeCandidate } from "@/components/ticket/ticketActionsModel";
import {
  labelsFor,
  milestoneFor,
  projectFor,
  type TicketField,
  type TicketFieldsValue,
  type TicketHeaderValue,
} from "@/components/ticket/ticketFieldsModel";
import {
  ticketAttachmentErrorMessage,
  type TicketAttachmentMessage,
  type TicketAttachmentMessageKey,
} from "@/components/ticket/ticketAttachmentsModel";
import { hasKaizenAccess } from "@/lib/kaizenAccess";
import type { BoardMember, BoardOption, Milestone, Sprint } from "@/lib/schemas/boards";
import type { ChatLocalFile } from "@/lib/schemas/chat";
import {
  PENDING_IMAGE_SRC_PREFIX,
  TICKET_DESCRIPTION_MAX,
  type PendingImage,
} from "@/lib/schemas/tickets";

/*
 * How a new ticket reads on the ticket screen before it exists: the draft
 * shown through the same header and field list as a saved ticket, the fields
 * it cannot be created without, the create request's refused fields placed
 * under those rows or the header, and what Create does once the ticket
 * exists: the description images and staged files that need its id. No
 * React, so `bun test` covers them.
 */

/** A choice that belongs to one board, so it falls back to the default when the board changes. */
export interface PerBoard<T> {
  boardId: string;
  value: T;
}

/** The choice when it was made on `boardId`, else `fallback`. */
export function onBoard<T>(choice: PerBoard<T> | null, boardId: string | null, fallback: T): T {
  return choice && choice.boardId === boardId ? choice.value : fallback;
}

/** The values of a new ticket as the screen holds them. */
export interface TicketDraftValues {
  title: string;
  type: string;
  priority: string;
  status: string;
  missionRank: string;
  /** `BACKLOG_PLACEMENT` or a sprint id. */
  placement: string;
  assigneeIds: readonly string[];
  labelIds: readonly string[];
  milestoneId: string | null;
  projectId: string | null;
  /** `YYYY-MM-DD`, or null without a due date. */
  dueDay: string | null;
  isSubTicket: boolean;
}

/** The board lists the draft's ids are looked up in; an empty list while one loads. */
export interface TicketDraftOptions {
  members: readonly BoardMember[];
  labels: readonly BoardOption[];
  sprints: readonly Sprint[];
  milestones: readonly Milestone[];
  projects: readonly BoardOption[];
}

/**
 * The draft as the ticket screen's header and field list read it. Ids whose
 * names have not loaded yet are left out until they arrive; a backlog ticket
 * shows Todo, the only status the API keeps there. A value that is not a
 * mission rank shows as no rank, so the required rank chip reads as missing
 * rather than as a size nobody picked.
 */
export function draftTicketView(
  draft: TicketDraftValues,
  options: TicketDraftOptions
): { header: TicketHeaderValue; fields: TicketFieldsValue } {
  const status: string = statusForPlacement(draft.status, draft.placement, draft.isSubTicket);
  const candidates: AssigneeCandidate[] = options.members.map((member) => ({
    userId: member.userId,
    firstName: member.firstName,
    lastName: member.lastName,
    email: member.email,
    role: member.role,
    kaizenAccess: hasKaizenAccess(member),
  }));
  const known: Set<string> = new Set(candidates.map((candidate) => candidate.userId));
  const missionRank: string | null = isMissionRank(draft.missionRank) ? draft.missionRank : null;
  const sprint: Sprint | undefined =
    draft.placement === BACKLOG_PLACEMENT
      ? undefined
      : options.sprints.find((item) => item.id === draft.placement);
  return {
    header: {
      type: draft.type,
      title: draft.title,
      status,
      priority: draft.priority,
      missionRank,
    },
    fields: {
      status,
      assignees: assigneesFor(
        draft.assigneeIds.filter((id) => known.has(id)),
        candidates,
        []
      ),
      labels: labelsFor(draft.labelIds, options.labels, []),
      sprint: sprint ? { id: sprint.id, name: sprint.name, status: sprint.status } : null,
      milestone: milestoneFor(draft.milestoneId, options.milestones),
      project: draft.projectId ? projectFor(draft.projectId, options.projects) : null,
      dueDate: dueDateInstant(draft.dueDay),
    },
  };
}

/** The request fields of `TicketCreateRequest` that a field row shows. */
const ROW_OF_FIELD: Record<string, TicketField> = {
  assigneeIds: "assignees",
  labelIds: "labels",
  sprintId: "sprint",
  backlogId: "sprint",
  milestoneId: "milestone",
  projectId: "project",
  dueDate: "dueDate",
};

/** A refused create request, sorted by where each message shows. */
export interface CreateFieldErrors {
  title: string | null;
  description: string | null;
  /** The message under the header's chips, for the mission rank chip. */
  missionRank: string | null;
  /** Messages shown under a field row. */
  rows: Partial<Record<TicketField, string>>;
  /** Messages of fields without a row of their own, such as the type, for a toast. */
  others: string[];
}

/** No refused field. */
export const NO_CREATE_ERRORS: CreateFieldErrors = {
  title: null,
  description: null,
  missionRank: null,
  rows: {},
  others: [],
};

/**
 * Sorts the field errors of a refused create request: the title and the
 * description under their fields, the mission rank under the header's chips,
 * the planning fields under their rows, and the rest (type, priority, status)
 * into one list for a toast.
 */
export function createFieldErrors(fields: Readonly<Record<string, string>>): CreateFieldErrors {
  const result: CreateFieldErrors = {
    title: null,
    description: null,
    missionRank: null,
    rows: {},
    others: [],
  };
  for (const [field, message] of Object.entries(fields)) {
    const row: TicketField | undefined = ROW_OF_FIELD[field];
    if (field === "title") result.title = message;
    else if (field === "description") result.description = message;
    else if (field === "missionRank") result.missionRank = message;
    else if (row) result.rows[row] = result.rows[row] ? `${result.rows[row]}\n${message}` : message;
    else result.others.push(message);
  }
  return result;
}

/** What a new ticket is checked for before Create sends it. */
export interface DraftChecked {
  title: string;
  description: string;
  missionRank: string;
}

/**
 * Why the draft cannot be sent yet, per field. The title and the mission rank
 * are required, as the web form marks them and the API refuses a ticket
 * without them; the type, priority and status always hold a value.
 */
export interface DraftProblems {
  title: TitleProblem | null;
  missionRank: "required" | null;
  description: "too-long" | null;
}

/** The problems of the draft, or null when Create can send it. */
export function draftProblems(draft: DraftChecked): DraftProblems | null {
  const problems: DraftProblems = {
    title: titleProblem(draft.title),
    missionRank: isMissionRank(draft.missionRank) ? null : "required",
    description: descriptionTooLong(draft.description) ? "too-long" : null,
  };
  return problems.title || problems.missionRank || problems.description ? problems : null;
}

/** A field of the draft that can hold a problem, in the order the screen shows them. */
export type DraftProblemField = keyof DraftProblems;

const PROBLEM_ORDER: readonly DraftProblemField[] = ["title", "missionRank", "description"];

/** The field the screen brings into view first: the topmost one with a problem. */
export function firstProblemField(problems: DraftProblems): DraftProblemField | null {
  return PROBLEM_ORDER.find((field) => problems[field] !== null) ?? null;
}

/**
 * The names of the staged files whose content is already attached to a
 * ticket. The server refuses duplicate ticket content across the whole
 * organization, and the files upload only after the ticket exists, so the
 * web checks first and does not create the ticket when one is a duplicate.
 * A check that fails counts as "not known to be a duplicate", as on the web:
 * it is reported through `onCheckFailed` and the upload after Create then
 * meets the server's own refusal, which the result toast names.
 */
export async function duplicateAttachmentNames(
  files: readonly ChatLocalFile[],
  exists: (file: ChatLocalFile) => Promise<boolean>,
  onCheckFailed: (file: ChatLocalFile, error: unknown) => void
): Promise<string[]> {
  const names: string[] = [];
  for (const file of files) {
    try {
      if (await exists(file)) names.push(file.name);
    } catch (error) {
      onCheckFailed(file, error);
    }
  }
  return names;
}

/** Thrown when the description outgrows the API's limit once the image links replace the temporary tags. */
export class DescriptionTooLongError extends Error {
  constructor(readonly length: number) {
    super(`The description is ${length} characters with its image links`);
    this.name = "DescriptionTooLongError";
  }
}

/** An `<img>` tag whose image is not uploaded, alone on its paragraph line or inline. */
const PENDING_TAG = `<img\\b[^>]*?\\ssrc="${PENDING_IMAGE_SRC_PREFIX}[^"]*"[^>]*>`;
const PENDING_TAG_PARAGRAPH = new RegExp(
  `(^|\\n+)[ \\t]*${PENDING_TAG}[ \\t]*(\\n\\n|\\n?$)`,
  "gi"
);
const PENDING_TAG_INLINE = new RegExp(PENDING_TAG, "gi");

/**
 * The description without the tags of images that are not uploaded. A tag
 * that was a paragraph of its own goes with its blank line, so the text
 * reads as if the image had never been added.
 */
export function withoutPendingImages(markdown: string): string {
  let previous: string;
  let next: string = markdown;
  // A removed paragraph shares its blank line with the next one, so repeat until none is left.
  do {
    previous = next;
    next = next.replace(PENDING_TAG_PARAGRAPH, (_tag: string, before: string, after: string) =>
      before === "" || after === "" ? "" : "\n\n"
    );
  } while (next !== previous);
  return next.replace(PENDING_TAG_INLINE, "");
}

/**
 * The description to save when its images could not all be added: the
 * uploaded ones in place, the others left out. A temporary tag shows no
 * image anywhere, on the web or here, so it is never saved. When the links
 * of the uploaded images make the text too long, they are left out too; the
 * files then show in the ticket's attachments.
 */
export function partialCreatedDescription(
  markdown: string,
  images: readonly PendingImage[],
  uploaded: ReadonlyMap<string, UploadedImage>
): string {
  let committed: string = markdown;
  for (const image of images) {
    const stored: UploadedImage | undefined = image.uploaded ?? uploaded.get(image.key);
    if (stored) committed = rewritePendingImageTag(committed, image, stored);
  }
  committed = withoutPendingImages(committed);
  return committed.length > TICKET_DESCRIPTION_MAX ? withoutPendingImages(markdown) : committed;
}

/** What the new ticket's description needs once the ticket exists. */
export interface CreatedDescriptionOptions {
  /** The description as Create sent it, with the temporary tags of its images. */
  markdown: string;
  /** The draft's pending images. */
  images: readonly PendingImage[];
  /** Uploads one image to the new ticket; rejects on failure. */
  upload: (image: PendingImage) => Promise<UploadedImage>;
  /** Saves the description of the new ticket; rejects on failure. */
  save: (description: string) => Promise<void>;
}

/** How the description images of a new ticket fared; both null when every image is in place. */
export interface CreatedDescriptionOutcome {
  /** Why an image could not be uploaded or placed, or null. */
  imageError: unknown;
  /** Why the description could not be saved again, or null; its temporary tags then stay. */
  saveError: unknown;
}

/**
 * Uploads the description images of a ticket just created and saves the
 * description again with their links, as the web does after its create.
 * When an image fails, the description is still saved, with the uploaded
 * images and without the rest, so no temporary tag stays in the ticket.
 * Nothing is sent when the description holds no pending image.
 */
export async function commitCreatedDescription({
  markdown,
  images,
  upload,
  save,
}: CreatedDescriptionOptions): Promise<CreatedDescriptionOutcome> {
  if (pendingImageSrcs(markdown).length === 0) return { imageError: null, saveError: null };
  const uploaded: Map<string, UploadedImage> = new Map<string, UploadedImage>();
  let imageError: unknown = null;
  let description: string;
  try {
    description = await commitPendingImages({
      markdown,
      images,
      upload,
      onUploaded: (key, stored) => uploaded.set(key, stored),
    });
    if (description.length > TICKET_DESCRIPTION_MAX) {
      throw new DescriptionTooLongError(description.length);
    }
  } catch (error) {
    imageError = error;
    description = partialCreatedDescription(markdown, images, uploaded);
  }
  try {
    await save(description);
    return { imageError, saveError: null };
  } catch (error) {
    return { imageError, saveError: error };
  }
}

/** The result toast's title after Create, by the follow-up steps that failed. */
export type NewTicketResultKey =
  | "newTicketCreated"
  | "newTicketCreatedImagesFailed"
  | "newTicketCreatedAttachmentsFailed"
  | "newTicketCreatedImagesAndAttachmentsFailed";

/** Which result toast Create shows: success, or a warning naming what the ticket lacks. */
export function newTicketResultKey(
  imagesFailed: boolean,
  attachmentsFailed: boolean
): NewTicketResultKey {
  if (imagesFailed && attachmentsFailed) return "newTicketCreatedImagesAndAttachmentsFailed";
  if (imagesFailed) return "newTicketCreatedImagesFailed";
  if (attachmentsFailed) return "newTicketCreatedAttachmentsFailed";
  return "newTicketCreated";
}

/** A reason a follow-up step failed, as a message key with its values. */
export interface NewTicketFollowUpMessage {
  key:
    | TicketAttachmentMessageKey
    | "descriptionImageMissing"
    | "ticketDescriptionTooLongAfterImages";
  params: { name?: string; max?: number };
}

/** Why a description image could not be added, in the words of the description sheet. */
export function imageFailureMessage(error: unknown): NewTicketFollowUpMessage {
  if (error instanceof MissingPendingImageError) {
    return { key: "descriptionImageMissing", params: {} };
  }
  if (error instanceof DescriptionTooLongError) {
    return { key: "ticketDescriptionTooLongAfterImages", params: { max: TICKET_DESCRIPTION_MAX } };
  }
  const message: TicketAttachmentMessage = ticketAttachmentErrorMessage(error, "upload");
  return { key: message.key, params: message.params };
}
