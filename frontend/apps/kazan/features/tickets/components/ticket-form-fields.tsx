/**
 * TicketFormFields Component
 *
 * Renders all form fields, sub-tickets, and danger zone for the ticket
 * create/edit form. Shared by BOTH shells that host the form, and both use
 * the same two-pane layout for create AND edit:
 *
 *   - left:  Title, Description, Attachments, Sub-tickets, Danger zone
 *   - right: a ~20rem properties panel of label-left / value-right rows
 *
 * The only difference between the shells is `withTabs`: the full page
 * (`variant="page"`) splits the left pane into Details / Attachments tabs,
 * the dialog (`variant="dialog"`) shows it as one column.
 *
 * Must be rendered inside a <Form> (FormProvider) to access useFormContext.
 *
 * Internalises:
 * - form control via useFormContext (no prop drilling)
 * - option lists (TICKET_TYPES, STATUS_OPTIONS, PRIORITIES)
 * - parentTicketOptions (derived from availableParentTickets)
 * - board role checks
 */

"use client";

import type { TicketListResponse, TicketDetailResponse } from "@/features/tickets/schemas";
import type { TicketFormValues } from "../schemas";
import { TICKET_BACKLOG_SPRINT_VALUE, TICKET_NO_MILESTONE_VALUE } from "../schemas";
import type { TicketPickerUser } from "../hooks/use-ticket-form";
import type { SelectOption } from "@oba/shared/features/form/components";
import type { BoardListWithChildrenResponse } from "@/features/boards/schemas";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { TextField, SelectField, DateField } from "@/components/ui/form-fields";
import { FormFieldWrapper } from "@/components/ui/form-fields/form-field-wrapper";
import { FormField } from "@/components/ui/form";
import { SelectFieldStandalone, SelectField as SharedSelectField } from "@oba/shared/features/form/components";
import {
  AttachmentSection,
  StorageEntityType,
  parseAttachmentIds,
} from "@oba/shared/features/attachments";
import { UserAvatar } from "@/components/ui/user-avatar";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { SubTicketList } from "./sub-ticket-list";
import { TicketFormSkeleton } from "./ticket-form-skeleton";
import { MarkdownEditor } from "@oba/shared/components/markdown-editor-lazy";
import type { MarkdownEditorComponents } from "@oba/shared/components/markdown-editor-components";
import { DangerZone } from "@/components/shared/danger-zone";
import { useTranslations } from "next-intl";
import { MissionRank } from "@/types/enums";
import { useFormContext, type Control } from "react-hook-form";
import { useTicketTypeOptions, usePriorityOptions, useStatusOptions } from "./ticket-form-options";
import { useOptionalBoardRoleContext } from "@/features/boards/providers/board-role-provider";
import { useBoardRole } from "@/features/boards/hooks/use-board-role";
import { Children, useMemo, useCallback, useId, type ReactNode } from "react";
import { useLabels, useCreateLabel } from "@/features/labels/hooks/use-labels";
import { useProjects, useCreateProject } from "@/features/projects/hooks/use-projects";
import { useSprints } from "@/features/sprints/hooks/use-sprints";
import { useMilestoneOptions } from "@/features/milestones/hooks/use-milestone-options";
import { useTicketPageTab, type TicketPageTab } from "../hooks/use-ticket-page-tab";
import {
  resolveAllowedStatuses,
  resolveDetachesFromParent,
  useReparentPlacement,
  useUnparentPlacement,
} from "../hooks/use-ticket-form";

// ── Layout constants ──────────────────────────────────────────────────

/**
 * Height and padding for every control in the properties panel. The base
 * controls are `h-11`, which is right for a stacked form but makes a column
 * of label-left rows tall and heavy. Covers all three trigger kinds in use
 * (Input, Radix Select trigger, the shared popover select) -- `min-h-9`
 * overrides the shared select's `min-h-11`, `py-1` keeps text from being
 * squeezed by the base `py-3`.
 */
const PANEL_CONTROL_CLASS = "h-9 min-h-9 px-3 py-1";

/** Kazan's own primitives, so the shared editor's toolbar and link popover look like Kazan. */
const DESCRIPTION_EDITOR_COMPONENTS: MarkdownEditorComponents = { Button, Input };

/**
 * One titled section of panel rows. Two columns of rows only when the PANEL
 * itself is at least 36rem wide (`@xl`, a container query against the
 * `@container` on the panel `<aside>`), otherwise one column.
 *
 * Viewport breakpoints cannot decide this. This app's breakpoints are
 * overridden in `@oba/ui` globals.css (`sm` = 320px, `md` = 744px,
 * `lg` = 1440px), so `sm:` already applies on phones, where two 7rem-label
 * rows side by side left ~44px controls. And on the page the panel's width
 * depends on the sidebar, not the viewport. The container query goes two
 * columns in the stacked dialog from 744px and on the stacked page when the
 * sidebar leaves room; the 20rem panel beside the content at `lg` stays one
 * column on its own.
 */
const PANEL_GROUP_CLASS = "grid grid-cols-1 gap-x-6 gap-y-2 @xl:grid-cols-2";

/**
 * Section heading: small, bold (`font-semibold`) foreground text, the weight
 * and colour the app's other bold headings use. Deliberately not CSS
 * `uppercase`: `<html lang>` is not set per locale, and Turkish needs
 * locale-aware casing (i -> İ). `col-span-full` keeps it on its own line in
 * the two-column layout. Its bottom border is the section's divider: the same
 * subtle `border-border` line the form uses elsewhere, 4px under the text,
 * and it spans both columns whenever the panel has two.
 */
