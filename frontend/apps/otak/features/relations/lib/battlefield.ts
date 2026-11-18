import type { QueryClient } from "@tanstack/react-query";
import type { ApiResponse } from "@oba/shared/schema";
import { RELATION_KEYS } from "../hooks/query-keys";
import {
  CUSTOMER_STAGES,
  INACTIVE_LEAD_STAGES,
  OPEN_LEAD_STAGES,
  PipelineStage,
  RelationType,
  type RelationListItem,
} from "../schemas";

/** The battlefield's answer as the query cache holds it: the envelope with every lead and customer. */
export type BattlefieldResponse = ApiResponse<RelationListItem[]>;

/** What a move changed, so a refused move can be put back. */
export interface BattlefieldMoveContext {
  relationId: string;
  /** The stage the card had before the move. */
  previousStage: PipelineStage;
  /** The stage the card was moved to. */
  stage: PipelineStage;
}

/**
 * The columns of the battlefield, left to right, every one always shown: the
 * stages a lead is worked in, then the customers (won, direct), then the leads
 * nobody works any more. Nothing ever leaves the battlefield.
 */
export const BATTLEFIELD_COLUMNS = [
  ...OPEN_LEAD_STAGES,
  ...CUSTOMER_STAGES,
  ...INACTIVE_LEAD_STAGES,
] as const satisfies readonly PipelineStage[];

/**
 * The stages a lead card's select offers: every lead stage and CLOSED, which
 * makes the lead a customer. Never DIRECT, which is only for a customer that
 * never was a lead.
 */
export const LEAD_CARD_STAGES = [
  ...OPEN_LEAD_STAGES,
  PipelineStage.CLOSED,
  ...INACTIVE_LEAD_STAGES,
] as const satisfies readonly PipelineStage[];

/** Why a card may not go to a column; the key of its message under `battlefield.dropRefused`. */
export type BattlefieldDropRefusal = "notLead" | "directNotForLead";

/**
 * Whether a card may be moved to a stage, decided before any request so a
 * refused drop sends nothing. Only a lead moves, and never to DIRECT; a
 * customer card cannot be dragged, so "notLead" only guards a stale drag. The
 * server refuses the same moves on its own.
 */
export function battlefieldDropRefusal(
  relation: Pick<RelationListItem, "type">,
  stage: PipelineStage
): BattlefieldDropRefusal | null {
  if (relation.type !== RelationType.LEAD) return "notLead";
  if (stage === PipelineStage.DIRECT) return "directNotForLead";
  return null;
}

/** The type a relation has at a stage: a customer stage makes it a customer, any other keeps it a lead. */
export function battlefieldTypeOf(stage: PipelineStage): RelationType {
  return (CUSTOMER_STAGES as readonly PipelineStage[]).includes(stage)
    ? RelationType.CUSTOMER
    : RelationType.LEAD;
}

/** The DOM id of a card's stage select, so focus can find the card again after a move. */
export function battlefieldStageSelectId(relationId: string): string {
  return `battlefield-stage-${relationId}`;
}

/** The DOM id of a card's name link, the focus target of a card that has no select. */
export function battlefieldCardLinkId(relationId: string): string {
  return `battlefield-link-${relationId}`;
}

/** A card moved with its stage select; focus follows it to its new column. */
export interface StageSelectMove {
  relationId: string;
  stage: PipelineStage;
}

/**
 * Where focus goes after a move made with a card's stage select. The card
 * leaves its column, which takes the focused select away with it, so focus
 * goes to the card's select in the new column, or to its name link when the
 * move made it a customer, whose card has no select. "wait" while the
 * battlefield does not show the move yet; "none" when the card is no longer on
 * the battlefield.
 */
export function stageSelectFocusTarget(
  relations: readonly RelationListItem[],
  move: StageSelectMove
): "wait" | "select" | "link" | "none" {
  const relation = relations.find((candidate) => candidate.id === move.relationId);
  if (!relation) return "none";
  if (relation.stage !== move.stage) return "wait";
  return relation.type === RelationType.LEAD ? "select" : "link";
}

