import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type {
  DraftCreateRequest,
  DraftFileSaveRequest,
  DraftFileSaveResponse,
  DraftRebaseRequest,
  FileDiffResponse,
  ItemOverrideRequest,
  ItemOverrideResponse,
  VersionDetailResponse,
  VersionFileContentResponse,
  VersionResponse,
} from "../schemas";

/** A version with its rendered files, ordered by path. */
export async function getVersion(id: string) {
  const res = await api.get<ApiResponse<VersionDetailResponse>>(API_ROUTES.versions.detail(id));
  return res.data;
}

/** One file of a version; `path` is the exact stored path. */
export async function getVersionFile(versionId: string, path: string) {
  const res = await api.get<ApiResponse<VersionFileContentResponse>>(
    API_ROUTES.versions.file(versionId),
    { params: { path } }
  );
  return res.data;
}

/**
 * The changed paths of a version against `against`, or against its base when
 * absent; `path` limits the diff to that one file.
 */
export async function getVersionDiff(versionId: string, against?: string, path?: string) {
  const params: Record<string, string> = {};
  if (against !== undefined) params.against = against;
  if (path !== undefined) params.path = path;
  const res = await api.get<ApiResponse<FileDiffResponse[]>>(API_ROUTES.versions.diff(versionId), {
    params: Object.keys(params).length === 0 ? undefined : params,
  });
  return res.data;
}

/** Starts a ROLLBACK draft with a released version's files, based on the current version. */
export async function restoreVersion(versionId: string) {
  const res = await api.post<ApiResponse<VersionResponse>>(API_ROUTES.versions.restore(versionId));
  return res.data;
}

// ── Drafts (writes are for the draft's proposer only) ──────────────

/** A new draft of a pack, copying its current version. */
export async function createDraft(data: DraftCreateRequest) {
  const res = await api.post<ApiResponse<VersionResponse>>(API_ROUTES.drafts.create, data);
  return res.data;
}

/** Adds or replaces one file of a draft; the answer describes the stored file without its content. */
export async function saveDraftFile(draftId: string, data: DraftFileSaveRequest) {
  const res = await api.put<ApiResponse<DraftFileSaveResponse>>(
    API_ROUTES.drafts.files(draftId),
    data
  );
  return res.data;
}

/** Removes one file of a draft. */
export async function deleteDraftFile(draftId: string, path: string): Promise<void> {
  await api.delete(API_ROUTES.drafts.files(draftId), { params: { path } });
}

/** Deletes a draft that was never reviewed. */
export async function deleteDraft(draftId: string): Promise<void> {
  await api.delete(API_ROUTES.drafts.detail(draftId));
}

/** Moves a draft onto the current version; conflicts unresolved by the body answer 409 KAY_CFG_002. */
export async function rebaseDraft(draftId: string, data: DraftRebaseRequest = {}) {
  const res = await api.post<ApiResponse<VersionResponse>>(API_ROUTES.drafts.rebase(draftId), data);
  return res.data;
}

/**
 * Moves the base of the draft's override of an item to the version the
 * nearest ancestor gives now: Keep or Update after a parent update. Keep as
 * own after the parent removed the item is `clearOverride`. 409 KAY_CFG_032 when that ancestor
 * changed again, 422 KAY_CFG_031 for a locked item.
 */
export async function setOverrideBase(draftId: string, data: ItemOverrideRequest) {
  const res = await api.put<ApiResponse<ItemOverrideResponse>>(
    API_ROUTES.drafts.overrides(draftId),
    data
  );
  return res.data;
}

/**
 * Drops the draft's override record of an item the parent removed, so it
 * stays as a plain own item. Refused while an ancestor still gives the item;
 * deleting the draft's files is how an override is dropped then.
 */
export async function clearOverride(draftId: string, path: string): Promise<void> {
  await api.delete(API_ROUTES.drafts.overrides(draftId), { params: { path } });
}
