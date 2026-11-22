import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  PipelineStage,
  RelationCreateRequest,
  RelationDetail,
  RelationAttachment,
  RelationListApiResponse,
  RelationListItem,
  RelationOwnerCandidate,
  RelationQueryRequest,
  RelationStageRequest,
  RelationType,
  RelationTypeRequest,
  RelationUpdateRequest,
} from "../schemas";

/**
 * One page of the organization's relations, of one type or of every type,
 * newest change first.
 * `stage` repeats as `?stage=NEW&stage=WARM`, the form the server binds; the
 * default `stage[]=` form would be dropped.
 */
export async function listRelations(params: RelationQueryRequest) {
  const res = await api.get<RelationListApiResponse>(API_ROUTES.relations.list, {
    params,
    paramsSerializer: { indexes: null },
  });
  return res.data;
}

/**
 * The files of the relation and of its mails, newest first. A relation of
 * another organization answers `OTK_NOT_001`.
 */
export async function listRelationAttachments(relationId: string) {
  const res = await api.get<ApiResponse<RelationAttachment[]>>(
    API_ROUTES.relations.attachments(relationId)
  );
  return res.data;
}

export async function getRelation(relationId: string) {
  const res = await api.get<ApiResponse<RelationDetail>>(API_ROUTES.relations.detail(relationId));
  return res.data;
}

/** A new LEAD starts at stage NEW, a new CUSTOMER at DIRECT; a supplier has no stage. */
export async function createRelation(data: RelationCreateRequest) {
  const res = await api.post<ApiResponse<RelationDetail>>(API_ROUTES.relations.list, data);
  return res.data;
}

/** A full update: a null or missing optional field is cleared. Type and stage stay. */
export async function updateRelation(relationId: string, data: RelationUpdateRequest) {
  const res = await api.put<ApiResponse<RelationDetail>>(
    API_ROUTES.relations.detail(relationId),
    data
  );
  return res.data;
}

/** Removes the relation with its contacts; ADMIN or OWNER only. */
export async function deleteRelation(relationId: string): Promise<void> {
  await api.delete(API_ROUTES.relations.detail(relationId));
}

/**
 * Every lead and customer of the organization for the battlefield, every
 * stage, ordered by name and not paged.
 */
export async function getRelationBattlefield() {
  const res = await api.get<ApiResponse<RelationListItem[]>>(API_ROUTES.relations.battlefield);
  return res.data;
}

/**
 * Every active member of the organization, each with whether they have Otak
 * access, for the owner select. Ordered by first name, last name and email.
 */
export async function listRelationOwnerCandidates() {
  const res = await api.get<ApiResponse<RelationOwnerCandidate[]>>(
    API_ROUTES.relations.ownerCandidates
  );
  return res.data;
}

/** Moves a lead to another stage; a customer or supplier is refused with `OTK_STA_002`. */
export async function changeRelationStage(relationId: string, stage: PipelineStage) {
  const body: RelationStageRequest = { stage };
  const res = await api.patch<ApiResponse<RelationDetail>>(
    API_ROUTES.relations.stage(relationId),
    body
  );
  return res.data;
}

/**
 * Gives the relation another type. A lead starts again at NEW; a lead that
 * becomes a customer gets CLOSED, a supplier that becomes one DIRECT; a
 * supplier has no stage.
 */
export async function changeRelationType(relationId: string, type: RelationType) {
  const body: RelationTypeRequest = { type };
  const res = await api.patch<ApiResponse<RelationDetail>>(
    API_ROUTES.relations.type(relationId),
    body
  );
  return res.data;
}