/**
 * The relations of each stage, in the order the server sent them (by name). A
 * relation without a stage has no column.
 */
export function groupBattlefieldByStage(
  leads: readonly RelationListItem[]
): Map<PipelineStage, RelationListItem[]> {
  const columns = new Map<PipelineStage, RelationListItem[]>();
  for (const lead of leads) {
    if (lead.stage === null) continue;
    const column = columns.get(lead.stage);
    if (column) {
      column.push(lead);
    } else {
      columns.set(lead.stage, [lead]);
    }
  }
  return columns;
}

/**
 * The expected value of a column: the sum of its leads' expected values, or
 * null when none of them has one, so a column of unpriced leads shows no
 * misleading zero. Summed in cents, because values carry two decimals and
 * adding them as floats would show a total like 0.30000000000000004.
 */
export function columnValueTotal(leads: readonly RelationListItem[]): number | null {
  let cents = 0;
  let priced = false;
  for (const lead of leads) {
    if (lead.expectedValue === null) continue;
    cents += Math.round(lead.expectedValue * 100);
    priced = true;
  }
  return priced ? cents / 100 : null;
}

/**
 * The battlefield with one card at another stage, and with the type that stage
 * gives: a lead moved to CLOSED is a customer at once, and moved back it is a
 * lead again, so a refused conversion rolls back whole. The card keeps its
 * place in the list, so it lands in its column at its place by name. Returns
 * the same battlefield when the card is not on it.
 */
export function moveRelationOnBattlefield(
  battlefield: BattlefieldResponse,
  relationId: string,
  stage: PipelineStage
): BattlefieldResponse {
  const leads = battlefield.data;
  if (!leads?.some((lead) => lead.id === relationId)) {
    return battlefield;
  }
  return {
    ...battlefield,
    data: leads.map((lead) =>
      lead.id === relationId ? { ...lead, stage, type: battlefieldTypeOf(stage) } : lead
    ),
  };
}

/** The stage a card has on the cached battlefield, or null when the battlefield or the card is not cached. */
export function battlefieldStageOf(
  queryClient: QueryClient,
  organizationId: string,
  relationId: string
): PipelineStage | null {
  const battlefield = queryClient.getQueryData<BattlefieldResponse>(
    RELATION_KEYS.battlefield(organizationId)
  );
  return battlefield?.data?.find((lead) => lead.id === relationId)?.stage ?? null;
}

/**
 * Moves the card in the cached battlefield before the server answers, so the battlefield
 * shows the move at once. Returns what to undo, or null when the card is not
 * on the cached battlefield or already at that stage.
 */
export function applyBattlefieldMove(
  queryClient: QueryClient,
  organizationId: string,
  relationId: string,
  stage: PipelineStage
): BattlefieldMoveContext | null {
  const previousStage = battlefieldStageOf(queryClient, organizationId, relationId);
  if (previousStage === null || previousStage === stage) {
    return null;
  }
  queryClient.setQueryData<BattlefieldResponse>(
    RELATION_KEYS.battlefield(organizationId),
    (battlefield) =>
      battlefield ? moveRelationOnBattlefield(battlefield, relationId, stage) : battlefield
  );
  return { relationId, previousStage, stage };
}

/**
 * Puts a refused move back. Only this card changes, and only while it still
 * sits where this move put it: a later move of the same card, or of any other
 * card, is kept.
 */
export function rollbackBattlefieldMove(
  queryClient: QueryClient,
  organizationId: string,
  context: BattlefieldMoveContext
): void {
  if (battlefieldStageOf(queryClient, organizationId, context.relationId) !== context.stage) {
    return;
  }
  queryClient.setQueryData<BattlefieldResponse>(
    RELATION_KEYS.battlefield(organizationId),
    (battlefield) =>
      battlefield
        ? moveRelationOnBattlefield(battlefield, context.relationId, context.previousStage)
        : battlefield
  );
}
