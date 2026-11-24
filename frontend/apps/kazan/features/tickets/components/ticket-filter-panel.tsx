"use client";

import { useMemo, useState } from "react";
import {
  format,
  parseISO,
  startOfWeek,
  endOfWeek,
  startOfMonth,
  endOfMonth,
  startOfYear,
  endOfYear,
} from "date-fns";
import { getDateFnsLocale } from "@oba/shared/lib/date-fns-locale";
import { Filter, Loader2, RotateCcw, Save, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useTranslations, useLocale } from "next-intl";
import { useProjects } from "@/features/projects/hooks/use-projects";
import { useMultipleBoardLabels } from "@/features/labels/hooks/use-labels";
import type { LabelResponse } from "@/features/labels/schemas";
import { useBoardMembers } from "@/features/boards/hooks/use-board-members";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { useOrganizationUsers } from "@/features/users/hooks/use-users";
import type { TicketFilterParams } from "@/features/tickets/hooks/use-ticket-filter-params";
import {
  TicketStatusSchema,
  TicketTypeSchema,
  TicketPrioritySchema,
} from "@/types/enums";
import { TicketStatusIcon } from "./ticket-status-icon";
import { TicketTypeBadge } from "./ticket-type-badge";
import { TicketPriorityIcon } from "./ticket-priority-icon";
import { DatePicker } from "@/components/ui/date-picker";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { UserAvatar } from "@/components/ui/user-avatar";
import { BoardDot } from "@/components/ui/board-dot";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@oba/ui/lib/utils";

// ── Types ─────────────────────────────────────────────────────────────

/**
 * A board the list can be narrowed to. `depth` indents a child under its
 * parent when the caller offers a whole board tree.
 */
export interface BoardFilterOption {
  id: string;
  name: string;
  prefix: string;
  color: string;
  depth?: number;
}

export interface TicketFilterPanelProps {
  filters: TicketFilterParams;
  onChange: (filters: Partial<TicketFilterParams>) => void;
  onClear: () => void;
  activeCount: number;
  boardId: string;
  /**
   * Boards the list spans. The Board category is shown only when this is
   * non-empty, because narrowing by board is meaningless on a list that
   * covers a single board. The Label category offers the labels of these
   * boards, or of `boardId` alone when this is empty.
   */
  boardOptions?: BoardFilterOption[];
  /** Save current filter settings as user preference */
  onSavePreferences?: () => void;
  /** Reset filter settings to defaults */
  onResetPreferences?: () => void;
  /** Whether the save button should be disabled (no changes to save) */
  saveDisabled?: boolean;
  /** Whether a save/reset operation is in progress */
  isSaving?: boolean;
  /** Whether saved preferences exist (controls reset button visibility) */
  hasSavedPreferences?: boolean;
  /** Tooltip text for the save button */
  saveTooltip?: string;
  /**
   * Categories a list does not offer, because the list fixes that field
   * itself (the Incidents page lists only one ticket type).
   */
  hiddenCategories?: FilterCategory[];
  /**
   * Offers the Reporter category, which narrows by the user who created the
   * ticket. Only the cross-board list endpoint filters by creator.
   */
  showReporter?: boolean;
}

export type FilterCategory =
  | "board"
  | "status"
  | "type"
  | "priority"
  | "assignee"
  | "reviewer"
  | "reporter"
  | "label"
  | "project"
  | "dueDate";

type ArrayFilterKey =
  | "statuses"
  | "types"
  | "priorities"
  | "assigneeIds"
  | "reviewerIds"
  | "createdByIds"
  | "labelIds"
  | "boardIds";

/** The filter key each person category toggles; all three offer the same members. */
const MEMBER_FILTER_KEYS = {
  assignee: "assigneeIds",
  reviewer: "reviewerIds",
  reporter: "createdByIds",
} as const satisfies Partial<Record<FilterCategory, ArrayFilterKey>>;

/**
 * A label the list can be narrowed to. `boardName` is set only when the
 * offered labels come from more than one board, so that two labels with the
 * same name on different boards can be told apart.
 */
interface LabelOption {
  id: string;
  name: string;
  color: string;
  boardName: string | null;
}

/**
 * Merges the label lists of several boards into one option list. A board's
 * list also carries the labels of its related boards, so the same label can
 * arrive more than once; sorting by name puts same-name labels side by side.
 */
