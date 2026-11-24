/**
 * SprintSelector Component
 *
 * Dropdown to select active sprint
 * Per visual-design-reference.md section 9:
 * - Active sprint: accent (light Korur blue) background
 * - Completed: gray-400 text with checkmark
 * - "Create New Sprint" button at bottom
 */

"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import type { Sprint } from "@/types/sprint";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import { ChevronDown, Check, Plus, CheckCircle, Clock, Pencil, Trash2 } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { formatSprintDateRange } from "@/features/sprints/lib/sprint-dates";

interface SprintSelectorProps {
  sprints: Sprint[];
  currentSprintId: string | null;
  onSprintChange: (sprintId: string | null) => void;
  onCreateSprint: () => void;
  canCreateSprint?: boolean;
  onEditSprint?: (sprintId: string) => void;
  onDeleteSprint?: (sprintId: string) => void;
}

export function SprintSelector({
  sprints,
  currentSprintId,
  onSprintChange,
  onCreateSprint,
  canCreateSprint = true,
  onEditSprint,
  onDeleteSprint,
}: SprintSelectorProps) {
  const t = useTranslations("sprint");
  const [open, setOpen] = useState(false);

  const currentSprint = sprints.find((s) => s.id === currentSprintId);
  const activeSprints = sprints.filter((s) => s.status === "ACTIVE");
  const plannedSprints = sprints.filter((s) => s.status === "PLANNED");
  const completedSprints = sprints.filter((s) => s.status === "COMPLETED");

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          className="border-border bg-card text-foreground hover:border-border hover:bg-card h-8 min-w-[200px] justify-between rounded-lg border py-3 pr-2 pl-3 text-sm"
        >
          <span className="truncate">
            {currentSprint ? (
              <>
                <span className="font-semibold">{currentSprint.name}</span>
                {currentSprint.status === "ACTIVE" && (
                  <span className="text-primary ml-2 text-xs">{t("active")}</span>
                )}
              </>
            ) : (
              <span className="text-muted-foreground">{t("selectSprint")}</span>
            )}
          </span>
          <ChevronDown className="h-4 w-4 shrink-0" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="flex w-[300px] flex-col gap-2">
        {/* Active Sprints */}
        {activeSprints.length > 0 && (
          <>
            <div className="text-muted-foreground flex items-center gap-1.5 px-2 py-1.5 font-mono text-[11px] font-semibold tracking-[0.12em] uppercase">
              <Clock className="text-primary h-3.5 w-3.5" />
              <span>{t("active")}</span>
            </div>
            {activeSprints.map((sprint) => (
              <SprintOption
                key={sprint.id}
                sprint={sprint}
                isSelected={sprint.id === currentSprintId}
                onSelect={() => {
                  onSprintChange(sprint.id);
                  setOpen(false);
                }}
                onEdit={
                  onEditSprint
                    ? () => {
                        onEditSprint(sprint.id);
                        setOpen(false);
                      }
                    : undefined
                }
                onDelete={
                  onDeleteSprint
                    ? () => {
                        onDeleteSprint(sprint.id);
                        setOpen(false);
                      }
                    : undefined
                }
              />
            ))}
            {(plannedSprints.length > 0 || completedSprints.length > 0) && (
              <DropdownMenuSeparator />
            )}
          </>
        )}

        {/* Planned Sprints */}
        {plannedSprints.length > 0 && (
          <>
            <div className="text-muted-foreground flex items-center gap-1.5 px-2 py-1.5 font-mono text-[11px] font-semibold tracking-[0.12em] uppercase">
              <Clock className="h-3.5 w-3.5" />
              <span>{t("planned")}</span>
            </div>
            {plannedSprints.map((sprint) => (
              <SprintOption
                key={sprint.id}
                sprint={sprint}
                isSelected={sprint.id === currentSprintId}
                onSelect={() => {
                  onSprintChange(sprint.id);
                  setOpen(false);
                }}
                onEdit={
                  onEditSprint
                    ? () => {
                        onEditSprint(sprint.id);
                        setOpen(false);
                      }
                    : undefined
                }
                onDelete={
                  onDeleteSprint
                    ? () => {
                        onDeleteSprint(sprint.id);
                        setOpen(false);
                      }
                    : undefined
                }
              />
            ))}
            {completedSprints.length > 0 && <DropdownMenuSeparator />}
          </>
        )}

        {/* Completed Sprints */}
        {completedSprints.length > 0 && (
          <>
            <div className="text-muted-foreground flex items-center gap-1.5 px-2 py-1.5 font-mono text-[11px] font-semibold tracking-[0.12em] uppercase">
              <CheckCircle className="h-3.5 w-3.5" />
              <span>{t("completed")}</span>
            </div>
            {completedSprints.map((sprint) => (
              <SprintOption
                key={sprint.id}
                sprint={sprint}
                isSelected={sprint.id === currentSprintId}
                muted
                onSelect={() => {
                  onSprintChange(sprint.id);
                  setOpen(false);
                }}
                onEdit={
                  onEditSprint
                    ? () => {
                        onEditSprint(sprint.id);
                        setOpen(false);
                      }
                    : undefined
                }
                onDelete={
                  onDeleteSprint
                    ? () => {
                        onDeleteSprint(sprint.id);
                        setOpen(false);
                      }
                    : undefined
                }
              />
            ))}
          </>
        )}

        {/* Create New Sprint */}
        {canCreateSprint && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={() => {
                onCreateSprint();
                setOpen(false);
              }}
              className="flex cursor-pointer items-center gap-2 px-3 py-2"
            >
              <Plus className="h-4 w-4" />
              <span className="text-foreground font-medium">{t("createNewSprint")}</span>
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

interface SprintOptionProps {
  sprint: Sprint;
  isSelected: boolean;
  /** Selects this sprint and closes the menu. */
  onSelect: () => void;
  /** Opens the edit dialog; omitted when the user cannot manage sprints. */
  onEdit?: () => void;
  /** Opens the delete dialog; omitted when the user cannot manage sprints. */
  onDelete?: () => void;
  /** Dims the name. Set for completed sprints, which are still selectable. */
  muted?: boolean;
}

/**
 * One selectable sprint row.
 *
 * The sprint's date range is the muted second line here rather than a
 * permanent strip on the toolbar: this list is the moment the user is
 * actually comparing sprints, which is the only moment the literal dates earn
 * their space. The closed trigger deliberately stays one line -- the sprint
 * name -- so the toolbar row does not grow.
 *
 * Active, planned and completed sprints rendered three byte-identical copies
 * of this markup before; only `muted` differed, so it is the only thing that
 * stayed a prop.
 */
function SprintOption({
  sprint,
  isSelected,
  onSelect,
  onEdit,
  onDelete,
  muted = false,
}: SprintOptionProps) {
  const locale = useLocale();
  const dateRange = formatSprintDateRange(locale, sprint.startDate, sprint.endDate);

  return (
    <DropdownMenuItem
      onClick={onSelect}
      className={cn(
        "flex cursor-pointer items-center justify-between gap-2 px-3 py-2",
        isSelected && "bg-accent"
      )}
    >
      <div className="flex flex-col gap-0.5">
        <span className={cn("font-medium", muted && "text-muted-foreground")}>{sprint.name}</span>
        {dateRange && <span className="text-muted-foreground text-xs">{dateRange}</span>}
      </div>
      <div className="flex items-center gap-1">
        {isSelected && <Check className="text-primary h-4 w-4" />}
        {onEdit && (
          <button
            type="button"
            className="text-muted-foreground hover:text-foreground p-0.5"
            onClick={(e) => {
              e.stopPropagation();
              onEdit();
            }}
          >
            <Pencil className="h-3.5 w-3.5" />
          </button>
        )}
        {onDelete && (
          <button
            type="button"
            className="text-muted-foreground hover:text-destructive p-0.5"
            onClick={(e) => {
              e.stopPropagation();
              onDelete();
            }}
          >
            <Trash2 className="h-3.5 w-3.5" />
          </button>
        )}
      </div>
    </DropdownMenuItem>
  );
}
