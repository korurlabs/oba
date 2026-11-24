/**
 * SprintChildBoardsField Component
 *
 * The create sprint form's picker for the descendant boards that get the new
 * sprint too. A closed trigger keeps the dialog short however deep the board
 * tree is; the popover shows the tree indented by depth, with a search by name
 * or prefix. A board the user is not an admin of cannot be chosen, because the
 * server refuses the whole create for it. A board that already runs its own
 * sprint stays selectable: switching it to the shared sprint is a valid
 * choice, so it is only flagged.
 */

"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { useFormContext } from "react-hook-form";
import { Check, ChevronDown } from "lucide-react";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@oba/ui/components/command";
import { Popover, PopoverContent, PopoverTrigger } from "@oba/ui/components/popover";
import { Button } from "@/components/ui/button";
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { cn } from "@/lib/utils";
import type { SprintChildBoardResponse } from "@/features/sprints/schemas";

type ChildBoardsFormShape = { childBoardIds: string[] };

/** Left padding added for each level below the direct children. */
const INDENT_PER_DEPTH_PX = 16;

/** Selected boards shown as chips on the trigger before the rest collapse into "+N". */
const MAX_TRIGGER_CHIPS = 3;

/** The boards checked when the dialog opens: linkable and without an active sprint. */
export function preselectedChildBoardIds(boards: SprintChildBoardResponse[]): string[] {
  return boards.filter((board) => board.canLink && !board.activeSprintId).map((board) => board.id);
}

interface SprintChildBoardsFieldProps {
  boards: SprintChildBoardResponse[];
  disabled?: boolean;
}

/**
 * The shared multi-select removes chosen options from its list and has no
 * tree, so this picker follows the app's repository combobox (Popover and
 * Command) and keeps every board in place with a checkbox.
 */
