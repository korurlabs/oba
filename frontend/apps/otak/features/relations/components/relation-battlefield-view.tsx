"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { AlertTriangle, Plus } from "lucide-react";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { cn } from "@oba/ui/lib/utils";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { EmptyState, LoadError } from "@oba/ui/sky/page-states";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { useChangeRelationStage, useRelationBattlefield } from "../hooks/use-relations";
import {
  BATTLEFIELD_COLUMNS,
  battlefieldCardLinkId,
  battlefieldDropRefusal,
  battlefieldStageSelectId,
  columnValueTotal,
  groupBattlefieldByStage,
  stageSelectFocusTarget,
  type StageSelectMove,
} from "../lib/battlefield";
import { relationErrorText } from "../lib/relation-error";
import {
  BATTLEFIELD_MAX_LEADS,
  PipelineStage,
  RelationType,
  type RelationListItem,
} from "../schemas";
import { RelationBattlefieldCard } from "./relation-battlefield-card";

/** A stage column: a pale sky lane with rounded corners, the cards white on it. */
const BATTLEFIELD_COLUMN_CLASS =
  "bg-muted/80 rounded-xl border p-3 transition-colors md:w-72 md:shrink-0";

/**
 * The battlefield: a column for every stage, always shown, with every lead and
 * customer in it, so nothing leaves it. A lead card moves at once and goes
 * back with a message when the server refuses the move; a move to Closed (won)
 * makes the lead a customer and says so. A move the battlefield refuses (a
 * lead onto Direct) sends nothing and says why. Every move is announced to
 * screen readers, and a move made with a card's stage select keeps focus on
 * that card.
 */