function toLabelOptions(labelLists: LabelResponse[][]): LabelOption[] {
  const byId = new Map<string, LabelResponse>();
  for (const list of labelLists) {
    for (const label of list) byId.set(label.id, label);
  }
  const unique = [...byId.values()];
  const spansBoards = new Set(unique.map((l) => l.boardId)).size > 1;
  return unique
    .map((l) => ({
      id: l.id,
      name: l.name,
      color: l.color ?? "#6B7280",
      boardName: spansBoards ? (l.boardName ?? null) : null,
    }))
    .sort(
      (a, b) =>
        a.name.localeCompare(b.name) ||
        (a.boardName ?? "").localeCompare(b.boardName ?? ""),
    );
}

/** A label matches the option search on its name or its board's name. */
function matchesLabelSearch(label: LabelOption, search: string): boolean {
  if (!search) return true;
  const needle = search.toLowerCase();
  return (
    label.name.toLowerCase().includes(needle) ||
    (label.boardName?.toLowerCase().includes(needle) ?? false)
  );
}

/** A board matches the option search on its name or its ticket key prefix. */
function matchesBoardSearch(board: BoardFilterOption, search: string): boolean {
  if (!search) return true;
  const needle = search.toLowerCase();
  return (
    board.name.toLowerCase().includes(needle) ||
    board.prefix.toLowerCase().includes(needle)
  );
}

// ── Component ─────────────────────────────────────────────────────────

/**
 * The filter browser itself, with no trigger and no popover around it.
 *
 * Split out of TicketFilterPanel so a caller that already owns a popover can
 * render the browser inline rather than nesting a second popover inside the
 * first. Mounting is the deferral: every lookup query below runs on mount, and
 * a closed popover has not mounted this, so nothing is fetched until the panel
 * is actually on screen.
 */
export interface TicketFilterPanelContentProps extends TicketFilterPanelProps {
  /**
   * Classes for the content root. A caller whose surface has a bounded height
   * passes the flex classes that let the browser shrink inside it instead of
   * being clipped by it.
   */
  className?: string;
}

