/**
 * SubTicketList Component
 *
 * Notion-style sub-ticket management
 * Sub-tickets are full tickets linked to parent
 * Features:
 * - Progress bar showing completion
 * - Click to open full ticket editor
 * - Checkbox for completion toggle
 * - Drag handle for reordering (pointer and keyboard); the list renders the
 *   order it is given and never sorts
 */

"use client";

import { useId, useState, useRef, useEffect } from "react";
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import type { TicketListResponse } from "@/features/tickets/schemas";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { TicketTypeBadge } from "./ticket-type-badge";
import { TicketPriorityIcon } from "./ticket-priority-icon";
import { Plus, Trash2, GripVertical, ExternalLink } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { useTranslations } from "next-intl";
import { devError } from "@oba/shared/helpers/dev-log";
import { BoardListWithChildrenResponse } from "@/features/boards/schemas";

interface SubTicketListProps {
  subTickets: TicketListResponse[];
  onToggleComplete?: (id: string, completed: boolean) => void;
  onDelete?: (id: string) => void;
  /** A row was dropped on a sibling: `activeId` takes the slot of `overId`. */
  onReorder?: (activeId: string, overId: string) => void;
  /** The order is being saved or reloaded: the handles stay visible but do not start a drag. */
  isReorderLocked?: boolean;
  /** Resolves to true when the sub-ticket was created, false when the call was a duplicate. */
  onCreate?: (title: string) => Promise<boolean>;
  onUpdateTitle?: (id: string, title: string) => Promise<void>;
  onOpenSubTicket?: (ticket: TicketListResponse) => void; // Open sub-ticket in full editor (will be cast to full type by parent)
  onRequestCreateSubTicket?: () => void; // Request to create via full dialog
  isCreating?: boolean;
  currentBoard: BoardListWithChildrenResponse | null;
  canEditTickets: boolean;
}

/** Pointer travel in px before a press on the handle becomes a drag. */
const DRAG_ACTIVATION_DISTANCE = 5;