const PANEL_HEADING_CLASS =
  "text-foreground border-border col-span-full border-b pb-1 text-xs font-semibold";

/**
 * One titled section of the properties panel: heading, divider, rows. Renders
 * nothing when every row in it is hidden, so a heading or divider never sits
 * over an empty section (and the panel's flex gap skips it too).
 */
function PanelSection({ title, children }: { title: string; children: ReactNode }) {
  const headingId = useId();
  // `toArray` drops the `false` of a hidden row and keys the rest.
  const rows = Children.toArray(children);
  if (rows.length === 0) return null;
  return (
    <section aria-labelledby={headingId} className={PANEL_GROUP_CLASS}>
      <h3 id={headingId} className={PANEL_HEADING_CLASS}>
        {title}
      </h3>
      {rows}
    </section>
  );
}

/** The two panes: content on the left, the ~20rem properties panel on the right. */
const PANES_CLASS =
  "mx-auto grid w-full max-w-6xl grid-cols-1 gap-x-6 gap-y-4 lg:grid-cols-[minmax(0,1fr)_20rem]";

// ── Interfaces ────────────────────────────────────────────────────────

/** Sub-ticket action handlers grouped to reduce prop drilling */
export interface SubTicketActions {
  /** Resolves to true when the sub-ticket was created; see `handleCreateSubTicket`. */
  onCreate: (title: string) => Promise<boolean>;
  onUpdateTitle: (id: string, title: string) => Promise<void>;
  onToggleComplete: (id: string, completed: boolean) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
  /** A sub-ticket was dropped on a sibling: `activeId` takes the slot of `overId`. */
  onReorder: (activeId: string, overId: string) => void;
  onOpenSubTicket: (subTicket: TicketListResponse) => void;
  onRequestCreate: () => void;
}

/** Board-related props for the create-mode board selector */
export interface BoardSelectorProps {
  selectedBoardId: string;
  setSelectedBoardId: (id: string) => void;
  boardSelectOptions: SelectOption[];
  isLoading: boolean;
}

export interface TicketFormFieldsProps {
  mode: "create" | "edit";
  /**
   * Split the left pane into Details / Attachments tabs. Set by the full
   * page; the dialog shows the left pane as one column.
   */
  withTabs?: boolean;
  activeTicketId: string | null;
  ticket: TicketDetailResponse | null;
  /**
   * True while the edit-mode detail query is in flight and no cached
   * ticket is available yet. When true, the component renders a
   * skeleton placeholder instead of empty form inputs to avoid the
   * empty-input flash on deep-link / sub-ticket navigation.
   */
  isTicketLoading: boolean;
  isSaving: boolean;
  boardSelector: BoardSelectorProps;
  renderBoardOption: (option: SelectOption) => ReactNode;
  renderBoardValue: (option: SelectOption) => ReactNode;
  availableUsers: TicketPickerUser[];
  availableParentTickets: TicketListResponse[];
  subTickets: TicketListResponse[];
  subTicketActions: SubTicketActions;
  isCreatingSubTicket: boolean;
  /** True while a sub-ticket reorder is saving or the ticket detail refetches. */
  isSubTicketReorderLocked: boolean;
  isDeleting: boolean;
  onDeleteTicket: () => Promise<void>;
  onFileUpload: (file: File) => Promise<{ url: string; isImage: boolean; filename: string }>;
  /**
   * Registers a dropped/pasted/picked image with the parent's pending-
   * images map. Returns the generated localId. Forwarded to
   * `<MarkdownEditor onImageDrop={...}>` so the editor can defer the
   * upload until form submit.
   */
  addPendingImage: (image: { file: File; blobUrl: string; width: number; height: number }) => string;
  /**
   * Removes the pending image whose `blobUrl` matches `src`. No-op for
   * srcs that aren't in the map (already-saved Azure URLs, unknown
   * srcs). Forwarded to `<MarkdownEditor onImageDelete={...}>` so the
   * trash-button NodeView can eagerly clean up unsaved images.
   */
  removePendingImageBySrc: (src: string) => void;
  onOpenChange: (open: boolean) => void;
  /**
   * Create mode only: receives the files staged by the attachment picker.
   * They cannot be uploaded yet -- the endpoint needs a ticket id -- so the
   * form hook uploads them immediately after the ticket is created.
   */
  onStagedAttachmentsChange: (files: File[]) => void;
  currentBoard: BoardListWithChildrenResponse | null;
  isBacklog: boolean;
  isBacklogPending: boolean;
}

