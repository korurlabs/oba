import {
  createLoader,
  parseAsArrayOf,
  parseAsInteger,
  parseAsString,
  parseAsStringEnum,
} from "nuqs/server";
import type { Values } from "nuqs";
import {
  CUSTOMER_STAGES,
  LEAD_STAGES,
  PipelineStage,
  RELATIONS_PAGE_SIZE,
  RelationType,
  type RelationQueryRequest,
} from "../schemas";

/** The relation types in the order the type filter offers them. */
export const RELATION_TYPES = [
  RelationType.LEAD,
  RelationType.CUSTOMER,
  RelationType.SUPPLIER,
] as const satisfies readonly RelationType[];

/**
 * The stages the stage filter offers, grouped by the type that has them. A
 * supplier has no stage, so it has no group.
 */
export const RELATION_STAGE_GROUPS: readonly {
  type: RelationType;
  stages: readonly PipelineStage[];
}[] = [
  { type: RelationType.LEAD, stages: LEAD_STAGES },
  { type: RelationType.CUSTOMER, stages: CUSTOMER_STAGES },
];

/**
 * The list's state in the URL, so a filtered list can be reloaded and
 * shared. Server-safe: the list page parses the same parsers to prefetch
 * under the key the client builds.
 */
export const relationFilterParams = {
  /** The types listed; none means every type. */
  type: parseAsArrayOf(parseAsStringEnum(Object.values(RelationType))),
  search: parseAsString,
  /** The stages listed, of any type; none hides lost and parked leads. */
  stage: parseAsArrayOf(parseAsStringEnum(Object.values(PipelineStage))),
  owner: parseAsString,
  /** The id of the event the relations were met at. */
  event: parseAsString,
  /** The id of the campaign the relations came from. */
  campaign: parseAsString,
  page: parseAsInteger.withDefault(1),
};

export type RelationFilterParams = Values<typeof relationFilterParams>;

export const loadRelationFilterParams = createLoader(relationFilterParams);

/**
 * The query the list sends for its URL state. Absent filters are left out,
 * so the client and the server prefetch build the same key. Without a stage
 * the list hides lost and parked leads, which nobody works any more; picking
 * stages lists exactly those, whatever the types.
 */
export function toRelationQuery(filters: RelationFilterParams): RelationQueryRequest {
  const query: RelationQueryRequest = {
    page: Math.max(1, filters.page),
    amount: RELATIONS_PAGE_SIZE,
  };
  const types = filters.type ?? [];
  if (types.length > 0) query.type = types;
  const stages = filters.stage ?? [];
  if (stages.length > 0) {
    query.stage = stages;
  } else {
    query.hideInactiveLeads = true;
  }
  const search = filters.search?.trim();
  if (search) query.search = search;
  if (filters.owner) query.ownerUserId = filters.owner;
  if (filters.event) query.eventId = filters.event;
  if (filters.campaign) query.campaignId = filters.campaign;
  return query;
}

/** True when the list is narrowed by anything other than its page. */
export function hasActiveRelationFilter(filters: RelationFilterParams): boolean {
  return (
    !!filters.search ||
    !!filters.owner ||
    !!filters.event ||
    !!filters.campaign ||
    (filters.type ?? []).length > 0 ||
    (filters.stage ?? []).length > 0
  );
}