export function RelationBattlefieldView() {
  const t = useTranslations("relations");
  const tErrors = useTranslations("errors");
  const format = useFormatter();
  const battlefield = useRelationBattlefield();
  const changeStage = useChangeRelationStage();
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [dropStage, setDropStage] = useState<PipelineStage | null>(null);
  const [announcement, setAnnouncement] = useState("");
  // A ref, not state: it is only read once the battlefield shows the move.
  const stageSelectMove = useRef<StageSelectMove | null>(null);

  const relations = useMemo(() => battlefield.data?.data ?? [], [battlefield.data?.data]);
  const relationsByStage = useMemo(() => groupBattlefieldByStage(relations), [relations]);
  const dragged = useMemo(
    () => relations.find((candidate) => candidate.id === draggingId) ?? null,
    [relations, draggingId]
  );

  // The moved card renders in another column, so the select that had focus is
  // gone; without this, focus falls to the page and the keyboard user is lost.
  useEffect(() => {
    const move = stageSelectMove.current;
    if (!move) return;
    const target = stageSelectFocusTarget(relations, move);
    if (target === "wait") return;
    stageSelectMove.current = null;
    if (target === "select") {
      document.getElementById(battlefieldStageSelectId(move.relationId))?.focus();
    } else if (target === "link") {
      document.getElementById(battlefieldCardLinkId(move.relationId))?.focus();
    }
  }, [relations]);

  const handleMove = useCallback(
    (relation: RelationListItem, stage: PipelineStage) => {
      if (relation.stage === stage) return;
      const refusal = battlefieldDropRefusal(relation, stage);
      if (refusal !== null) {
        // The card stays put, so there is no move for focus to follow.
        stageSelectMove.current = null;
        toast.info(t(`battlefield.dropRefused.${refusal}`, { name: relation.name }));
        return;
      }
      setAnnouncement(t("battlefield.moved", { name: relation.name, stage: t(`stages.${stage}`) }));
      changeStage.mutate(
        { relationId: relation.id, stage, name: relation.name },
        {
          onSettled: () => {
            // A move the battlefield never showed leaves nothing for focus to follow.
            const pending = stageSelectMove.current;
            if (pending?.relationId === relation.id && pending.stage === stage) {
              stageSelectMove.current = null;
            }
          },
        }
      );
    },
    [changeStage, t]
  );

  const handleStageSelect = useCallback(
    (relation: RelationListItem, stage: PipelineStage) => {
      if (relation.stage === stage) return;
      stageSelectMove.current = { relationId: relation.id, stage };
      handleMove(relation, stage);
    },
    [handleMove]
  );

  const endDrag = useCallback(() => {
    setDraggingId(null);
    setDropStage(null);
  }, []);

  const handleDrop = (stage: PipelineStage, relationId: string) => {
    const relation = relations.find((candidate) => candidate.id === relationId);
    if (relation) handleMove(relation, stage);
    endDrag();
  };

  return (
    <div className="space-y-6">
      <PageHeading
        title={t("battlefield.title")}
        description={t("battlefield.subtitle")}
        actions={
          <Button asChild>
            <Link href={{ pathname: "/relations/new", query: { type: RelationType.LEAD } }}>
              <Plus aria-hidden="true" />
              {t("battlefield.newLead")}
            </Link>
          </Button>
        }
      />

      <p className="sr-only" role="status" aria-live="polite">
        {announcement}
      </p>
      {battlefield.isError && battlefield.data !== undefined && (
        <p className="text-warning-text flex items-start gap-2 text-sm" role="status">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          {t("battlefield.reloadError")}
        </p>
      )}
      {relations.length >= BATTLEFIELD_MAX_LEADS && (
        <p className="text-muted-foreground text-sm" role="status">
          {t("battlefield.capped", { max: BATTLEFIELD_MAX_LEADS })}
        </p>
      )}

      {battlefield.isError && battlefield.data === undefined ? (
        <LoadError
          title={t("battlefield.loadError")}
          message={relationErrorText(battlefield.error, t, "errors.loadHint")}
          retryLabel={tErrors("tryAgain")}
          onRetry={() => void battlefield.refetch()}
        />
      ) : battlefield.data === undefined ? (
        <div className="flex flex-col gap-4 md:flex-row" aria-busy="true">
          <span className="sr-only">{t("battlefield.loading")}</span>
          {BATTLEFIELD_COLUMNS.map((stage) => (
            <div key={stage} className={cn(BATTLEFIELD_COLUMN_CLASS, "space-y-3")}>
              <div className="bg-card h-4 w-24 animate-pulse rounded-md" />
              <div className="bg-card h-24 animate-pulse rounded-lg" />
            </div>
          ))}
        </div>
      ) : relations.length === 0 ? (
        <EmptyState
          title={t("battlefield.emptyTitle")}
          description={t("battlefield.empty")}
          action={
            <Button asChild>
              <Link href={{ pathname: "/relations/new", query: { type: RelationType.LEAD } }}>
                <Plus aria-hidden="true" />
                {t("battlefield.newLead")}
              </Link>
            </Button>
          }
        />
      ) : (
        <div className="flex flex-col gap-4 md:flex-row md:items-start md:overflow-x-auto md:pb-2">
          {BATTLEFIELD_COLUMNS.map((stage) => {
            const columnRelations = relationsByStage.get(stage) ?? [];
            const columnTotal = columnValueTotal(columnRelations);
            const headingId = `battlefield-column-${stage}`;
            const hintId = `battlefield-column-hint-${stage}`;
            const isClosedColumn = stage === PipelineStage.CLOSED;
            return (
              <section
                key={stage}
                aria-labelledby={headingId}
                aria-describedby={isClosedColumn ? hintId : undefined}
                onDragOver={(event) => {
                  if (draggingId === null) return;
                  // The drop is still taken, so a refused one can say why; the
                  // column only does not light up as a place the card can go.
                  event.preventDefault();
                  event.dataTransfer.dropEffect = "move";
                  const accepts =
                    dragged !== null && battlefieldDropRefusal(dragged, stage) === null;
                  const next = accepts ? stage : null;
                  if (dropStage !== next) setDropStage(next);
                }}
                onDragLeave={(event) => {
                  // Leaving for a card inside the column is not leaving the column.
                  if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
                    setDropStage((current) => (current === stage ? null : current));
                  }
                }}
                onDrop={(event) => {
                  event.preventDefault();
                  handleDrop(stage, event.dataTransfer.getData("text/plain") || draggingId || "");
                }}
                className={cn(
                  BATTLEFIELD_COLUMN_CLASS,
                  dropStage === stage && "border-primary bg-accent"
                )}
              >
                <div className="mb-3 space-y-1 px-1">
                  <div className="flex items-center justify-between gap-2">
                    <h2 id={headingId} className="truncate text-sm font-semibold">
                      {t(`stages.${stage}`)}
                    </h2>
                    <Badge variant="secondary" className="tabular-nums" aria-hidden="true">
                      {columnRelations.length}
                    </Badge>
                    <span className="sr-only">
                      {t("battlefield.columnCount", { count: columnRelations.length })}
                    </span>
                  </div>
                  {isClosedColumn && (
                    <p id={hintId} className="text-muted-foreground text-xs">
                      {t("battlefield.closedHint")}
                    </p>
                  )}
                  {columnTotal !== null && (
                    <p className="text-muted-foreground text-xs tabular-nums">
                      {t("battlefield.columnTotal", {
                        value: format.number(columnTotal, { style: "currency", currency: "EUR" }),
                      })}
                    </p>
                  )}
                </div>
                {columnRelations.length === 0 ? (
                  <p className="text-muted-foreground py-4 text-center text-xs">
                    {t("battlefield.emptyColumn")}
                  </p>
                ) : (
                  <ul className="space-y-2">
                    {columnRelations.map((relation) => (
                      <RelationBattlefieldCard
                        key={relation.id}
                        relation={relation}
                        isDragging={draggingId === relation.id}
                        onDragStart={setDraggingId}
                        onDragEnd={endDrag}
                        onMove={handleStageSelect}
                      />
                    ))}
                  </ul>
                )}
              </section>
            );
          })}
        </div>
      )}
    </div>
  );
}