export function TicketFilterPanelContent({
  filters,
  onChange,
  onClear,
  activeCount,
  boardId,
  boardOptions = [],
  onSavePreferences,
  onResetPreferences,
  saveDisabled,
  isSaving,
  hasSavedPreferences,
  saveTooltip,
  hiddenCategories = [],
  showReporter = false,
  className,
}: TicketFilterPanelContentProps) {
  const t = useTranslations("filters");
  const tCommon = useTranslations("common");
  const tStatus = useTranslations("ticket.status");
  const tTypes = useTranslations("ticket.types");
  const tPriority = useTranslations("ticket.priority");
  const tProject = useTranslations("project");
  const locale = useLocale();
  const { data: meResponse } = useGetMe();
  const currentUserId = meResponse?.data?.id;
  const [selectedCategory, setSelectedCategory] =
    useState<FilterCategory | null>(null);
  const [optionSearch, setOptionSearch] = useState("");

  const { data: projectsResponse } = useProjects(boardId);
  const projects: { id: string; name: string; color: string }[] =
    projectsResponse?.data?.map((p) => ({
      id: p.id,
      name: p.name,
      color: p.color ?? "#6B7280",
    })) ?? [];

  // A list that spans several boards offers the labels of each of them;
  // a single-board list offers its own board's labels.
  const labelBoardIdsKey =
    boardOptions.length > 0 ? boardOptions.map((b) => b.id).join(",") : boardId;
  const labelBoardIds = useMemo(
    () => (labelBoardIdsKey ? labelBoardIdsKey.split(",") : []),
    [labelBoardIdsKey],
  );
  const labelQueries = useMultipleBoardLabels(labelBoardIds);
  const labels = toLabelOptions(labelQueries.map((q) => q.data?.data ?? []));

  const hasBoardId = !!boardId;

  const { data: boardMembersResponse } = useBoardMembers(boardId, {
    enabled: hasBoardId,
  });

  const { data: usersResponse } = useOrganizationUsers({
    enabled: !hasBoardId,
  });

  const members = useMemo(() => {
    if (hasBoardId) {
      return (boardMembersResponse?.data ?? []).map((m) => ({
        id: m.userId,
        name: `${m.firstName} ${m.lastName}`.trim(),
        firstName: m.firstName,
        lastName: m.lastName,
        email: m.email,
      }));
    }
    return (usersResponse?.data ?? []).map((u) => ({
      id: u.id,
      name: `${u.profile?.firstName ?? ""} ${u.profile?.lastName ?? ""}`.trim(),
      firstName: u.profile?.firstName ?? "",
      lastName: u.profile?.lastName ?? "",
      email: u.email,
    }));
  }, [hasBoardId, boardMembersResponse?.data, usersResponse?.data]);

  const toggleArrayFilter = <T extends string>(
    key: ArrayFilterKey,
    value: T,
  ) => {
    const current = (filters[key] ?? []) as T[];
    const updated = current.includes(value)
      ? current.filter((v) => v !== value)
      : [...current, value];
    onChange({ [key]: updated });
  };

  const handleMyTicketsToggle = (active: boolean) => {
    if (active && currentUserId) {
      onChange({ assigneeIds: [currentUserId] });
    } else {
      onChange({ assigneeIds: null });
    }
  };

  const isMyTickets =
    currentUserId != null &&
    filters.assigneeIds?.length === 1 &&
    filters.assigneeIds[0] === currentUserId;

  // Reset option search when switching categories
  const handleCategorySelect = (category: FilterCategory) => {
    setSelectedCategory(category);
    setOptionSearch("");
  };

  // Count active filters per category
  const activeFilterCounts: Record<FilterCategory, number> = {
    board: filters.boardIds?.length ?? 0,
    status: filters.statuses?.length ?? 0,
    type: filters.types?.length ?? 0,
    priority: filters.priorities?.length ?? 0,
    assignee: filters.assigneeIds?.length ?? 0,
    reviewer: filters.reviewerIds?.length ?? 0,
    reporter: filters.createdByIds?.length ?? 0,
    label: filters.labelIds?.length ?? 0,
    project: filters.projectId ? 1 : 0,
    dueDate: (filters.dueDateStart ? 1 : 0) + (filters.dueDateEnd ? 1 : 0),
  };

  // Build the list of visible categories
  const allCategories: { key: FilterCategory; label: string }[] = [
    ...(boardOptions.length > 0
      ? [{ key: "board" as FilterCategory, label: t("board") }]
      : []),
    { key: "status", label: t("status") },
    { key: "type", label: t("type") },
    { key: "priority", label: t("priority") },
    { key: "assignee", label: t("assignee") },
    { key: "reviewer", label: t("reviewer") },
    ...(showReporter
      ? [{ key: "reporter" as FilterCategory, label: t("reporter") }]
      : []),
    ...(labels.length > 0
      ? [{ key: "label" as FilterCategory, label: t("label") }]
      : []),
    ...(projects.length > 0
      ? [{ key: "project" as FilterCategory, label: t("project") }]
      : []),
    { key: "dueDate" as FilterCategory, label: t("dueDate") },
  ];
  const categories = allCategories.filter((c) => !hiddenCategories.includes(c.key));

  return (
    <div className={cn("flex min-h-0 flex-col overflow-hidden", className)}>
      {/* Quick Filters + Search Header */}
      <div className="shrink-0 space-y-3 border-b px-4 pt-4 pb-3">
        {currentUserId && (
          <div>
            <Label className="text-muted-foreground mb-2 block text-xs font-medium">
              {t("quickFilters")}
            </Label>
            <div className="flex gap-2">
              <Button
                variant={isMyTickets ? "default" : "outline"}
                size="sm"
                className="border-border flex-1"
                onClick={() => handleMyTicketsToggle(true)}
              >
                {tCommon("myTickets")}
              </Button>
              <Button
                variant={!isMyTickets ? "default" : "outline"}
                size="sm"
                className="flex-1"
                onClick={() => handleMyTicketsToggle(false)}
              >
                {tCommon("allTickets")}
              </Button>
            </div>
          </div>
        )}

        <SenseySearchInput
          placeholder={t("search")}
          value={filters.search ?? ""}
          onSearchChange={(value) => onChange({ search: value || null })}
        />
      </div>

      {/* Two-Panel Layout */}
      <div className="flex min-h-0 flex-1 max-h-[320px]">
        {/* Left Panel — Categories */}
        <div className="border-r w-[40%] overflow-y-auto py-1">
          {categories.map((cat) => {
            const isActive = selectedCategory === cat.key;
            const count = activeFilterCounts[cat.key];
            return (
              <button
                key={cat.key}
                type="button"
                onClick={() => handleCategorySelect(cat.key)}
                className={cn(
                  "flex w-full items-center justify-between px-4 py-2.5 text-left text-sm transition-colors",
                  isActive
                    ? "bg-accent text-foreground border-l-2 border-primary"
                    : "hover:bg-muted/50 border-l-2 border-transparent",
                )}
              >
                <span className="font-medium">{cat.label}</span>
                {count > 0 && (
                  <span className="bg-primary/15 text-primary flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-[10px] font-semibold">
                    {count}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {/* Right Panel — Options */}
        <div className="flex w-[60%] min-h-0 flex-col">
          {selectedCategory === null ? (
            <div className="text-muted-foreground flex flex-1 items-center justify-center px-4 text-center text-sm">
              {t("selectField")}
            </div>
          ) : (
            <>
              {/* Search within options (for member-based categories) */}
              {(selectedCategory === "board" ||
                selectedCategory === "assignee" ||
                selectedCategory === "reviewer" ||
                selectedCategory === "reporter" ||
                selectedCategory === "label" ||
                selectedCategory === "project") && (
                <div className="border-b px-3 py-2">
                  <SenseySearchInput
                    placeholder={t("searchOptions")}
                    value={optionSearch}
                    onSearchChange={(value) => setOptionSearch(value ?? "")}
                  />
                </div>
              )}

              {/* Options list */}
              <div className="flex-1 overflow-y-auto px-3 py-2">
                <CategoryOptions
                  category={selectedCategory}
                  filters={filters}
                  boards={boardOptions}
                  members={members}
                  projects={projects}
                  labels={labels}
                  optionSearch={optionSearch}
                  toggleArrayFilter={toggleArrayFilter}
                  onChange={onChange}
                  locale={locale}
                  tStatus={tStatus}
                  tTypes={tTypes}
                  tPriority={tPriority}
                  tProject={tProject}
                  tCommon={tCommon}
                  tFilters={t}
                />
              </div>

              {/* Count display */}
              <OptionCount
                category={selectedCategory}
                boards={boardOptions}
                members={members}
                projects={projects}
                labels={labels}
                optionSearch={optionSearch}
                t={t}
              />
            </>
          )}
        </div>
      </div>

      {/* Footer: Save/Reset preferences + Clear All */}
      {(activeCount > 0 || onSavePreferences || onResetPreferences) && (
        <div className="shrink-0 border-t px-4 py-2 space-y-1">
          {(onSavePreferences || onResetPreferences) && (
            <TooltipProvider>
              <div className="flex items-center gap-1">
                {onSavePreferences && (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="flex-1 gap-1.5 text-xs"
                        onClick={onSavePreferences}
                        disabled={saveDisabled || isSaving}
                      >
                        {isSaving ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        ) : (
                          <Save className="h-3.5 w-3.5" />
                        )}
                        {t("savePreferences")}
                      </Button>
                    </TooltipTrigger>
                    {saveTooltip && <TooltipContent>{saveTooltip}</TooltipContent>}
                  </Tooltip>
                )}
                {onResetPreferences && hasSavedPreferences && (
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="flex-1 gap-1.5 text-xs"
                        onClick={onResetPreferences}
                        disabled={isSaving}
                      >
                        <RotateCcw className="h-3.5 w-3.5" />
                        {t("resetPreferences")}
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>{t("resetPreferencesTooltip")}</TooltipContent>
                  </Tooltip>
                )}
              </div>
            </TooltipProvider>
          )}
          {activeCount > 0 && (
            <Button
              variant="ghost"
              size="sm"
              className="w-full gap-2"
              onClick={onClear}
            >
              <X className="h-4 w-4" />
              {t("clearAll")}
            </Button>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * The filter browser behind its own funnel trigger, badged with the active
 * filter count. Kept for toolbars that surface filters as a standalone
 * control; the board list toolbars instead fold the content into their
 * single View popover.
 */
export function TicketFilterPanel(props: TicketFilterPanelProps) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" className="border-border h-8 gap-2 px-3">
          <Filter className="h-4 w-4" />
          {props.activeCount > 0 && (
            <Badge
              variant="secondary"
              className="bg-primary/15 hover:bg-primary/15 text-primary ml-1"
            >
              {props.activeCount}
            </Badge>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent
        className="flex max-h-[var(--radix-popover-content-available-height)] w-[520px] flex-col overflow-hidden p-0"
        align="end"
        collisionPadding={16}
      >
        <TicketFilterPanelContent {...props} className="min-h-0 flex-1" />
      </PopoverContent>
    </Popover>
  );
}

// ── CategoryOptions ───────────────────────────────────────────────────

interface CategoryOptionsProps {
  category: FilterCategory;
  filters: TicketFilterParams;
  boards: BoardFilterOption[];
  members: {
    id: string;
    name: string;
    firstName: string;
    lastName: string;
    email: string;
  }[];
  projects: { id: string; name: string; color: string }[];
  labels: LabelOption[];
  optionSearch: string;
  toggleArrayFilter: <T extends string>(
    key: ArrayFilterKey,
    value: T,
  ) => void;
  onChange: (filters: Partial<TicketFilterParams>) => void;
  locale: string;
  tStatus: ReturnType<typeof useTranslations>;
  tTypes: ReturnType<typeof useTranslations>;
  tPriority: ReturnType<typeof useTranslations>;
  tProject: ReturnType<typeof useTranslations>;
  tCommon: ReturnType<typeof useTranslations>;
  tFilters: ReturnType<typeof useTranslations>;
}

function CategoryOptions({
  category,
  filters,
  boards,
  members,
  projects,
  labels,
  optionSearch,
  toggleArrayFilter,
  onChange,
  locale,
  tStatus,
  tTypes,
  tPriority,
  tProject,
  tCommon,
  tFilters,
}: CategoryOptionsProps) {
  switch (category) {
    case "board": {
      const filtered = boards.filter((b) => matchesBoardSearch(b, optionSearch));
      return (
        <div className="space-y-1">
          {filtered.map((board) => (
            <label
              key={board.id}
              className="hover:bg-muted/50 flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors"
              style={{ paddingLeft: `${0.5 + (board.depth ?? 0)}rem` }}
            >
              <Checkbox
                checked={(filters.boardIds ?? []).includes(board.id)}
                onCheckedChange={() => toggleArrayFilter("boardIds", board.id)}
              />
              <BoardDot color={board.color} />
              <span className="truncate">{board.name}</span>
              <span className="text-muted-foreground shrink-0 text-xs">
                {board.prefix}
              </span>
            </label>
          ))}
          {filtered.length === 0 && (
            <p className="text-muted-foreground px-2 py-8 text-center text-sm">
              {tFilters("noBoards")}
            </p>
          )}
        </div>
      );
    }

    case "status":
      return (
        <div className="space-y-1">
          {Object.values(TicketStatusSchema.enum).map((status) => (
            <label
              key={status}
              className="hover:bg-muted/50 flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors"
            >
              <Checkbox
                checked={(filters.statuses ?? []).includes(status)}
                onCheckedChange={() => toggleArrayFilter("statuses", status)}
              />
              <TicketStatusIcon status={status} />
              <span>{tStatus(status)}</span>
            </label>
          ))}
        </div>
      );

    case "type":
      return (
        <div className="space-y-1">
          {Object.values(TicketTypeSchema.enum).map((type) => (
            <label
              key={type}
              className="hover:bg-muted/50 flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors"
            >
              <Checkbox
                checked={(filters.types ?? []).includes(type)}
                onCheckedChange={() => toggleArrayFilter("types", type)}
              />
              <TicketTypeBadge type={type} size="sm" />
              <span>{tTypes(type)}</span>
            </label>
          ))}
        </div>
      );

    case "priority":
      return (
        <div className="space-y-1">
          {Object.values(TicketPrioritySchema.enum).map((priority) => (
            <label
              key={priority}
              className="hover:bg-muted/50 flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors"
            >
              <Checkbox
                checked={(filters.priorities ?? []).includes(priority)}
                onCheckedChange={() =>
                  toggleArrayFilter("priorities", priority)
                }
              />
              <TicketPriorityIcon priority={priority} size={14} />
              <span>{tPriority(priority)}</span>
            </label>
          ))}
        </div>
      );

    case "assignee":
    case "reviewer":
    case "reporter": {
      const filterKey = MEMBER_FILTER_KEYS[category];
      const filtered = members.filter((m) =>
        optionSearch
          ? m.name.toLowerCase().includes(optionSearch.toLowerCase()) ||
            m.email.toLowerCase().includes(optionSearch.toLowerCase())
          : true,
      );
      return (
        <div className="space-y-1">
          {filtered.map((member) => (
            <label
              key={member.id}
              className="hover:bg-muted/50 flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors"
            >
              <Checkbox
                checked={(filters[filterKey] ?? []).includes(member.id)}
                onCheckedChange={() =>
                  toggleArrayFilter(filterKey, member.id)
                }
              />
              <UserAvatar
                user={{
                  firstName: member.firstName,
                  lastName: member.lastName,
                  name: member.name,
                  email: member.email,
                }}
                size="xs"
                name={member.name || member.email}
                className="gap-2.5"
                nameClassName="text-inherit text-[length:inherit] font-normal"
              />
            </label>
          ))}
          {filtered.length === 0 && (
            <p className="text-muted-foreground px-2 py-8 text-center text-sm">
              {tFilters("noMembers")}
            </p>
          )}
        </div>
      );
    }

    case "label": {
      const filtered = labels.filter((l) => matchesLabelSearch(l, optionSearch));
      return (
        <div className="space-y-1">
          {filtered.map((label) => (
            <label
              key={label.id}
              className="hover:bg-muted/50 flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors"
            >
              <Checkbox
                checked={(filters.labelIds ?? []).includes(label.id)}
                onCheckedChange={() => toggleArrayFilter("labelIds", label.id)}
              />
              <div
                className="h-3 w-3 shrink-0 rounded-full"
                style={{ backgroundColor: label.color }}
              />
              <span className="truncate">{label.name}</span>
              {label.boardName && (
                <span className="text-muted-foreground ml-auto max-w-[45%] shrink-0 truncate text-xs">
                  {label.boardName}
                </span>
              )}
            </label>
          ))}
          {filtered.length === 0 && (
            <p className="text-muted-foreground px-2 py-8 text-center text-sm">
              {tFilters("noLabels")}
            </p>
          )}
        </div>
      );
    }

    case "project": {
      const filtered = projects.filter((p) =>
        optionSearch
          ? p.name.toLowerCase().includes(optionSearch.toLowerCase())
          : true,
      );
      return (
        <div className="space-y-1">
          <label className="hover:bg-muted/50 flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors">
            <Checkbox
              checked={!filters.projectId}
              onCheckedChange={() => onChange({ projectId: null })}
            />
            <span>{tCommon("allTickets")}</span>
          </label>
          <label className="hover:bg-muted/50 flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors">
            <Checkbox
              checked={filters.projectId === "none"}
              onCheckedChange={() =>
                onChange({
                  projectId: filters.projectId === "none" ? null : "none",
                })
              }
            />
            <span>{tProject("noProject")}</span>
          </label>
          {filtered.map((project) => (
            <label
              key={project.id}
              className="hover:bg-muted/50 flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 text-sm transition-colors"
            >
              <Checkbox
                checked={filters.projectId === project.id}
                onCheckedChange={() =>
                  onChange({
                    projectId:
                      filters.projectId === project.id ? null : project.id,
                  })
                }
              />
              <div
                className="h-3 w-3 shrink-0 rounded-full"
                style={{ backgroundColor: project.color }}
              />
              <span className="truncate">{project.name}</span>
            </label>
          ))}
        </div>
      );
    }

    case "dueDate": {
      const dateLocale = getDateFnsLocale(locale);
      const dueDateStartValue = filters.dueDateStart
        ? parseISO(filters.dueDateStart)
        : undefined;
      const dueDateEndValue = filters.dueDateEnd
        ? parseISO(filters.dueDateEnd)
        : undefined;

      const today = new Date();
      const presets = [
        {
          label: tFilters("today"),
          start: format(today, "yyyy-MM-dd"),
          end: format(today, "yyyy-MM-dd"),
        },
        {
          label: tFilters("thisWeek"),
          start: format(startOfWeek(today, { weekStartsOn: 1 }), "yyyy-MM-dd"),
          end: format(endOfWeek(today, { weekStartsOn: 1 }), "yyyy-MM-dd"),
        },
        {
          label: tFilters("thisMonth"),
          start: format(startOfMonth(today), "yyyy-MM-dd"),
          end: format(endOfMonth(today), "yyyy-MM-dd"),
        },
        {
          label: tFilters("thisYear"),
          start: format(startOfYear(today), "yyyy-MM-dd"),
          end: format(endOfYear(today), "yyyy-MM-dd"),
        },
      ];

      return (
        <div className="space-y-3 px-1">
          <div className="flex flex-wrap gap-1.5">
            {presets.map((preset) => {
              const isActive =
                filters.dueDateStart === preset.start &&
                filters.dueDateEnd === preset.end;
              return (
                <Button
                  key={preset.label}
                  variant={isActive ? "secondary" : "outline"}
                  size="sm"
                  className="h-7 text-xs"
                  onClick={() =>
                    onChange({
                      dueDateStart: preset.start,
                      dueDateEnd: preset.end,
                    })
                  }
                >
                  {preset.label}
                </Button>
              );
            })}
          </div>

          <DatePicker
            label={tFilters("dueDateStart")}
            locale={dateLocale}
            value={dueDateStartValue}
            onChange={(date) =>
              onChange({
                dueDateStart: date
                  ? format(date, "yyyy-MM-dd")
                  : null,
              })
            }
            toDate={dueDateEndValue}
            placeholder={tCommon("selectDate")}
            dateFormat="PP"
          />

          <DatePicker
            label={tFilters("dueDateEnd")}
            locale={dateLocale}
            value={dueDateEndValue}
            onChange={(date) =>
              onChange({
                dueDateEnd: date
                  ? format(date, "yyyy-MM-dd")
                  : null,
              })
            }
            fromDate={dueDateStartValue}
            placeholder={tCommon("selectDate")}
            dateFormat="PP"
          />
        </div>
      );
    }

    default:
      return null;
  }
}

// ── OptionCount ───────────────────────────────────────────────────────

interface OptionCountProps {
  category: FilterCategory;
  boards: BoardFilterOption[];
  members: { id: string; name: string; email: string }[];
  projects: { id: string; name: string; color: string }[];
  labels: LabelOption[];
  optionSearch: string;
  t: ReturnType<typeof useTranslations>;
}

function OptionCount({
  category,
  boards,
  members,
  projects,
  labels,
  optionSearch,
  t,
}: OptionCountProps) {
  let visible = 0;
  let total = 0;

  switch (category) {
    case "board":
      total = boards.length;
      visible = boards.filter((b) => matchesBoardSearch(b, optionSearch)).length;
      break;
    case "status":
      total = Object.values(TicketStatusSchema.enum).length;
      visible = total;
      break;
    case "type":
      total = Object.values(TicketTypeSchema.enum).length;
      visible = total;
      break;
    case "priority":
      total = Object.values(TicketPrioritySchema.enum).length;
      visible = total;
      break;
    case "assignee":
    case "reviewer":
    case "reporter": {
      total = members.length;
      visible = optionSearch
        ? members.filter(
            (m) =>
              m.name.toLowerCase().includes(optionSearch.toLowerCase()) ||
              m.email.toLowerCase().includes(optionSearch.toLowerCase()),
          ).length
        : total;
      break;
    }
    case "label":
      total = labels.length;
      visible = labels.filter((l) => matchesLabelSearch(l, optionSearch)).length;
      break;
    case "project":
      total = projects.length + 2; // +2 for "all" and "no project"
      visible = optionSearch
        ? projects.filter((p) =>
            p.name.toLowerCase().includes(optionSearch.toLowerCase()),
          ).length + 2
        : total;
      break;
    case "dueDate":
      // Date range picker doesn't have countable options
      return null;
  }

  return (
    <div className="text-muted-foreground shrink-0 border-t px-3 py-1.5 text-right text-xs">
      {t("optionCount", { visible, total })}
    </div>
  );
}
