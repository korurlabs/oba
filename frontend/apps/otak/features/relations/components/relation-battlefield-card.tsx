"use client";

import { useFormatter, useTranslations } from "next-intl";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { cn } from "@oba/ui/lib/utils";
import { NativeSelect } from "@oba/ui/sky/field";
import {
  battlefieldCardLinkId,
  battlefieldStageSelectId,
  LEAD_CARD_STAGES,
} from "../lib/battlefield";
import {
  PipelineStage,
  PipelineStageSchema,
  RelationType,
  type RelationListItem,
} from "../schemas";
import { RelationStageBadge } from "./relation-stage-badge";

function isPipelineStage(value: string): value is PipelineStage {
  return PipelineStageSchema.safeParse(value).success;
}

interface RelationBattlefieldCardProps {
  relation: RelationListItem;
  isDragging: boolean;
  onDragStart: (relationId: string) => void;
  onDragEnd: () => void;
  onMove: (relation: RelationListItem, stage: PipelineStage) => void;
}

/**
 * A relation on the battlefield: name, owner, expected value and
 * do-not-contact. A lead moves by drag and drop, or by its stage select, which
 * is the way on a touch screen and from the keyboard; picking Closed (won)
 * makes it a customer. A customer's stage is set by Otak, so its card cannot
 * be dragged and shows the stage as a badge instead of a select.
 */
export function RelationBattlefieldCard({
  relation,
  isDragging,
  onDragStart,
  onDragEnd,
  onMove,
}: RelationBattlefieldCardProps) {
  const t = useTranslations("relations");
  const format = useFormatter();
  const isLead = relation.type === RelationType.LEAD;

  return (
    <li
      draggable={isLead}
      onDragStart={
        isLead
          ? (event) => {
              event.dataTransfer.setData("text/plain", relation.id);
              event.dataTransfer.effectAllowed = "move";
              onDragStart(relation.id);
            }
          : undefined
      }
      onDragEnd={isLead ? onDragEnd : undefined}
      className={cn(
        "bg-card space-y-2 rounded-lg border p-3 shadow-xs transition-shadow hover:shadow-md",
        isLead && "md:cursor-grab",
        isDragging && "opacity-50"
      )}
    >
      <Link
        id={battlefieldCardLinkId(relation.id)}
        href={`/relations/${relation.id}`}
        draggable={false}
        className="text-foreground focus-visible:ring-ring block truncate rounded-sm text-sm font-medium hover:underline focus-visible:ring-2 focus-visible:outline-none"
      >
        {relation.name}
      </Link>
      <div className="text-muted-foreground flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
        <span className="truncate">{relation.owner?.name ?? t("noOwner")}</span>
        {relation.expectedValue !== null && (
          <span className="tabular-nums">
            {format.number(relation.expectedValue, { style: "currency", currency: "EUR" })}
          </span>
        )}
      </div>
      {isLead ? (
        <NativeSelect
          id={battlefieldStageSelectId(relation.id)}
          value={relation.stage ?? ""}
          aria-label={t("battlefield.stageOf", { name: relation.name })}
          onChange={(event) => {
            const stage = event.target.value;
            if (isPipelineStage(stage)) onMove(relation, stage);
          }}
          className="h-8 w-full text-xs"
        >
          {LEAD_CARD_STAGES.map((stage) => (
            <option key={stage} value={stage}>
              {stage === PipelineStage.CLOSED ? t("battlefield.closeOption") : t(`stages.${stage}`)}
            </option>
          ))}
        </NativeSelect>
      ) : (
        relation.stage !== null && <RelationStageBadge stage={relation.stage} />
      )}
    </li>
  );
}