export function SubTicketList({
  subTickets,
  onToggleComplete,
  onDelete,
  onReorder,
  isReorderLocked = false,
  onCreate,
  onUpdateTitle,
  onOpenSubTicket,
  onRequestCreateSubTicket,
  isCreating = false,
  currentBoard,
  canEditTickets,
}: SubTicketListProps) {
  const t = useTranslations("ticket.subTickets");
  const [isQuickAdding, setIsQuickAdding] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);
  // A stable id keeps dnd-kit's generated aria ids equal on server and client.
  const dndContextId = useId();

  const isSortable = canEditTickets && !!onReorder && subTickets.length > 1;
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: DRAG_ACTIVATION_DISTANCE },
    }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const handleDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    onReorder?.(String(active.id), String(over.id));
  };

  const completedCount = subTickets.filter((t) => t.status === "DONE").length;
  const totalCount = subTickets.length;
  const progressPercent = totalCount > 0 ? (completedCount / totalCount) * 100 : 0;

  useEffect(() => {
    if (isQuickAdding && inputRef.current) {
      inputRef.current.focus();
    }
  }, [isQuickAdding]);

  const handleQuickCreate = async () => {
    if (!newTitle.trim() || !onCreate) return;
    try {
      // Only a created sub-ticket clears the title. A repeated Enter while
      // the first create runs resolves to false and leaves it alone.
      if (await onCreate(newTitle.trim())) setNewTitle("");
    } catch (error) {
      // The create mutation's `onError` has already shown the error toast.
      // The title stays in the input, so the user can retry.
      devError("Sub-ticket quick create failed:", error);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      e.preventDefault();
      handleQuickCreate();
    } else if (e.key === "Escape") {
      setIsQuickAdding(false);
      setNewTitle("");
    }
  };

  const handleAddClick = () => {
    // If we have a full dialog callback, use it. Otherwise fall back to quick add.
    if (onRequestCreateSubTicket) {
      onRequestCreateSubTicket();
    } else {
      setIsQuickAdding(true);
    }
  };

  return (
    <Accordion type="single" collapsible defaultValue="sub-tickets">
      <AccordionItem value="sub-tickets" className="border-0">
        <AccordionTrigger className="hover:bg-muted rounded-lg px-2 py-2 transition-colors hover:no-underline">
          <div className="flex flex-1 items-center gap-3">
            <span className="text-foreground text-sm font-semibold">
              {t("title")} ({totalCount})
            </span>
            {totalCount > 0 && (
              <>
                <div className="bg-primary/10 h-1.5 max-w-[120px] flex-1 overflow-hidden">
                  <div
                    className="bg-primary h-full transition-all duration-300"
                    style={{ width: `${progressPercent}%` }}
                  />
                </div>
                <span className="text-muted-foreground font-mono text-xs whitespace-nowrap tabular-nums">
                  {completedCount}/{totalCount}
                </span>
              </>
            )}
          </div>
        </AccordionTrigger>
        <AccordionContent className="pt-2">
          <div className="space-y-3">
            {/* Sub-ticket items */}
            <DndContext
              id={dndContextId}
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragEnd={handleDragEnd}
            >
              <SortableContext
                items={subTickets.map((subTicket) => subTicket.id)}
                strategy={verticalListSortingStrategy}
                disabled={!isSortable}
              >
                <div className="space-y-1">
                  {subTickets.map((subTicket) => (
                    <SubTicketRow
                      key={subTicket.id}
                      subTicket={subTicket}
                      onToggleComplete={onToggleComplete}
                      onDelete={onDelete}
                      onUpdateTitle={onUpdateTitle}
                      onOpen={onOpenSubTicket}
                      canEditTickets={canEditTickets}
                      isSortable={isSortable}
                      isReorderLocked={isReorderLocked}
                      dragHandleLabel={t("dragHandle")}
                      lockedHandleLabel={t("reorderBusy")}
                    />
                  ))}
                </div>
              </SortableContext>
            </DndContext>

            {/* Quick add (if no full dialog available) or Add button */}
            {isQuickAdding && !onRequestCreateSubTicket ? (
              <div className="bg-muted -mx-2 flex items-center gap-2 px-2 py-1.5">
                <Checkbox disabled className="opacity-50" />
                <Input
                  ref={inputRef}
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  onKeyDown={handleKeyDown}
                  onBlur={() => {
                    if (!newTitle.trim()) setIsQuickAdding(false);
                  }}
                  placeholder={t("clickToAdd")}
                  className="h-7 border-0 bg-transparent px-0 text-sm shadow-none focus-visible:ring-0"
                  disabled={isCreating}
                />
              </div>
            ) : (
              canEditTickets && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={handleAddClick}
                  className="text-muted-foreground hover:text-foreground h-8 w-full justify-start gap-2"
                >
                  <Plus className="h-4 w-4" />
                  <span>{t("addNew")}</span>
                </Button>
              )
            )}
          </div>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  );
}

interface SubTicketRowProps {
  subTicket: TicketListResponse;
  onToggleComplete?: (id: string, completed: boolean) => void;
  onDelete?: (id: string) => void;
  onUpdateTitle?: (id: string, title: string) => Promise<void>;
  onOpen?: (ticket: TicketListResponse) => void;
  canEditTickets: boolean;
  /** False for viewers and for a list with nothing to reorder. */
  isSortable: boolean;
  /** True while a reorder is saving or reloading; the handle is then disabled. */
  isReorderLocked: boolean;
  dragHandleLabel: string;
  lockedHandleLabel: string;
}

