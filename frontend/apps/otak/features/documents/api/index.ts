import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import { documentFilesFormData } from "@/features/document-templates/api";
import type { DocumentTemplateFiles } from "@/features/document-templates/schemas";
import type {
  DocumentCreateRequest,
  DocumentDetail,
  DocumentListApiResponse,
  DocumentQueryRequest,
  DocumentUpdateRequest,
  LegalEntityListItem,
} from "../schemas";

/**
 * One page of the organization's documents, last changed first. Needs Otak
 * access; a relation filter of another organization answers `APP_SEC_002`.
 */
export async function listDocuments(params: DocumentQueryRequest) {
  const res = await api.get<DocumentListApiResponse>(API_ROUTES.documents.list, { params });
  return res.data;
}

/** One document with its snapshots; the file URLs are signed and short-lived. */
export async function getDocument(documentId: string) {
  const res = await api.get<ApiResponse<DocumentDetail>>(API_ROUTES.documents.detail(documentId));
  return res.data;
}

/**
 * Creates a document of a relation. A template bound to another legal entity
 * answers `DOC_VAL_002`; a legal entity of another organization `LGE_NOT_001`.
 */
export async function createDocument(data: DocumentCreateRequest) {
  const res = await api.post<ApiResponse<DocumentDetail>>(API_ROUTES.documents.list, data);
  return res.data;
}

/** Replaces the title, type, language and legal entity; the files stay. */
export async function updateDocument(documentId: string, data: DocumentUpdateRequest) {
  const res = await api.put<ApiResponse<DocumentDetail>>(
    API_ROUTES.documents.detail(documentId),
    data
  );
  return res.data;
}

/**
 * Replaces both files of a document in one multipart request with the parts
 * `html` and `pdf`. Snapshots already attached to mails keep their copy. A
 * part that is not HTML or PDF answers `DOC_VAL_001`.
 */
export async function replaceDocumentFiles(documentId: string, files: DocumentTemplateFiles) {
  const res = await api.put<ApiResponse<DocumentDetail>>(
    API_ROUTES.documents.files(documentId),
    documentFilesFormData(files),
    { headers: { "Content-Type": "multipart/form-data" } }
  );
  return res.data;
}

/** Removes the document and its files; the snapshots on mails stay. */
export async function deleteDocument(documentId: string): Promise<void> {
  await api.delete(API_ROUTES.documents.detail(documentId));
}

/** Every legal entity of the organization, by short name; unpaged. */
export async function listLegalEntities() {
  const res = await api.get<ApiResponse<LegalEntityListItem[]>>(API_ROUTES.legalEntities.list);
  return res.data;
}
