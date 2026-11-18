import { isApiError } from "@/lib/api";
import type { ChatAttachmentResponse, ChatLocalFile } from "@/lib/schemas/chat";
import {
  ATTACHMENT_DUPLICATE_CODE,
  ATTACHMENT_EMPTY_FILE_CODE,
  ATTACHMENT_TOO_LARGE_CODE,
  TICKET_ATTACHMENT_MAX_BYTES,
  TICKET_ATTACHMENT_TYPES,
} from "@/lib/schemas/tickets";

/*
 * How a ticket's attachments are staged, listed and explained, without
 * React or native modules, so the rules can be tested alone. The rules are
 * the web attachments section's (`attachment-upload.tsx`, `attachment-list.tsx`).
 */

/** The size limit in the messages, in MB. */
export const TICKET_ATTACHMENT_MAX_MB: number = TICKET_ATTACHMENT_MAX_BYTES / (1024 * 1024);

/**
 * The message keys this model hands to the screen. The screen translates
 * them; each must exist in every locale.
 */
export type TicketAttachmentMessageKey =
  | "ticketAttachmentTypeNotAllowed"
  | "ticketAttachmentFileTooLarge"
  | "ticketAttachmentBatchTooLarge"
  | "ticketAttachmentsDuplicate"
  | "ticketAttachmentsEmptyFile"
  | "ticketAttachmentsTooLarge"
  | "ticketAttachmentsForbidden"
  | "ticketAttachmentsNotFound"
  | "ticketAttachmentsNetwork"
  | "ticketAttachmentsLoadFailed"
  | "ticketAttachmentsUploadFailed"
  | "ticketAttachmentsDeleteFailed";

/** A message for the user: its key and the values it interpolates. */
export interface TicketAttachmentMessage {
  key: TicketAttachmentMessageKey;
  params: { name?: string; max?: number };
}

const EXTENSION_TYPES: Readonly<Record<string, string>> = {
  png: "image/png",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  pdf: "application/pdf",
};

/**
 * The media type of a picked file, lower case and without parameters; from
 * the extension when the picker gave none. Null when neither tells it.
 */
export function ticketAttachmentType(
  file: Pick<ChatLocalFile, "name" | "mimeType">
): string | null {
  const given: string = (file.mimeType ?? "").split(";")[0]?.trim().toLowerCase() ?? "";
  if (given) return given;
  const dot: number = file.name.lastIndexOf(".");
  if (dot < 0) return null;
  return EXTENSION_TYPES[file.name.slice(dot + 1).toLowerCase()] ?? null;
}

/** Why a picked file is not staged. */
export type TicketAttachmentRefusal = "type" | "fileTooLarge" | "batchTooLarge";

export interface TicketAttachmentsStaging {
  /** The picked files that may be staged, each with its media type filled in. */
  accepted: ChatLocalFile[];
  /** One message per refused file, naming it, in the order picked. */
  refused: TicketAttachmentMessage[];
}

const REFUSAL_KEYS: Readonly<Record<TicketAttachmentRefusal, TicketAttachmentMessageKey>> = {
  type: "ticketAttachmentTypeNotAllowed",
  fileTooLarge: "ticketAttachmentFileTooLarge",
  batchTooLarge: "ticketAttachmentBatchTooLarge",
};

/**
 * Splits newly picked files into those that may join `staged` and those
 * refused: a type other than PNG, JPEG or PDF, a file over 12 MB, or a file
 * that would take the staged batch over 12 MB (one upload request carries
 * the whole batch). A file of unknown size counts as nothing; the server
 * still refuses an oversized request with a 413.
 */