function SubTicketRow({
  subTicket,
  onToggleComplete,
  onDelete,
  onUpdateTitle,
  onOpen,
  canEditTickets,
  isSortable,
  isReorderLocked,
  dragHandleLabel,
  lockedHandleLabel,
}: SubTicketRowProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: subTicket.id,
    // A locked row cannot be picked up, which also sets `aria-disabled` on the
    // handle. It stays a drop target so a drag already under way still ends in
    // `onReorder`, which reports the lock.
    disabled: { draggable: !isSortable || isReorderLocked, droppable: !isSortable },
  });
  const [isEditing, setIsEditing] = useState(false);
  const [title, setTitle] = useState(subTicket.title);
  const inputRef = useRef<HTMLInputElement>(null);
  const deletingRef = useRef(false);
  const isCompleted = subTicket.status === "DONE";

  useEffect(() => {
    if (isEditing && inputRef.current) {
      inputRef.current.focus();
      inputRef.current.select();
    }
  }, [isEditing]);

  const handleSave = async () => {
    if (deletingRef.current) return;
    if (title.trim() && title !== subTicket.title && onUpdateTitle) {
      await onUpdateTitle(subTicket.id, title.trim());
    }
    setIsEditing(false);
  };

  const handleRowClick = (e: React.MouseEvent) => {
    // Don't open if clicking on checkbox, delete, or editing
    if ((e.target as HTMLElement).closest('button, input, [role="checkbox"]')) {
      return;
    }
    if (onOpen) {
      onOpen(subTicket);
    } else if (!isEditing) {
      setIsEditing(true);
    }
  };

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      onClick={handleRowClick}
      className={cn(
        "group hover:bg-muted -mx-2 flex cursor-pointer items-center gap-2 rounded-lg px-3 py-2 transition-colors",
        isCompleted && "opacity-60",
        isDragging && "bg-muted relative z-10 opacity-50"
      )}
    >
      {/* Drag handle. A button, so the row click guard above ignores it and it
          is reachable by keyboard; the listeners sit here only, which leaves
          the checkbox, the title and the delete button clickable. */}
      {isSortable && (
        <button
          type="button"
          {...attributes}
          {...listeners}
          aria-label={dragHandleLabel}
          title={isReorderLocked ? lockedHandleLabel : dragHandleLabel}
          className={cn(
            "text-muted-foreground flex-shrink-0 touch-none opacity-0 transition-opacity focus-visible:opacity-100",
            isReorderLocked
              ? "cursor-not-allowed group-hover:opacity-40 focus-visible:opacity-40"
              : "cursor-grab group-hover:opacity-100 active:cursor-grabbing"
          )}
        >
          <GripVertical className="h-4 w-4" />
        </button>
      )}

      {/* Checkbox */}
      {canEditTickets && (
        <Checkbox
          checked={isCompleted}
          onCheckedChange={(checked) => onToggleComplete?.(subTicket.id, !!checked)}
          className="data-[state=checked]:bg-primary data-[state=checked]:border-primary flex-shrink-0"
        />
      )}

      {/* Type badge */}
      <TicketTypeBadge type={subTicket.type} size="sm" />

      {/* Key */}
      <span className="text-muted-foreground flex-shrink-0 font-mono text-xs">{subTicket.key}</span>

      {/* Title - inline editable or clickable */}
      {isEditing && !onOpen && canEditTickets ? (
        <Input
          ref={inputRef}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          onBlur={handleSave}
          onKeyDown={(e) => {
            if (e.key === "Enter") handleSave();
            if (e.key === "Escape") {
              setTitle(subTicket.title);
              setIsEditing(false);
            }
          }}
          className="h-6 flex-1 border-0 bg-transparent px-0 text-sm shadow-none focus-visible:ring-0"
        />
      ) : (
        <span
          className={cn(
            "flex-1 truncate text-sm",
            isCompleted && "line-through",
            onOpen && "hover:text-primary"
          )}
        >
          {subTicket.title}
        </span>
      )}

      {/* Priority icon */}
      <TicketPriorityIcon priority={subTicket.priority} size={14} className="flex-shrink-0" />

      {/* Open in full editor button */}
      {onOpen && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onOpen(subTicket);
          }}
          className="text-muted-foreground hover:text-primary p-1 opacity-0 transition-all group-hover:opacity-100"
          title="Open in full editor"
        >
          <ExternalLink className="h-3.5 w-3.5" />
        </button>
      )}

      {/* Delete button */}
      {canEditTickets && onDelete && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            deletingRef.current = true;
            onDelete(subTicket.id);
          }}
          className="text-muted-foreground p-1 opacity-0 transition-all group-hover:opacity-100 hover:text-destructive"
        >
          <Trash2 className="h-3.5 w-3.5" />
        </button>
      )}
    </div>
  );
}