export function SprintChildBoardsField({ boards, disabled = false }: SprintChildBoardsFieldProps) {
  const t = useTranslations("sprint.childBoards");
  const { control } = useFormContext<ChildBoardsFormShape>();
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");

  const linkableIds = useMemo(
    () => boards.filter((board) => board.canLink).map((board) => board.id),
    [boards]
  );

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return boards;
    return boards.filter(
      (board) =>
        board.name.toLowerCase().includes(term) || board.prefix.toLowerCase().includes(term)
    );
  }, [boards, search]);

  // A reopened picker starts unfiltered.
  const handleOpenChange = (next: boolean) => {
    setOpen(next);
    if (!next) setSearch("");
  };

  return (
    <FormField
      control={control}
      name="childBoardIds"
      render={({ field }) => {
        const selected = new Set(field.value);
        // Tree order, not click order, so the chips read like the list.
        const selectedBoards = boards.filter((board) => selected.has(board.id));
        const hiddenCount = selectedBoards.length - MAX_TRIGGER_CHIPS;
        const toggle = (boardId: string) => {
          field.onChange(
            selected.has(boardId)
              ? field.value.filter((id) => id !== boardId)
              : [...field.value, boardId]
          );
        };

        return (
          <FormItem>
            <FormLabel className="text-sm">{t("title")}</FormLabel>
            <Popover open={open} onOpenChange={handleOpenChange}>
              <PopoverTrigger asChild>
                <FormControl>
                  <Button
                    type="button"
                    variant="outline"
                    role="combobox"
                    aria-expanded={open}
                    disabled={disabled}
                    className={cn(
                      "border-input bg-background dark:bg-input/30 text-foreground h-auto min-h-11 w-full justify-between gap-2 rounded-lg border-[1.5px] px-3 py-2 text-sm font-normal shadow-xs",
                      "hover:border-border hover:bg-background dark:hover:bg-input/50",
                      "focus-visible:border-primary focus-visible:ring-primary/20 focus-visible:ring-[3px]",
                      open && "border-primary ring-primary/20 ring-[3px]"
                    )}
                  >
                    {selectedBoards.length === 0 ? (
                      <span className="text-muted-foreground truncate">{t("noneSelected")}</span>
                    ) : (
                      <span className="flex min-w-0 flex-1 flex-wrap items-center gap-1">
                        {selectedBoards.slice(0, MAX_TRIGGER_CHIPS).map((board) => (
                          <span
                            key={board.id}
                            className="bg-muted max-w-[9rem] truncate px-2 py-0.5 text-xs"
                            title={board.name}
                          >
                            {board.name}
                          </span>
                        ))}
                        {hiddenCount > 0 && (
                          <span className="text-muted-foreground shrink-0 text-xs">
                            {t("moreCount", { count: hiddenCount })}
                          </span>
                        )}
                      </span>
                    )}
                    <ChevronDown className="h-4 w-4 shrink-0 opacity-50" aria-hidden />
                  </Button>
                </FormControl>
              </PopoverTrigger>
              <PopoverContent
                align="start"
                collisionPadding={16}
                className="flex max-h-[min(24rem,var(--radix-popover-content-available-height))] w-(--radix-popover-trigger-width) max-w-[calc(100vw-2rem)] flex-col p-0"
              >
                <Command shouldFilter={false} className="min-h-0 flex-1">
                  <CommandInput
                    placeholder={t("searchPlaceholder")}
                    value={search}
                    onValueChange={setSearch}
                  />
                  <div className="border-border flex shrink-0 items-center justify-between gap-2 border-b px-3 py-1.5 text-xs">
                    <span className="text-muted-foreground">
                      {t("selectedCount", { count: selectedBoards.length })}
                    </span>
                    <div className="flex items-center gap-3">
                      <button
                        type="button"
                        className="text-primary hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                        onClick={() => field.onChange(linkableIds)}
                        disabled={linkableIds.length === 0}
                      >
                        {t("selectAll")}
                      </button>
                      <button
                        type="button"
                        className="text-primary hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                        onClick={() => field.onChange([])}
                        disabled={field.value.length === 0}
                      >
                        {t("selectNone")}
                      </button>
                    </div>
                  </div>
                  {/*
                    The dialog's scroll lock swallows wheel events that reach
                    the document from this portalled list; stopping them here
                    lets the list scroll, as the shared select field does.
                  */}
                  <CommandList
                    className="max-h-none min-h-0 flex-1 overscroll-contain"
                    onWheel={(event) => event.stopPropagation()}
                  >
                    <CommandEmpty>{t("noMatches")}</CommandEmpty>
                    <CommandGroup>
                      {filtered.map((board) => {
                        const checked = selected.has(board.id);
                        const hint = !board.canLink
                          ? t("adminRequired")
                          : board.activeSprintId
                            ? t("hasActiveSprint", { name: board.activeSprintName ?? "" })
                            : null;

                        return (
                          <CommandItem
                            key={board.id}
                            value={board.id}
                            disabled={!board.canLink}
                            onSelect={() => toggle(board.id)}
                            aria-checked={checked}
                            style={{ paddingLeft: 8 + (board.depth - 1) * INDENT_PER_DEPTH_PX }}
                          >
                            {/*
                              Visual only: the row is the control. A plain box, not the
                              Checkbox button, because the command item's svg rules resize
                              and recolour a nested indicator icon. The icon carries its own
                              size and text classes so those rules skip it.
                            */}
                            <span
                              aria-hidden
                              className={cn(
                                "flex size-4 shrink-0 items-center justify-center rounded-sm border",
                                checked
                                  ? "border-primary bg-primary text-primary-foreground"
                                  : "border-input dark:bg-input/30"
                              )}
                            >
                              {checked && <Check className="size-3 text-current" />}
                            </span>
                            <span className="min-w-0 truncate" title={board.name}>
                              {board.name}
                            </span>
                            <span className="text-muted-foreground shrink-0 text-xs">
                              {board.prefix}
                            </span>
                            {hint && (
                              <span
                                className={cn(
                                  "ml-auto shrink-0 text-xs",
                                  board.canLink ? "text-foreground" : "text-muted-foreground"
                                )}
                              >
                                {hint}
                              </span>
                            )}
                          </CommandItem>
                        );
                      })}
                    </CommandGroup>
                  </CommandList>
                </Command>
              </PopoverContent>
            </Popover>
            <FormMessage />
          </FormItem>
        );
      }}
    />
  );
}
