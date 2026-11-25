"use client";

import { useCallback, useMemo } from "react";
import { useTranslations } from "next-intl";
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { cn } from "@/lib/utils";
import {
  KanbanColumnHeader,
  KanbanColumnHeaderSkeleton,
  KanbanEmptyColumn,
} from "@/features/tickets/components/kanban-column";
import {
  DroppableColumn,
  KANBAN_DRAG_ACTIVATION_DISTANCE,
  KANBAN_DROP_ANIMATION,
  kanbanCollisionDetection,
  useDropHandoff,
  type DropSlotResolver,
} from "@/features/tickets/lib/kanban-config";
import { CHECK_STATUSES, CheckStatusSchema, type Check, type CheckStatus } from "../schemas";
import { CheckOutcomeChip } from "./check-outcome-chip";

/**
 * The column a drop lands in: the column itself, the column of the card it
 * was dropped on, or null when it was dropped outside every column.
 */
export function resolveDropStatus(
  overId: string,
  overContainerId: unknown,
  checks: Check[]
): CheckStatus | null {
  const column = CheckStatusSchema.safeParse(overId);
  if (column.success) return column.data;
  const container = CheckStatusSchema.safeParse(overContainerId);
  if (container.success) return container.data;
  return checks.find((check) => check.id === overId)?.status ?? null;
}

interface CheckKanbanCardProps {
  check: Check;
  /** False for a reader who may not change the status, and while this check's save runs. */
  canDrag: boolean;
  isOverlay?: boolean;
}

/**
 * A check on the board, compact: name, category, weight and outcome, on the
 * surface and blue hover edge of Kazan's ticket card. A card that can move is
 * picked up with the pointer or, from keyboard focus, with Space or Enter and
 * moved with the arrow keys.
 */
function CheckKanbanCard({ check, canDrag, isOverlay = false }: CheckKanbanCardProps) {
  const t = useTranslations("cyberscore");
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: check.id,
    disabled: !canDrag || isOverlay,
  });

  // The sortable transition moves the cards around the gap; on the dragged
  // card itself it would fight the transform dnd-kit writes every frame.
  const style = {
    transform: CSS.Transform.toString(transform),
    transition: isDragging ? undefined : (transition ?? undefined),
    willChange: transform ? "transform" : undefined,
  };

  return (
    <div
      ref={isOverlay ? undefined : setNodeRef}
      style={isOverlay ? undefined : style}
      {...(canDrag && !isOverlay ? { ...attributes, ...listeners } : {})}
      className={cn(
        "kazan-card border-border relative flex flex-col gap-3 rounded-none border p-[14px]",
        "transition-[border-color,box-shadow,opacity] duration-150 ease-in-out",
        "hover:border-primary/50 focus-visible:ring-ring outline-none focus-visible:ring-2",
        canDrag && "cursor-grab active:cursor-grabbing",
        isDragging && "opacity-50 shadow-xl"
      )}
    >
      <span className="kazan-grid-label truncate">
        {t(`categories.${check.category ?? "NONE"}`)}
      </span>
      <h4 className="text-foreground line-clamp-3 text-sm leading-snug font-medium break-words">
        {check.name}
      </h4>
      <div className="flex items-center justify-between gap-2">
        <CheckOutcomeChip outcome={check.outcome} />
        <span
          className="kazan-grid-number h-5 w-auto min-w-5 px-1"
          title={t("checks.columns.weight")}
          aria-label={`${t("checks.columns.weight")}: ${check.weight}`}
        >
          {check.weight}
        </span>
      </div>
    </div>
  );
}

interface CheckKanbanProps {
  checks: Check[];
  isLoading: boolean;
  canManage: boolean;
  savingCheckIds: ReadonlySet<string>;
  /** Saves the new status; the promise settles when the list shows it, or on a failure. */
  onStatusChange: (check: Check, status: CheckStatus) => Promise<unknown>;
}

