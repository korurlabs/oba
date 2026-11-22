import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type { AttachmentResponse } from "@oba/shared/features/attachments/schemas";
import type { DocumentSnapshotRequest } from "../schemas";

/**
 * Stores a copy of the document's PDF as a new attachment of a mail, named
 * `<title>.pdf`. A document without a PDF answers `DOC_VAL_003`, a mail of
 * another relation `DOC_VAL_004`, a sent mail `OTK_STA_011`, a PDF the mail
 * already holds `STG_VAL_005`. The mail needs ADMIN or OWNER.
 */
export async function attachDocumentSnapshot(documentId: string, data: DocumentSnapshotRequest) {
  const res = await api.post<ApiResponse<AttachmentResponse>>(
    API_ROUTES.documents.attachments(documentId),
    data
  );
  return res.data;
}