export function ticketAttachmentsProblem(
  staged: readonly ChatLocalFile[],
  picked: readonly ChatLocalFile[]
): TicketAttachmentsStaging {
  const accepted: ChatLocalFile[] = [];
  const refused: TicketAttachmentMessage[] = [];
  let batchBytes: number = staged.reduce((sum, file) => sum + (file.size ?? 0), 0);
  for (const file of picked) {
    const type: string | null = ticketAttachmentType(file);
    const size: number = file.size ?? 0;
    let refusal: TicketAttachmentRefusal | null = null;
    if (type === null || !TICKET_ATTACHMENT_TYPES.includes(type)) refusal = "type";
    else if (size > TICKET_ATTACHMENT_MAX_BYTES) refusal = "fileTooLarge";
    else if (batchBytes + size > TICKET_ATTACHMENT_MAX_BYTES) refusal = "batchTooLarge";
    if (refusal !== null) {
      refused.push({
        key: REFUSAL_KEYS[refusal],
        params: { name: file.name, max: TICKET_ATTACHMENT_MAX_MB },
      });
      continue;
    }
    batchBytes += size;
    accepted.push({ ...file, mimeType: type });
  }
  return { accepted, refused };
}

/** The request whose failure is explained. */
export type TicketAttachmentOperation = "list" | "upload" | "delete";

const FALLBACK_KEYS: Readonly<Record<TicketAttachmentOperation, TicketAttachmentMessageKey>> = {
  list: "ticketAttachmentsLoadFailed",
  upload: "ticketAttachmentsUploadFailed",
  delete: "ticketAttachmentsDeleteFailed",
};

/** The file a storage refusal names in `errorValues.fileName`, if any. */
function refusedFileName(error: { errorValues: Record<string, unknown> }): string | undefined {
  const name: unknown = error.errorValues.fileName;
  return typeof name === "string" && name !== "" ? name : undefined;
}

/**
 * The message for a failed attachment request: duplicate content (TICKET
 * content is unique across all tickets) and an empty file name the file; a
 * 413, a 403, a 404 and a request that got no answer have their own text;
 * anything else gets the operation's general failure.
 */
export function ticketAttachmentErrorMessage(
  error: unknown,
  operation: TicketAttachmentOperation
): TicketAttachmentMessage {
  if (!isApiError(error)) return { key: FALLBACK_KEYS[operation], params: {} };
  if (error.code === ATTACHMENT_DUPLICATE_CODE) {
    return { key: "ticketAttachmentsDuplicate", params: { name: refusedFileName(error) } };
  }
  if (error.code === ATTACHMENT_EMPTY_FILE_CODE) {
    return { key: "ticketAttachmentsEmptyFile", params: { name: refusedFileName(error) } };
  }
  if (error.status === 413 || error.code === ATTACHMENT_TOO_LARGE_CODE) {
    return { key: "ticketAttachmentsTooLarge", params: { max: TICKET_ATTACHMENT_MAX_MB } };
  }
  if (error.kind === "forbidden") return { key: "ticketAttachmentsForbidden", params: {} };
  if (error.kind === "not-found") return { key: "ticketAttachmentsNotFound", params: {} };
  if (error.kind === "network") return { key: "ticketAttachmentsNetwork", params: {} };
  return { key: FALLBACK_KEYS[operation], params: {} };
}

/**
 * The `data-attachment-id` of every `<img>` in a description, in order, as
 * the web's `parseAttachmentIds` reads them. An image without the attribute
 * is skipped.
 */
export function parseAttachmentIds(description: string | null | undefined): string[] {
  if (!description) return [];
  const ids: string[] = [];
  for (const match of description.matchAll(/<img\b[^>]*\sdata-attachment-id="([^"]+)"/gi)) {
    const id: string | undefined = match[1];
    if (id !== undefined) ids.push(id);
  }
  return ids;
}

/**
 * The attachments the section lists: all but the images the description
 * shows, which the web leaves out of its list too.
 */
export function visibleTicketAttachments(
  attachments: readonly ChatAttachmentResponse[],
  description: string | null | undefined
): ChatAttachmentResponse[] {
  const embedded: ReadonlySet<string> = new Set(parseAttachmentIds(description));
  return attachments.filter((attachment) => !embedded.has(attachment.id));
}