/**
 * The checks as a board with a column per status. An organization admin
 * drags a card to another column to change its status, with the drag and
 * drop of Kazan's ticket boards; every other reader sees the board without
 * drag handles. A card stays in the column it was dropped on while its save
 * runs, and a failure puts it back and toasts.
 */
export function CheckKanban({
  checks,
  isLoading,
  canManage,
  savingCheckIds,
  onStatusChange,
}: CheckKanbanProps) {
  const t = useTranslations("cyberscore");

  const resolveSlot = useCallback<DropSlotResolver<CheckStatus>>(
    (checkId) => checks.find((check) => check.id === checkId)?.status ?? null,
    [checks]
  );
  const { activeCard, pendingDrop, beginDrag, releaseDrag, handOffTo } = useDropHandoff<
    Check,
    CheckStatus
  >(resolveSlot);

  const pendingId = pendingDrop?.id;
  const pendingStatus = pendingDrop?.slot;
  const displayChecks = useMemo(() => {
    if (!pendingId || !pendingStatus) return checks;
    return checks.map((check) =>
      check.id === pendingId ? { ...check, status: pendingStatus } : check
    );
  }, [checks, pendingId, pendingStatus]);

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: KANBAN_DRAG_ACTIVATION_DISTANCE },
    }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const handleDragStart = (event: DragStartEvent) => {
    beginDrag(checks.find((check) => check.id === event.active.id) ?? null);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    const check = checks.find((candidate) => candidate.id === active.id);
    if (!over || !check) {
      releaseDrag();
      return;
    }
    const status = resolveDropStatus(
      String(over.id),
      over.data.current?.sortable?.containerId,
      checks
    );
    if (!status || status === check.status) {
      releaseDrag();
      return;
    }
    handOffTo(check.id, status, onStatusChange(check, status));
  };

  if (isLoading) {
    return (
      <div className="overflow-x-auto">
        <div className="grid min-w-[880px] grid-cols-4 gap-4" aria-busy="true">
          {CHECK_STATUSES.map((status) => (
            <div key={status} className="flex flex-col gap-3">
              <KanbanColumnHeaderSkeleton />
              <div className="border-border bg-card dark:bg-muted h-28 animate-pulse border" />
              <div className="border-border bg-card dark:bg-muted h-28 animate-pulse border" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={kanbanCollisionDetection}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onDragCancel={releaseDrag}
    >
      <div className="overflow-x-auto pb-2">
        {/* Each column is as tall as its own cards, so an empty column shows
            its message at the top rather than halfway down the longest one. */}
        <div className="grid min-w-[880px] grid-cols-4 items-start gap-4">
          {CHECK_STATUSES.map((status, index) => {
            const columnChecks = displayChecks.filter((check) => check.status === status);
            return (
              <section
                key={status}
                aria-label={t(`statuses.${status}`)}
                className="flex min-h-[480px] min-w-0 flex-col gap-3"
              >
                <KanbanColumnHeader
                  index={index + 1}
                  title={t(`statuses.${status}`)}
                  count={columnChecks.length}
                />
                <SortableContext
                  id={status}
                  items={columnChecks.map((check) => check.id)}
                  strategy={verticalListSortingStrategy}
                >
                  <DroppableColumn
                    id={status}
                    isOver={activeCard !== null && activeCard.status !== status}
                  >
                    {columnChecks.length === 0 ? (
                      <KanbanEmptyColumn message={t("checks.kanban.emptyColumn")} />
                    ) : (
                      columnChecks.map((check) => (
                        <CheckKanbanCard
                          key={check.id}
                          check={check}
                          canDrag={canManage && !savingCheckIds.has(check.id)}
                        />
                      ))
                    )}
                  </DroppableColumn>
                </SortableContext>
              </section>
            );
          })}
        </div>
      </div>

      <DragOverlay dropAnimation={KANBAN_DROP_ANIMATION}>
        {activeCard ? <CheckKanbanCard check={activeCard} canDrag isOverlay /> : null}
      </DragOverlay>
    </DndContext>
  );
}
