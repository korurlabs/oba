import type { AttachmentResponse } from "@oba/shared/features/attachments/schemas";
import type { DocumentType } from "@/features/document-templates/schemas";
import {
  DOCUMENT_LIMITS,
  PICKER_SIZE,
  type DocumentListItem,
  type DocumentQueryRequest,
} from "@/features/documents/schemas";
import { apiErrorText } from "@/lib/api-error-text";

/** Refusals of a document snapshot that have a text of their own. */
const SNAPSHOT_ERROR_TEXT_KEYS: Partial<Record<string, string>> = {
  DOC_NOT_002: "errors.documentNotFound",
  OTK_NOT_011: "errors.mailNotFound",
  DOC_VAL_003: "errors.noPdf",
  DOC_VAL_004: "errors.otherRelation",
  OTK_STA_011: "errors.mailSent",
  STG_VAL_005: "errors.duplicate",
  APP_SEC_001: "errors.noOtakAccess",
  APP_SEC_002: "errors.forbidden",
  ORG_SEC_003: "errors.forbidden",
};

/**
 * The text of a document that could not be attached to a mail: a
 * translated text for the refusals the user can act on, else the server's
 * message, else `fallbackKey`. `t` is the `outreaches.mailAttachments`
 * translator.
 */
export function documentSnapshotErrorText(
  error: unknown,
  t: (key: string) => string,
  fallbackKey: string
): string {
  return apiErrorText(error, t, fallbackKey, SNAPSHOT_ERROR_TEXT_KEYS);
}

/**
 * The document query of the mail's document select: the first documents of
 * the mail's relation, narrowed by a title search and a type.
 */
export function snapshotPickerQuery(
  relationId: string,
  search: string,
  type: DocumentType | null
): DocumentQueryRequest {
  const query: DocumentQueryRequest = { relationId, page: 1, amount: PICKER_SIZE };
  const trimmed = search.trim().slice(0, DOCUMENT_LIMITS.searchMax);
  if (trimmed) query.search = trimmed;
  if (type) query.type = type;
  return query;
}

/** The documents a mail can carry: only one with both files has the PDF a snapshot copies. */
export function attachableDocuments(documents: readonly DocumentListItem[]): DocumentListItem[] {
  return documents.filter((document) => document.ready);
}

/** True for a file copied from a document, which the mail lists under its documents. */
export function isDocumentSnapshot(attachment: Pick<AttachmentResponse, "documentId">): boolean {
  return attachment.documentId !== null && attachment.documentId !== undefined;
}