export function TicketFormFields({
  mode,
  withTabs = false,
  activeTicketId,
  ticket,
  isTicketLoading,
  isSaving,
  boardSelector,
  renderBoardOption,
  renderBoardValue,
  availableUsers,
  availableParentTickets,
  subTickets,
  subTicketActions,
  isCreatingSubTicket,
  isSubTicketReorderLocked,
  isDeleting,
  onDeleteTicket,
  onFileUpload,
  addPendingImage,
  removePendingImageBySrc,
  onOpenChange,
  onStagedAttachmentsChange,
  currentBoard,
  isBacklog,
  isBacklogPending,
}: TicketFormFieldsProps) {
  const t = useTranslations();

  // Which page tab is open, from `?t_tab=`. Called unconditionally to keep
  // hook order stable; only the page (`withTabs`) renders tabs at all — the
  // dialog has no tabs and never writes the param.
  const [activeTab, setActiveTab] = useTicketPageTab();

  // ── Form context (provided by parent <Form>) ────────────────────────
  const formContext = useFormContext<TicketFormValues>();
  // Cast control to fix type mismatch between @hookform/resolvers v5 and react-hook-form v7
  const control = formContext.control as unknown as Control<TicketFormValues>;
  const { watch } = formContext;

  // ── Option lists (hooks that use useFormContext internally) ──────────
  const TICKET_TYPES = useTicketTypeOptions();
  const STATUS_OPTIONS = useStatusOptions();
  const PRIORITIES = usePriorityOptions();

  // ── Sprint / backlog placement ───────────────────────────────────────
  // Moving a ticket between sprints (or into the backlog) used to be possible
  // only from the sprint/backlog table's row menu. The field below is the same
  // operation from inside the form; `useTicketForm` turns a change here into
  // the same `POST .../move` the table fires.
  const selectedSprintId = watch("sprintId");
  // The statuses the save keeps where the form places the ticket, by the
  // rule `useTicketForm` coerces the value with. A top-level backlog ticket
  // is locked to TODO, and so is a sub-ticket the save detaches into a
  // backlog; a sub-ticket that keeps its parent is not narrowed at all.
  const selectedParentId = watch("parentId");
  const isSubTicket = !!selectedParentId && selectedParentId !== "none";
  const unparent = useUnparentPlacement(ticket);
  const reparent = useReparentPlacement(ticket, selectedParentId);
  // A Placement away from the parent in the Parent field leaves the ticket
  // standalone on save, which the hint under the Placement field says.
  const detachesFromParent = resolveDetachesFromParent({
    ticket,
    sprintId: selectedSprintId,
    parentId: selectedParentId,
    unparentPlacement: unparent.placement,
    reparentPlacement: reparent.placement,
  });
  const allowedStatuses = useMemo(
    () =>
      resolveAllowedStatuses({
        ticket,
        sprintId: selectedSprintId,
        parentId: selectedParentId,
        unparentPlacement: unparent.placement,
        detachesFromParent,
      }),
    [ticket, selectedSprintId, selectedParentId, unparent.placement, detachesFromParent],
  );
  // Only TODO is left, which the backlog hint explains.
  const isBacklogStatusLocked = allowedStatuses?.length === 1;

  // Both modes: the Placement field is now part of the create form too, so the
  // selected board's sprints have to be listed whichever mode we are in.
  const { data: sprintsResponse } = useSprints(boardSelector.selectedBoardId);

  // The ticket's own sprint, straight off the detail response (edit only).
  const currentSprint = ticket?.sprint ?? null;

  /**
   * Two dropdown sections: "Backlog" (the single backlog option) and
   * "Sprints". The Sprints section disappears by itself when the board has
   * none, because the shared select only renders groups that have options.
   */
  const sprintOptions = useMemo((): SelectOption[] => {
    const backlogLabel = t("ticket.fields.placementBacklog");
    const sprintsGroup = t("ticket.fields.placementSprints");
    const sprints: SelectOption[] = (sprintsResponse?.data ?? [])
      // A completed sprint is not a valid move target -- but the ticket's own
      // sprint, and whatever is selected, must stay listed: the select shows
      // only values it has an option for, so dropping them would blank the
      // trigger and the user could not tell (or restore) where the ticket is.
      .filter(
        (sprint) =>
          sprint.status !== "COMPLETED" ||
          sprint.id === selectedSprintId ||
          sprint.id === currentSprint?.id,
      )
      .map((sprint) => ({ value: sprint.id, label: sprint.name, group: sprintsGroup }));
    // The board's list does not contain the ticket's sprint while it is still
    // loading, or when that sprint is no longer linked to the board. The
    // detail response carries its name, so the trigger can show it anyway.
    // After a board change only the target board's sprints are valid.
    const showsOwnBoard =
      !ticket?.board?.id || boardSelector.selectedBoardId === ticket.board.id;
    if (
      showsOwnBoard &&
      currentSprint &&
      !sprints.some((option) => option.value === currentSprint.id)
    ) {
      sprints.push({ value: currentSprint.id, label: currentSprint.name, group: sprintsGroup });
    }
    return [
      { value: TICKET_BACKLOG_SPRINT_VALUE, label: backlogLabel, group: backlogLabel },
      ...sprints,
    ];
  }, [sprintsResponse?.data, selectedSprintId, currentSprint, ticket?.board?.id, boardSelector.selectedBoardId, t]);

  // Offering a status the backend overwrites would be offering something the
  // save silently discards. `useTicketForm` pushes the value itself to an
  // allowed status in the same state, so the select is never left showing an
  // option it no longer lists. Locked to TODO the select is also disabled
  // (with the reason in a label tooltip); narrowing the list stays as a
  // second guard.
  const statusOptions = useMemo(
    () =>
      allowedStatuses
        ? STATUS_OPTIONS.filter((option) => allowedStatuses.some((status) => status === option.value))
        : STATUS_OPTIONS,
    [allowedStatuses, STATUS_OPTIONS],
  );

  // ── Parent ticket options (derived from availableParentTickets) ──────
  const parentTicketOptions = useMemo(
    () => [
      { value: "none", label: t("ticket.fields.parentNone") },
      ...availableParentTickets
        .filter((pt) => pt.id !== ticket?.id)
        .map((pt) => ({
          value: pt.id,
          label: `(${pt.key}) ${pt.title}`,
        })),
    ],
    [availableParentTickets, ticket?.id, t]
  );

  // ── Board role ──────────────────────────────────────────────────────
  // Try context first (available on board pages with BoardRoleProvider).
  // Always call useBoardRole as fallback — React Query deduplicates when both fire.
  const boardRoleCtx = useOptionalBoardRoleContext();
  const directRole = useBoardRole(boardSelector.selectedBoardId);
  const { currentUserRole, canEdit, isLoading } = boardRoleCtx ?? directRole;
  const isReadOnly = currentUserRole === "VIEWER";

  // ── User options for assignee / reviewer SharedSelectField ────────
  // A member without Kazan access is listed but cannot be picked, because
  // the server refuses to add them. One the field already holds stays
  // clickable, so it can be removed; an update keeps the people the ticket
  // already has, so the server accepts them.
  const assigneeIds = watch("assigneeIds");
  const reviewerIds = watch("reviewerIds");
  const userOptionsFor = useCallback(
    (selectedIds: string[] | undefined): SelectOption[] => {
      const selected = new Set(selectedIds ?? []);
      return availableUsers.map((u) => ({
        value: u.id,
        label: [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email || u.id,
        disabled: !u.kazanAccess && !selected.has(u.id),
        meta: { user: u },
      }));
    },
    [availableUsers]
  );
  const assigneeOptions = useMemo(() => userOptionsFor(assigneeIds), [userOptionsFor, assigneeIds]);
  const reviewerOptions = useMemo(() => userOptionsFor(reviewerIds), [userOptionsFor, reviewerIds]);

  const renderUserOption = useCallback(
    (option: SelectOption) => {
      const user = option.meta?.user as TicketPickerUser | undefined;
      if (!user) return <span>{option.label}</span>;
      // The name takes the color, size and weight of where the select shows it (an
      // option, the trigger, a selected chip). The unit shrinks, so the name
      // ellipsises inside a narrow panel trigger instead of pushing the `+N`
      // count out of view.
      const avatar = (
        <UserAvatar
          user={user}
          size="xs"
          name={option.label}
          nameClassName="text-inherit text-[length:inherit] font-[weight:inherit]"
        />
      );
      if (user.kazanAccess) return avatar;
      return (
        <span className="flex min-w-0 items-center gap-2" title={t("kazan.memberAccess.noAccessHint")}>
          {avatar}
          <span className="text-muted-foreground shrink-0 text-xs">
            ({t("kazan.memberAccess.noAccess")})
          </span>
        </span>
      );
    },
    [t]
  );

  // ── Label options & inline creation ────────────────────────────────
  const { data: labelsResponse } = useLabels(boardSelector.selectedBoardId);
  const labelOptions = useMemo(
    () =>
      (labelsResponse?.data ?? []).map((label) => ({
        value: label.id,
        label: label.name,
        meta: { color: label.color, boardId: label.boardId, boardName: label.boardName },
      })),
    [labelsResponse?.data],
  );

  const createLabel = useCreateLabel();
  const handleCreateLabel = useCallback(
    async (name: string) => {
      if (!boardSelector.selectedBoardId) return;
      await createLabel.mutateAsync({
        boardId: boardSelector.selectedBoardId,
        data: { name },
      });
    },
    [createLabel, boardSelector.selectedBoardId],
  );

  // ── Project options & inline creation ──────────────────────────────
  const { data: projectsResponse } = useProjects(boardSelector.selectedBoardId);
  const projectOptions = useMemo(
    () =>
      (projectsResponse?.data ?? []).map((project) => ({
        value: project.id,
        label: project.name,
        meta: { color: project.color, boardId: project.boardId, boardName: project.boardName },
      })),
    [projectsResponse?.data],
  );

  const createProject = useCreateProject();
  const handleCreateProject = useCallback(
    async (name: string) => {
      if (!boardSelector.selectedBoardId) return;
      await createProject.mutateAsync({
        boardId: boardSelector.selectedBoardId,
        data: { name },
      });
    },
    [createProject, boardSelector.selectedBoardId],
  );

  // ── Milestone options ──────────────────────────────────────────────
  // OPEN milestones of this board and its related boards, by id with the
  // target date in the label (names are not unique). The ticket's current
  // milestone stays listed even when it is CLOSED.
  const currentMilestone = ticket?.milestone;
  const pinnedMilestone = useMemo(
    () =>
      currentMilestone
        ? {
            id: currentMilestone.id,
            name: currentMilestone.name,
            targetDate: currentMilestone.targetDate,
            boardId: currentMilestone.boardId,
          }
        : null,
    [currentMilestone],
  );
  const { options: attachableMilestoneOptions } = useMilestoneOptions(
    boardSelector.selectedBoardId,
    { pinned: pinnedMilestone },
  );
  const milestoneOptions = useMemo<SelectOption[]>(
    () => [
      { value: TICKET_NO_MILESTONE_VALUE, label: t("ticket.fields.noMilestone") },
      ...attachableMilestoneOptions,
    ],
    [attachableMilestoneOptions, t],
  );

  // ── Description-embedded attachment ids ────────────────────────────
  // Images dropped into the description are uploaded as attachments on
  // this same (TICKET, ticketId) pair, so GET /storage/attachments
  // returns them alongside standalone files (CLI `--attach`, the upload
  // control below). The editor already renders them inline, so they are
  // excluded from the attachment list -- otherwise every inline image
  // would appear twice, and its delete button would leave a dangling
  // <img> in the description.
  //
  // Derived from the PERSISTED description (`ticket.description`), not
  // the live form value, so mid-edit typing doesn't make list rows
  // flicker in and out. An image removed in the editor stays hidden here
  // until save, at which point the form's orphan GC deletes it anyway.
  const descriptionAttachmentIds = useMemo(
    () => parseAttachmentIds(ticket?.description ?? ""),
    [ticket?.description],
  );

  // ── Shared color-dot renderer for labels & projects ────────────────
  const renderColorDotOption = useCallback((option: SelectOption) => {
    const color = (option.meta?.color as string) ?? "#6B7280";
    const optionBoardId = option.meta?.boardId as string | undefined;
    const optionBoardName = option.meta?.boardName as string | undefined;
    const isFromOtherBoard = optionBoardId && optionBoardId !== boardSelector.selectedBoardId;
    // `min-w-0`: same as the user option -- ellipsise in a narrow trigger.
    return (
      <span className="flex min-w-0 items-center gap-1.5">
        <span
          className="h-2.5 w-2.5 shrink-0"
          style={{ backgroundColor: color }}
        />
        <span className="truncate">{option.label}</span>
        {isFromOtherBoard && optionBoardName && (
          <span className="text-muted-foreground text-xs">{optionBoardName}</span>
        )}
      </span>
    );
  }, [boardSelector.selectedBoardId]);

  // While the edit-mode detail query is in flight (deep-link open,
  // sub-ticket navigation, cache miss), render a skeleton instead of
  // empty form inputs so users don't see flashing empty fields populate.
  // All hooks above run unconditionally to preserve hook order.
  if (isTicketLoading) {
    return <TicketFormSkeleton withTabs={withTabs} />;
  }

  // ── Properties panel rows ──────────────────────────────────────────
  // Declared once and composed into the panel below so the dialog and the
  // full page never drift apart. Every row is label-left / value-right with
  // an `h-9` control.

  /**
   * Board. In create mode it picks the board the ticket is created on (fixed
   * on the backlog page). In edit mode it moves the ticket to another board
   * on save, after the move dialog; a sub-ticket stays on its parent's board.
   */
  const ticketOwnBoardId = ticket?.board?.id;
  const isBoardMovePending =
    mode === "edit" && !!ticketOwnBoardId && boardSelector.selectedBoardId !== ticketOwnBoardId;
  const boardSelect = (
    <SelectFieldStandalone
      orientation="horizontal"
      className={PANEL_CONTROL_CLASS}
      value={boardSelector.selectedBoardId}
      onValueChange={boardSelector.setSelectedBoardId}
      options={boardSelector.boardSelectOptions}
      renderOption={renderBoardOption}
      renderValue={renderBoardValue}
      label={t("ticket.boardField")}
      placeholder={t("ticket.selectBoard")}
      searchPlaceholder={t("common.search")}
      isLoading={boardSelector.isLoading}
      disabled={mode === "edit" && (isReadOnly || !!ticket?.parentTicketId)}
    />
  );
  const boardField =
    mode === "create" ? (
      !isBacklog && boardSelect
    ) : (
      <div className="flex flex-col gap-1">
        {boardSelect}
        {(isBoardMovePending || ticket?.parentTicketId) && (
          <p className="text-muted-foreground text-xs">
            {isBoardMovePending ? t("ticket.moveBoard.pendingHint") : t("ticket.moveBoard.subTicketHint")}
          </p>
        )}
      </div>
    );

  /**
   * Status. Locked to To Do while a top-level ticket sits in the backlog:
   * the select is disabled and the reason lives in the label tooltip rather
   * than a hint line under the control.
   */
  const statusField = (
    <SelectField
      name="status"
      label={t("ticket.statusField")}
      control={control}
      options={statusOptions}
      orientation="horizontal"
      triggerClassName={PANEL_CONTROL_CLASS}
      tooltipText={isBacklogStatusLocked ? t("ticket.backlogStatusHint") : undefined}
      disabled={isReadOnly || isBacklogStatusLocked}
    />
  );

  // A sub-ticket being CREATED has no placement of its own: the backend
  // copies the parent's sprint or backlog and rejects a request that carries
  // either field itself. Editing one is different -- there the field drives
  // a real `POST .../move` -- so only the create case is locked, with the
  // reason in the label tooltip, rather than accepting a value the save
  // would drop on the floor.
  //
  // The control is the shared searchable select (Backlog / Sprints sections,
  // search by sprint name) inside the app's own field wrapper. It is not the
  // shared `SelectField`, because that one's `tooltipText` goes through the
  // `@oba/ui` FormLabel, whose Radix tooltip needs a `TooltipProvider`
  // this app does not mount; the kazan wrapper brings its own.
  //
  // In edit mode a Placement away from the parent is allowed, and the hint
  // under the field says before the save that the ticket becomes standalone.
  const isPlacementLocked = mode === "create" && isSubTicket;
  const placementField = (
    <FormField
      control={control}
      name="sprintId"
      render={({ field }) => (
        <FormFieldWrapper
          label={t("ticket.placementField")}
          orientation="horizontal"
          tooltipText={isPlacementLocked ? t("ticket.subTicketPlacementHint") : undefined}
          description={detachesFromParent ? t("ticket.detachedPlacementHint") : undefined}
        >
          <SelectFieldStandalone
            value={field.value ?? ""}
            onValueChange={field.onChange}
            options={sprintOptions}
            placeholder={t("ticket.actions.selectSprint")}
            searchPlaceholder={t("ticket.fields.searchSprint")}
            className={PANEL_CONTROL_CLASS}
            disabled={isReadOnly || isPlacementLocked}
          />
        </FormFieldWrapper>
      )}
    />
  );

  const typeField = (
    <SelectField
      name="type"
      label={t("ticket.typeField")}
      control={control}
      options={TICKET_TYPES}
      orientation="horizontal"
      triggerClassName={PANEL_CONTROL_CLASS}
      disabled={isReadOnly}
    />
  );

  const priorityField = (
    <SelectField
      name="priority"
      label={t("ticket.priorityField")}
      control={control}
      options={PRIORITIES}
      orientation="horizontal"
      triggerClassName={PANEL_CONTROL_CLASS}
      disabled={isReadOnly}
    />
  );

  const assigneeField = (
    <SharedSelectField
      isMulti
      name="assigneeIds"
      label={t("ticket.assigneeField")}
      control={control}
      options={assigneeOptions}
      placeholder={t("common.unassigned")}
      searchPlaceholder={t("common.search")}
      renderOption={renderUserOption}
      orientation="horizontal"
      className={PANEL_CONTROL_CLASS}
      disabled={isReadOnly}
    />
  );

  const reviewerField = (
    <SharedSelectField
      isMulti
      name="reviewerIds"
      label={t("ticket.fields.reviewer")}
      control={control}
      options={reviewerOptions}
      placeholder={t("common.unassigned")}
      searchPlaceholder={t("common.search")}
      renderOption={renderUserOption}
      orientation="horizontal"
      className={PANEL_CONTROL_CLASS}
      disabled={isReadOnly}
    />
  );

  /**
   * Due date. A compact row like every other property -- it only ever looked
   * full-length because it used to sit alone in a row with a `w-full`
   * trigger. Same date format as `DueDateBadge`; keeps the clear (X) button.
   */
  const dueDateField = (
    <DateField
      name="dueDate"
      label={t("ticket.fields.dueDate")}
      control={control}
      placeholder={t("ticket.fields.noDueDate")}
      dateFormat="MMM d, yyyy"
      orientation="horizontal"
      className={PANEL_CONTROL_CLASS}
      disabled={isReadOnly}
    />
  );

  const projectField = (
    <SharedSelectField
      name="projectId"
      label={t("ticket.fields.project")}
      control={control}
      options={projectOptions}
      placeholder={t("ticket.fields.noProject")}
      searchPlaceholder={t("common.search")}
      renderOption={renderColorDotOption}
      onCreate={handleCreateProject}
      createLabel={(term) => t("ticket.fields.createOption", { name: term })}
      isCreating={createProject.isPending}
      orientation="horizontal"
      className={PANEL_CONTROL_CLASS}
      disabled={isReadOnly}
    />
  );

  const milestoneField = (
    <SharedSelectField
      name="milestoneId"
      label={t("ticket.fields.milestone")}
      control={control}
      options={milestoneOptions}
      placeholder={t("ticket.fields.noMilestone")}
      searchPlaceholder={t("common.search")}
      orientation="horizontal"
      className={PANEL_CONTROL_CLASS}
      contentClassName="w-80 min-w-64 max-w-[calc(100vw-2rem)]"
      disabled={isReadOnly}
    />
  );

  const labelsField = (
    <SharedSelectField
      isMulti
      name="labelIds"
      label={t("ticket.fields.labels")}
      control={control}
      options={labelOptions}
      placeholder={t("ticket.fields.noLabels")}
      searchPlaceholder={t("common.search")}
      renderOption={renderColorDotOption}
      onCreate={handleCreateLabel}
      createLabel={(term) => t("ticket.fields.createOption", { name: term })}
      isCreating={createLabel.isPending}
      orientation="horizontal"
      className={PANEL_CONTROL_CLASS}
      disabled={isReadOnly}
    />
  );

  /**
   * Parent Ticket (for linking as sub-ticket) — hidden when nothing could be
   * a parent, and on a ticket that already has sub-tickets (depth is limited
   * to one). The dropdown is wider than the narrow trigger so long
   * `(KEY) Title` options stay readable.
   */
  const parentField = availableParentTickets.length > 0 && !ticket?.subTickets?.length && (
    <SharedSelectField
      name="parentId"
      label={t("ticket.fields.parent")}
      control={control}
      placeholder={t("ticket.fields.selectParent")}
      searchPlaceholder={t("ticket.fields.searchParent")}
      options={parentTicketOptions}
      orientation="horizontal"
      className={PANEL_CONTROL_CLASS}
      contentClassName="w-80 min-w-64 max-w-[calc(100vw-2rem)]"
      disabled={isReadOnly}
    />
  );

  const weightField = (
    <TextField
      name="weight"
      label={t("ticket.fields.weight")}
      control={control}
      type="number"
      min={1}
      max={100}
      placeholder="1"
      orientation="horizontal"
      className={PANEL_CONTROL_CLASS}
      disabled={isReadOnly}
    />
  );

  const missionRankField = (
    <SelectField
      name="missionRank"
      label={t("ticket.fields.missionRank")}
      control={control}
      required
      options={Object.values(MissionRank).map((rank) => ({
        value: rank,
        label: t(`ticket.missionRank.${rank}`),
      }))}
      orientation="horizontal"
      triggerClassName={PANEL_CONTROL_CLASS}
      disabled={isReadOnly}
    />
  );

  /**
   * Manual progress. Edit mode only: the create request has no `percentage`
   * (the backend ignores it on create), so a field there would be a value
   * the save silently drops.
   */
  const progressField = mode === "edit" && (
    <TextField
      name="percentage"
      label={t("ticket.fields.percentage")}
      control={control}
      type="number"
      min={0}
      max={100}
      suffix="%"
      orientation="horizontal"
      className={PANEL_CONTROL_CLASS}
      disabled={isReadOnly}
    />
  );

  /**
   * The properties panel, in four titled sections:
   *   - Workflow: where the ticket lives and its state
   *   - People:   who works on it
   *   - Details:  what kind of work it is, how it is classified and related
   *   - Planning: when, how big, how far along
   *
   * `gap-3` between sections: with a heading and a divider marking each
   * boundary, the space above a heading only has to be a little more than
   * the `gap-y-2` between rows.
   */
  const propertiesPanel = (
    <aside
      aria-label={t("ticket.properties")}
      className={cn(
        // `@container`: PANEL_GROUP_CLASS sizes its columns off this box.
        "@container flex min-w-0 flex-col gap-3",
        "lg:border-border lg:border-l lg:pl-6",
        // Dialog: pinned to column 2 across both rows, so it sits beside the
        // Title AND the content below it. The page grid has a single row.
        !withTabs && "lg:col-start-2 lg:row-span-2 lg:row-start-1",
      )}
    >
      <PanelSection title={t("ticket.sections.workflow")}>
        {boardField}
        {statusField}
        {placementField}
      </PanelSection>
      <PanelSection title={t("ticket.sections.people")}>
        {assigneeField}
        {reviewerField}
      </PanelSection>
      <PanelSection title={t("ticket.sections.details")}>
        {typeField}
        {priorityField}
        {projectField}
        {labelsField}
        {parentField}
      </PanelSection>
      <PanelSection title={t("ticket.sections.planning")}>
        {milestoneField}
        {dueDateField}
        {weightField}
        {missionRankField}
        {progressField}
      </PanelSection>
    </aside>
  );

  // ── Left pane ──────────────────────────────────────────────────────

  /** Title — key forces remount (and autoFocus) when switching between edit/create */
  const titleField = (
    <TextField
      key={`title-${mode}-${activeTicketId ?? "new"}`}
      name="title"
      label={t("ticket.titleLabel")}
      control={control}
      required
      placeholder={t("ticket.titlePlaceholder")}
      maxLength={255}
      autoFocus
      disabled={isReadOnly}
    />
  );

  /** Description (Markdown Editor) */
  // Wrapped in Kazan's own form field rather than the editor's built-in
  // one, so the label spacing and the translated error message match the
  // other ticket fields.
  const descriptionField = (
    <FormField
      control={control}
      name="description"
      render={({ field, fieldState }) => (
        <FormFieldWrapper label={t("ticket.description")}>
          <MarkdownEditor
            value={field.value ?? ""}
            onChange={field.onChange}
            error={!!fieldState.error}
            components={DESCRIPTION_EDITOR_COMPONENTS}
            placeholder={t("ticket.descriptionPlaceholder")}
            // Materially taller on the page: with attachments moved to their own
            // tab the description is what the Details tab is FOR. In the dialog it
            // grows at `lg+` to roughly the height of the properties panel beside
            // it, so the two panes end near each other instead of leaving a void.
            className={withTabs ? "min-h-[32rem]" : "min-h-[200px] lg:min-h-[18rem]"}
            onFileUpload={onFileUpload}
            onImageDrop={addPendingImage}
            onImageDelete={removePendingImageBySrc}
            disabled={isReadOnly}
          />
        </FormFieldWrapper>
      )}
    />
  );

  /* Attachments.
     Create mode: no ticket id exists yet, so the picker only stages files
     (deferred mode) and the form hook uploads them once the ticket is
     created. The user still sees a single Create step.
     Edit mode: uploads straight against the ticket. */
  // On the page the section is the whole Attachments tab, so it needs no
  // separator; in the dialog it follows the description.
  const attachmentsWrapperClass = withTabs ? "" : "border-border border-t pt-3";
  const attachmentsSection = mode === "create" ? (
    <div className={attachmentsWrapperClass}>
      <AttachmentSection
        entityType={StorageEntityType.TICKET}
        entityId={undefined}
        onStagedFilesChange={onStagedAttachmentsChange}
        readOnly={isReadOnly}
      />
    </div>
  ) : mode === "edit" && ticket && (
    <div className={attachmentsWrapperClass}>
      <AttachmentSection
        entityType={StorageEntityType.TICKET}
        entityId={ticket.id}
        excludeIds={descriptionAttachmentIds}
        // Fail-closed on purpose: `!canEdit`, not `isReadOnly`.
        // `isReadOnly` is `currentUserRole === "VIEWER"`, which is false
        // while the role is still unknown, so it would flash a live upload
        // dropzone at a VIEWER. `useBoardRole.isLoading` does not close that
        // window either -- it tracks only useBoardMembers, so `me` still
        // being in flight leaves `currentUserRole` undefined with isLoading
        // already false. `canEdit` is EDIT_ROLES.includes(currentUserRole),
        // i.e. false for undefined, so it denies during BOTH load windows
        // and for a non-member. Same signal the sub-ticket list is gated on.
        readOnly={!canEdit}
      />
    </div>
  );

  /** Sub-tickets (edit mode only, not for sub-tickets — depth limited to 1) */
  const subTicketsSection = mode === "edit" && ticket && !ticket.parentTicketId && (
    <div className="border-border border-t pt-3">
      <Label className="text-foreground mb-2 block text-xs font-semibold">
        {t("ticket.subTickets.title")}
      </Label>
      <SubTicketList
        subTickets={subTickets}
        onCreate={subTicketActions.onCreate}
        onUpdateTitle={subTicketActions.onUpdateTitle}
        onToggleComplete={subTicketActions.onToggleComplete}
        onDelete={subTicketActions.onDelete}
        onReorder={subTicketActions.onReorder}
        isReorderLocked={isSubTicketReorderLocked}
        onOpenSubTicket={subTicketActions.onOpenSubTicket}
        onRequestCreateSubTicket={subTicketActions.onRequestCreate}
        isCreating={isCreatingSubTicket}
        currentBoard={currentBoard}
        canEditTickets={canEdit}
      />
    </div>
  );

  /**
   * Danger Zone (edit mode only). Bottom of the LEFT pane, not the panel: a
   * destructive action does not belong between the ticket's properties.
   */
  const dangerZone = mode === "edit" && currentUserRole !== "VIEWER" && (
    <div className="border-border border-t">
      <DangerZone
        title={t("ticket.dangerZone")}
        description={t("ticket.deleteWarning")}
        onDelete={onDeleteTicket}
        disabled={isDeleting || isSaving}
        buttonText={isDeleting ? t("ticket.deleting") : t("ticket.deleteButton")}
      />
    </div>
  );

  // Both shells put the footer last in a fixed-height flex column, so it is
  // pinned by the layout itself — the scrolling body is its sibling above.
  const footer = (
    <div className="border-border bg-muted flex shrink-0 flex-col-reverse gap-2 border-t px-4 py-3 sm:flex-row sm:justify-end sm:gap-0 sm:space-x-2 md:px-6">
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={() => onOpenChange(false)}
        disabled={isSaving}
      >
        {t("common.cancel")}
      </Button>
      {currentUserRole !== "VIEWER" && (
        <Button
          type="submit"
          size="sm"
          disabled={
            isSaving ||
            !watch("title")?.trim() ||
            isLoading ||
            isBacklogPending ||
            (mode === "create" && !boardSelector.selectedBoardId)
          }
        >
          {isSaving
            ? t("common.saving")
            : mode === "create"
              ? t("common.create")
              : t("common.save")}
        </Button>
      )}
    </div>
  );

  // ── Composition ────────────────────────────────────────────────────
  // ONE scroll region for both panes: a single scrollbar for the whole
  // ticket body, and the panel is deliberately not sticky. `min-h-0` lets
  // this flex child shrink under the form column's height so
  // `overflow-y-auto` fires.

  if (withTabs) {
    return (
      <>
        <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4 md:px-6">
          <div className={PANES_CLASS}>
            {/*
              Left pane, split into pill tabs. The properties panel stays
              OUTSIDE the tabs on purpose: status, sprint, assignee and the
              rest are context the user needs whichever tab is open, and
              rendering them per tab would put the same RHF-registered field
              in the tree twice.

              `forceMount` on both panels: the description is a TipTap editor
              and the title input carries `autoFocus`, so letting Radix unmount
              the inactive panel would re-initialise the editor (losing its
              undo history) and steal focus back to the title on every return
              to Details. Radix does NOT hide a force-mounted panel for you —
              it renders `hidden={!present}` and `present` is true whenever
              `forceMount` is set, so both panels would be visible — hence the
              explicit `data-[state=inactive]:hidden` on each one.
            */}
            <div className="min-w-0">
              <Tabs
                value={activeTab}
                onValueChange={(value) => void setActiveTab(value as TicketPageTab)}
                className="flex flex-col gap-4"
              >
                <TabsList className="w-fit self-start">
                  <TabsTrigger value="details">{t("ticket.tabs.details")}</TabsTrigger>
                  <TabsTrigger value="attachments">
                    {t("ticket.tabs.attachments")}
                  </TabsTrigger>
                </TabsList>

                <TabsContent
                  value="details"
                  forceMount
                  className="mt-0 space-y-4 data-[state=inactive]:hidden"
                >
                  {titleField}
                  {descriptionField}
                  {subTicketsSection}
                  {dangerZone}
                </TabsContent>

                <TabsContent
                  value="attachments"
                  forceMount
                  className="mt-0 data-[state=inactive]:hidden"
                >
                  {attachmentsSection}
                </TabsContent>
              </Tabs>
            </div>

            {propertiesPanel}
          </div>
        </div>

        {footer}
      </>
    );
  }

  // Dialog. One grid whose DOM order is the narrow-screen reading order --
  // Title, properties, then the content -- so tab order follows what the user
  // sees there. At `lg+` the panel moves to column 2 and spans both rows. The
  // second row is `1fr` on purpose: a spanning item's extra height is
  // distributed to flexible tracks only, so a panel taller than the Title
  // grows the content row instead of pushing a gap in under the Title.
  return (
    <>
      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4 md:px-6">
        <div className={cn(PANES_CLASS, "lg:grid-rows-[auto_1fr]")}>
          <div className="min-w-0 lg:col-start-1 lg:row-start-1">{titleField}</div>

          {propertiesPanel}

          <div className="flex min-w-0 flex-col gap-4 lg:col-start-1 lg:row-start-2">
            {descriptionField}
            {attachmentsSection}
            {subTicketsSection}
            {dangerZone}
          </div>
        </div>
      </div>

      {footer}
    </>
  );
}
