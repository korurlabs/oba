import {
  createLoader,
  parseAsArrayOf,
  parseAsInteger,
  parseAsString,
  parseAsStringEnum,
  parseAsStringLiteral,
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

/** The list's tabs, one per relation type, in the order they are shown. */
export const RELATION_TABS = ["leads", "customers", "suppliers"] as const;
export type RelationTab = (typeof RELATION_TABS)[number];

/** The relation type each tab lists. */
export const RELATION_TAB_TYPES: Record<RelationTab, RelationType> = {
  leads: RelationType.LEAD,
  customers: RelationType.CUSTOMER,
  suppliers: RelationType.SUPPLIER,
};

/** The tab that lists relations of a type, to return to after a delete. */
export function relationTabOf(type: RelationType): RelationTab {
  return RELATION_TABS.find((tab) => RELATION_TAB_TYPES[tab] === type) ?? "leads";
}

/** The stages the stage filter offers on a tab; suppliers have no stage and no filter. */
export const RELATION_TAB_STAGES: Record<RelationTab, readonly PipelineStage[]> = {
  leads: LEAD_STAGES,
  customers: CUSTOMER_STAGES,
  suppliers: [],
};

/** True when the tab lists relations with a stage, so it shows the stage column and filter. */
export function tabHasStage(tab: RelationTab): boolean {
  return RELATION_TAB_STAGES[tab].length > 0;
}

/**
 * The URL's stages that belong to the tab. A stage of another tab, as in a
 * link edited by hand, is dropped: the filter could not show it, and the
 * server would match nothing for it.
 */
function tabStages(filters: RelationFilterParams): PipelineStage[] {
  const offered = RELATION_TAB_STAGES[filters.tab];
  return (filters.stage ?? []).filter((stage) => offered.includes(stage));
}

/**
 * The list's state in the URL, so a filtered list can be reloaded and
 * shared. Server-safe: the list page parses the same parsers to prefetch
 * under the key the client builds.
 */
export const relationFilterParams = {
  tab: parseAsStringLiteral(RELATION_TABS).withDefault("leads"),
  search: parseAsString,
  stage: parseAsArrayOf(parseAsStringEnum(Object.values(PipelineStage))),
  owner: parseAsString,
  /** The id of the event the relations were met at. */
  event: parseAsString,
  page: parseAsInteger.withDefault(1),
};

export type RelationFilterParams = Values<typeof relationFilterParams>;

export const loadRelationFilterParams = createLoader(relationFilterParams);

/**
 * The query the list sends for its URL state. Absent filters are left out,
 * so the client and the server prefetch build the same key. The stage
 * filter applies to leads and customers.
 */
export function toRelationQuery(filters: RelationFilterParams): RelationQueryRequest {
  const type = RELATION_TAB_TYPES[filters.tab];
  const query: RelationQueryRequest = {
    type,
    page: Math.max(1, filters.page),
    amount: RELATIONS_PAGE_SIZE,
  };
  const search = filters.search?.trim();
  if (search) query.search = search;
  if (filters.owner) query.ownerUserId = filters.owner;
  if (filters.event) query.eventId = filters.event;
  const stages = tabStages(filters);
  if (stages.length > 0) query.stage = stages;
  return query;
}

/** True when the list is narrowed by anything other than its tab and page. */
export function hasActiveRelationFilter(filters: RelationFilterParams): boolean {
  return !!filters.search || !!filters.owner || !!filters.event || tabStages(filters).length > 0;
}
