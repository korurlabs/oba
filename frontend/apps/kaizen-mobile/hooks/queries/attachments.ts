import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { StorageEntityType } from "@oba/shared/features/attachments/schemas";

import { api, readData } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import type { ChatAttachmentResponse, ChatLocalFile } from "@/lib/schemas/chat";
import { attachmentExistsSchema, ticketAttachmentListSchema } from "@/lib/schemas/tickets";

/*
 * The storage endpoints for a ticket's attachments: its files and its
 * description images. Every request is silent, because each caller shows its
 * own message for a failure (ticketAttachmentsModel.ts maps the error to it);
 * the response interceptor still logs every failure and rejects it.
 */

const SILENT = { _silentErrors: true } as const;

/** A file without a known type goes up as bytes; the server stores what it sniffs. */
const UNKNOWN_FILE_TYPE = "application/octet-stream";

/**
 * A JSON part of a multipart body. React Native's FormData sends a
 * `{ string, type }` part with that content type, which a plain string part
 * would not carry; the server reads `metadata` only as `application/json`.
 */
function jsonPart(value: unknown): Blob {
  return { string: JSON.stringify(value), type: "application/json" } as unknown as Blob;
}

/** A file part streamed from the file on the device. */
function filePart(file: ChatLocalFile): Blob {
  return {
    uri: file.uri,
    name: file.name,
    type: file.mimeType ?? UNKNOWN_FILE_TYPE,
  } as unknown as Blob;
}

/**
 * `POST /storage/attachments`: uploads files to a ticket in one request and
 * returns the stored attachments in the order sent. `metadata` is a JSON
 * array with one entry per file, and each entry's `fileName` equals the name
 * of its `files` part (the server refuses a mismatch). The upload is all or
 * nothing: one refused file fails the request. `onProgress` gets the share
 * uploaded, 0 to 1. No files sends nothing.
 */
export async function uploadTicketAttachments(
  ticketId: string,
  files: readonly ChatLocalFile[],
  onProgress?: (share: number) => void
): Promise<ChatAttachmentResponse[]> {
  if (files.length === 0) return [];
  const formData = new FormData();
  formData.append(
    "metadata",
    jsonPart(
      files.map((file) => ({
        fileName: file.name,
        entityType: StorageEntityType.TICKET,
        entityId: ticketId,
      }))
    )
  );
  for (const file of files) {
    formData.append("files", filePart(file));
  }
  const response = await api.post(API_ROUTES.storage.attachments, formData, {
    headers: { "Content-Type": "multipart/form-data" },
    ...SILENT,
    onUploadProgress: onProgress
      ? (event) => {
          if (event.total) onProgress(Math.min(1, event.loaded / event.total));
        }
      : undefined,
  });
  return readData(ticketAttachmentListSchema, response.data);
}

/**
 * `GET /storage/attachments`: every attachment of a ticket, oldest first,
 * description images included. Each `url` is signed for 30 minutes.
 */
export async function listTicketAttachments(ticketId: string): Promise<ChatAttachmentResponse[]> {
  const response = await api.get(API_ROUTES.storage.attachments, {
    ...SILENT,
    params: { entityType: StorageEntityType.TICKET, entityId: ticketId },
  });
  return readData(ticketAttachmentListSchema, response.data);
}

/**
 * `POST /storage/attachments/exists`: whether the file's content is already
 * stored on any ticket of the organization, which an upload would refuse.
 * New ticket asks before it creates the ticket, so a refusal cannot leave a
 * ticket without its files. Unlike the upload, `metadata` is one JSON object
 * without `entityId`, and the file part is `file`.
 */
export async function ticketAttachmentExists(file: ChatLocalFile): Promise<boolean> {
  const formData = new FormData();
  formData.append(
    "metadata",
    jsonPart({ fileName: file.name, entityType: StorageEntityType.TICKET })
  );
  formData.append("file", filePart(file));
  const response = await api.post(API_ROUTES.storage.attachmentsExists, formData, {
    headers: { "Content-Type": "multipart/form-data" },
    ...SILENT,
  });
  return readData(attachmentExistsSchema, response.data).exists;
}

/** `DELETE /storage/attachments/{id}`: deletes the file and its record. */
export async function deleteAttachment(attachmentId: string): Promise<void> {
  await api.delete(API_ROUTES.storage.attachment(attachmentId), SILENT);
}

/** Query keys of attachments, scoped by organization. */
export const attachmentKeys = {
  all: (orgId: string) => ["attachments", orgId] as const,
  list: (orgId: string, ticketId: string) =>
    [...attachmentKeys.all(orgId), "ticket", ticketId] as const,
};

/**
 * Marks a ticket's attachment list stale, for a writer that calls the client
 * functions directly, such as the description save that uploads images and
 * deletes the ones removed.
 */
export function invalidateTicketAttachments(
  queryClient: QueryClient,
  orgId: string | null,
  ticketId: string
): Promise<void> | undefined {
  if (!orgId) return undefined;
  return queryClient.invalidateQueries({ queryKey: attachmentKeys.list(orgId, ticketId) });
}

/**
 * A ticket's attachments. Every mount reads the server again: the web, the
 * CLI and the description save change the list without touching this cache.
 * A failure is the query's error state, which the section shows with Retry.
 */
export function useTicketAttachments(ticketId?: string) {
  const orgId: string | null = useActiveOrganizationId();
  return useQuery<ChatAttachmentResponse[]>({
    queryKey: attachmentKeys.list(orgId ?? "", ticketId ?? ""),
    queryFn: () => listTicketAttachments(ticketId!),
    enabled: !!orgId && !!ticketId,
    refetchOnMount: "always",
  });
}

export interface TicketAttachmentsUpload {
  ticketId: string;
  files: readonly ChatLocalFile[];
  onProgress?: (share: number) => void;
}

/**
 * Uploads staged files to a ticket. The caller shows the result: the error
 * reaches it through `mutate`'s callbacks or `mutateAsync`'s rejection.
 */
export function useUploadTicketAttachments() {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<ChatAttachmentResponse[], Error, TicketAttachmentsUpload>({
    mutationFn: ({ ticketId, files, onProgress }) =>
      uploadTicketAttachments(ticketId, files, onProgress),
    onSuccess: (created, { ticketId }) => {
      if (!orgId) return;
      queryClient.setQueryData<ChatAttachmentResponse[]>(
        attachmentKeys.list(orgId, ticketId),
        (current) => (current ? [...current, ...created] : current)
      );
    },
    onError: (error) => console.warn("[attachments] upload failed", error.message),
    onSettled: (_data, _error, { ticketId }) =>
      invalidateTicketAttachments(queryClient, orgId, ticketId),
  });
}

/**
 * Deletes one attachment of a ticket and drops it from the list at once. The
 * caller shows the result.
 */
export function useDeleteAttachment(ticketId: string) {
  const queryClient: QueryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  return useMutation<void, Error, string>({
    mutationFn: (attachmentId) => deleteAttachment(attachmentId),
    onSuccess: (_data, attachmentId) => {
      if (!orgId) return;
      queryClient.setQueryData<ChatAttachmentResponse[]>(
        attachmentKeys.list(orgId, ticketId),
        (current) => current?.filter((attachment) => attachment.id !== attachmentId)
      );
    },
    onError: (error) => console.warn("[attachments] delete failed", error.message),
    onSettled: () => invalidateTicketAttachments(queryClient, orgId, ticketId),
  });
}
