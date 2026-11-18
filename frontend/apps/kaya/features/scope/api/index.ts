import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type {
  EffectiveItemResponse,
  EffectiveQuery,
  PackCreateRequest,
  PackDetailResponse,
  PackResponse,
  PackUpdateRequest,
  RepositoryPackResponse,
} from "../schemas";

/** Every pack of the organization, by name; not paged. */
export async function listPacks() {
  const res = await api.get<ApiResponse<PackResponse[]>>(API_ROUTES.packs.list);
  return res.data;
}

/**
 * Creates a root pack, or a child pack of `parentPackId`; owners only. The
 * new pack is empty and has no version until a first one is approved.
 */
export async function createPack(data: PackCreateRequest) {
  const res = await api.post<ApiResponse<PackResponse>>(API_ROUTES.packs.list, data);
  return res.data;
}

/** One pack with the repositories that compose it, each with its mount folder and newest release. */
export async function getPack(packId: string) {
  const res = await api.get<ApiResponse<PackDetailResponse>>(API_ROUTES.packs.detail(packId));
  return res.data;
}

/**
 * Sets a pack's name and description; owners only. The server refuses a new
 * name (409) once a repository composes the pack or a version of it is
 * approved; a description change alone is always accepted.
 */
export async function updatePack(packId: string, data: PackUpdateRequest) {
  const res = await api.patch<ApiResponse<PackResponse>>(API_ROUTES.packs.detail(packId), data);
  return res.data;
}

/** The packs a repository uses, required ones first, with its newest release. */
export async function getRepositoryPacks(repositoryId: string) {
  const res = await api.get<ApiResponse<RepositoryPackResponse>>(
    API_ROUTES.repositoryPacks.list(repositoryId)
  );
  return res.data;
}

/**
 * A pack's items at one of its own versions or drafts (default its current
 * version), sorted by path: its own items with their override status, and
 * with `inherited` also the items it inherits from the nearest ancestor that
 * gives each one.
 */
export async function getPackEffective(packId: string, query: EffectiveQuery) {
  const params: Record<string, string> = { inherited: String(query.inherited) };
  if (query.versionId !== undefined) params.versionId = query.versionId;
  const res = await api.get<ApiResponse<EffectiveItemResponse[]>>(
    API_ROUTES.packs.effective(packId),
    { params }
  );
  return res.data;
}
