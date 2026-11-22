import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type { AttachmentResponse } from "@oba/shared/features/attachments/schemas";
import {
  HTML_MEDIA_TYPE,
  PDF_MEDIA_TYPE,
  type DocumentTemplateAttachmentRequest,
  type DocumentTemplateDetail,
  type DocumentTemplateFiles,
  type DocumentTemplateListApiResponse,
  type DocumentTemplateQueryRequest,
  type DocumentTemplateRequest,
} from "../schemas";

/** One page of the organization's templates, ordered by name, then language. */
export async function listDocumentTemplates(params: DocumentTemplateQueryRequest) {
  const res = await api.get<DocumentTemplateListApiResponse>(API_ROUTES.documentTemplates.list, {
    params,
  });
  return res.data;
}

/** One template with its content; the file URLs are signed and short-lived. */
export async function getDocumentTemplate(templateId: string) {
  const res = await api.get<ApiResponse<DocumentTemplateDetail>>(
    API_ROUTES.documentTemplates.detail(templateId)
  );
  return res.data;
}

/**
 * Creates a template without files. A name the organization already has in
 * this language, case ignored, is refused with `DOC_STA_001`. ADMIN or
 * OWNER only.
 */
export async function createDocumentTemplate(data: DocumentTemplateRequest) {
  const res = await api.post<ApiResponse<DocumentTemplateDetail>>(
    API_ROUTES.documentTemplates.list,
    data
  );
  return res.data;
}

/** Replaces every field, the type included; the files are kept. ADMIN or OWNER only. */
export async function updateDocumentTemplate(templateId: string, data: DocumentTemplateRequest) {
  const res = await api.put<ApiResponse<DocumentTemplateDetail>>(
    API_ROUTES.documentTemplates.detail(templateId),
    data
  );
  return res.data;
}

/**
 * The multipart body of a template's files upload: the parts `html` and `pdf`, each sent with its media type set, because the
 * server checks the declared type and a browser may give a picked file none.
 */
export function documentFilesFormData(files: DocumentTemplateFiles): FormData {
  const formData = new FormData();
  formData.append("html", new Blob([files.html], { type: HTML_MEDIA_TYPE }), files.html.name);
  formData.append("pdf", new Blob([files.pdf], { type: PDF_MEDIA_TYPE }), files.pdf.name);
  return formData;
}

/**
 * Replaces both files of a template in one multipart request with the parts
 * `html` and `pdf`. A part that is not HTML or PDF answers `DOC_VAL_001` with
 * the part in `errorValues`. ADMIN or OWNER only.
 */
export async function replaceDocumentTemplateFiles(
  templateId: string,
  files: DocumentTemplateFiles
) {
  const res = await api.put<ApiResponse<DocumentTemplateDetail>>(
    API_ROUTES.documentTemplates.files(templateId),
    documentFilesFormData(files),
    { headers: { "Content-Type": "multipart/form-data" } }
  );
  return res.data;
}

/**
 * Stores a copy of the template's PDF as a new attachment of a mail or a
 * relation, named after the template. A template without its PDF answers
 * `DOC_VAL_002`, a PDF already on the target `STG_VAL_005`, a sent mail
 * `OTK_STA_011`. A mail needs ADMIN or OWNER, a relation any member.
 */
export async function copyDocumentTemplateToAttachment(
  templateId: string,
  data: DocumentTemplateAttachmentRequest
) {
  const res = await api.post<ApiResponse<AttachmentResponse>>(
    API_ROUTES.documentTemplates.attachments(templateId),
    data
  );
  return res.data;
}

/** Removes the template and its files; attachments copied from it stay. ADMIN or OWNER only. */
export async function deleteDocumentTemplate(templateId: string): Promise<void> {
  await api.delete(API_ROUTES.documentTemplates.detail(templateId));
}
