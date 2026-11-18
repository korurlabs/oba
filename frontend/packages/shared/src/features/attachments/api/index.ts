import { api } from "../../../api/client";
import type { ApiResponse } from "../../../schema";
import { ATTACHMENT_ROUTES } from "../constants/api-routes";
import type { AttachmentResponse, StorageEntityType } from "../schemas";

export async function uploadAttachments(
  entityType: StorageEntityType,
  entityId: string,
  files: File[],
) {
  const metadata = files.map((file) => ({
    fileName: file.name,
    entityType,
    entityId,
  }));

  const formData = new FormData();
  formData.append(
    "metadata",
    new Blob([JSON.stringify(metadata)], { type: "application/json" }),
  );
  files.forEach((file) => {
    formData.append("files", file);
  });

  const res = await api.post<ApiResponse<AttachmentResponse[]>>(
    ATTACHMENT_ROUTES.upload,
    formData,
    {
      headers: { "Content-Type": "multipart/form-data" },
    },
  );
  return res.data;
}

export async function listAttachments(
  entityType: StorageEntityType,
  entityId: string,
) {
  const res = await api.get<ApiResponse<AttachmentResponse[]>>(
    ATTACHMENT_ROUTES.list,
    { params: { entityType, entityId } },
  );
  return res.data;
}

export async function deleteAttachment(attachmentId: string): Promise<void> {
  await api.delete(ATTACHMENT_ROUTES.delete(attachmentId));
}

/**
 * Pre-flight duplicate check for a single candidate file.
 *
 * The server rejects content that already exists for this entity type within
 * the organization -- across ALL tickets, not just the one being edited. On
 * the CREATE path the ticket is saved BEFORE its attachments upload, so
 * discovering the rejection afterwards leaves a saved ticket whose files
 * failed and are gone. Checking first is what keeps that from happening.
 *
 * Note two differences from the upload body: `metadata` is a single JSON
 * OBJECT (not an array) and carries no entityId, and the file part is named
 * "file" (singular). Mirrors the kaizen CLI's `CheckAttachmentExists`.
 */
export async function checkAttachmentExists(
  entityType: StorageEntityType,
  file: File,
): Promise<boolean> {
  const formData = new FormData();
  formData.append(
    "metadata",
    new Blob([JSON.stringify({ fileName: file.name, entityType })], {
      type: "application/json",
    }),
  );
  formData.append("file", file);

  const res = await api.post<ApiResponse<{ exists: boolean }>>(
    ATTACHMENT_ROUTES.exists,
    formData,
    { headers: { "Content-Type": "multipart/form-data" } },
  );
  return res.data.data?.exists ?? false;
}
