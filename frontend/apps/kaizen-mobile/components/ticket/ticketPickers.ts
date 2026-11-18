import type { ActionSheetAction } from "@/components/kaizen/ActionSheet";
import { TICKET_TYPES } from "@/components/kaizen/ticketMetaModel";
import { parentOptionLabel } from "@/components/boards/boardForms";
import type { BoardRow } from "@/components/boards/boardModel";
import { DEFAULT_TICKET_STATUS } from "@/components/ticket/newTicketModel";
import {
  priorityChoices,
  RANK_CHOICES,
  STATUS_CHOICES,
} from "@/components/ticket/ticketActionsModel";
import { sprintChoices, type SprintChoice } from "@/components/ticket/ticketFieldsModel";
import type { TranslationKey } from "@/i18n";
import type { BoardOption, Milestone, Sprint } from "@/lib/schemas/boards";

/*
 * The choice lists of the ticket screen. Each one takes the current value
 * and what to do with a pick, so a saved ticket writes the pick and a new
 * ticket keeps it in its draft. The type, status, priority and mission rank
 * are a ChoicePicker, shown in the choice sheet with each value as its chip and a
 * check on the current one; the board lists (sprint, milestone, project,
 * board) are a TicketPicker in an action sheet, with the current value greyed
 * out.
 */

/** A list of choices the screen shows in an action sheet, such as the sprints. */
export interface TicketPicker {
  title: string;
  actions: ActionSheetAction[];
}

/** What a choice sheet offers; each kind is drawn as the chip the app shows it with. */
export type ChoiceKind = "type" | "status" | "priority" | "rank";

/** One value of a ChoicePicker. */
export interface ChoiceOption {
  value: string;
  /** Shown but not pickable, such as a status a backlog ticket cannot have. */
  disabled?: boolean;
  /** Why the option is disabled, shown under the list. */
  reason?: string;
  /** More about the value next to its chip, such as a rank's effort. */
  detail?: string;
  /** What the value is worth, at the end of its row, such as a rank's leaderboard points. */
  trailing?: string;
}

/** A choice sheet of one ticket value, such as its status. */
export interface ChoicePicker {
  title: string;
  kind: ChoiceKind;
  /** The value the ticket has now; it carries the check. */
  current: string | null;
  options: ChoiceOption[];
  /** Receives a pick only when it is enabled and differs from `current`. */
  onPick: (value: string) => void;
}

/** The app's typed translator, as `useTranslation` returns it. */
type Translate = (key: TranslationKey, options?: Record<string, unknown>) => string;

/**
 * Every status. A top-level backlog ticket is always Todo, so with `locked`
 * the other statuses are listed disabled, with the reason.
 */
export function statusPicker(
  t: Translate,
  current: string,
  onPick: (status: string) => void,
  locked: boolean = false
): ChoicePicker {
  return {
    title: t("ticketChangeStatus"),
    kind: "status",
    current,
    options: STATUS_CHOICES.map((status) =>
      locked && status !== DEFAULT_TICKET_STATUS
        ? { value: status, disabled: true, reason: t("ticketStatusBacklogTodo") }
        : { value: status }
    ),
    onPick,
  };
}

/** The priorities of the ticket's type, most urgent first. */
export function priorityPicker(
  t: Translate,
  type: string,
  current: string | null,
  onPick: (priority: string) => void
): ChoicePicker {
  return {
    title: t("ticketSetPriority"),
    kind: "priority",
    current,
    options: priorityChoices(type).map((priority) => ({ value: priority })),
    onPick,
  };
}

/**
 * The mission ranks, smallest first, each with its effort and the
 * leaderboard points a done ticket of that rank earns. There is no way to
 * clear the rank, because every ticket needs one.
 */
export function rankPicker(
  t: Translate,
  current: string | null,
  onPick: (rank: string) => void
): ChoicePicker {
  return {
    title: t("ticketSizingTitle"),
    kind: "rank",
    current,
    options: RANK_CHOICES.map((choice) => ({
      value: choice.rank,
      detail: t(choice.sizeKey),
      trailing: t("homePoints", { count: choice.points, points: choice.points }),
    })),
    onPick,
  };
}

/** Every ticket type. */
export function typePicker(
  t: Translate,
  current: string,
  onPick: (type: string) => void
): ChoicePicker {
  return {
    title: t("newTicketType"),
    kind: "type",
    current,
    options: TICKET_TYPES.map((type) => ({ value: type })),
    onPick,
  };
}

/**
 * The value a tap on `value` hands to `onPick`, or null when the tap only
 * closes the sheet: the option is the current value, disabled or unknown.
 */
export function choiceToApply(picker: ChoicePicker, value: string): string | null {
  const option: ChoiceOption | undefined = picker.options.find((item) => item.value === value);
  if (!option || option.disabled || value === picker.current) return null;
  return value;
}

/** The reasons of the disabled options, each once, in list order, for the note under the list. */
export function disabledReasons(options: readonly ChoiceOption[]): string[] {
  const reasons: string[] = [];
  for (const option of options) {
    if (option.disabled && option.reason && !reasons.includes(option.reason)) {
      reasons.push(option.reason);
    }
  }
  return reasons;
}

/** The backlog, then the board's open sprints with their state. */
export function sprintPicker(
  t: Translate,
  sprints: readonly Sprint[],
  current: string,
  onPick: (choice: SprintChoice) => void
): TicketPicker {
  const label = (choice: SprintChoice): string => {
    if (!choice.sprint) return t("ticketBacklog");
    const state: string =
      choice.sprint.status === "ACTIVE" ? t("boardSprintActive") : t("boardSprintPlanned");
    return `${choice.sprint.name} · ${state}`;
  };
  return {
    title: t("ticketFieldSprint"),
    actions: sprintChoices(sprints).map((choice) => ({
      label: label(choice),
      disabled: choice.id === current,
      onPress: () => onPick(choice),
    })),
  };
}

/**
 * The board's milestones, and "No milestone" while one is set. Null when the
 * board has none and none is set, so the caller can say so instead.
 */
export function milestonePicker(
  t: Translate,
  milestones: readonly Milestone[],
  currentId: string | null,
  onPick: (milestone: Milestone) => void,
  onClear: () => void
): TicketPicker | null {
  if (milestones.length === 0 && !currentId) return null;
  return {
    title: t("ticketFieldMilestone"),
    actions: [
      ...milestones.map((milestone) => ({
        label: milestone.name,
        disabled: milestone.id === currentId,
        onPress: () => onPick(milestone),
      })),
      ...(currentId ? [{ label: t("ticketMilestoneRemove"), onPress: onClear }] : []),
    ],
  };
}

/**
 * The board's projects. "No project" is offered only with `onClear`: the
 * API cannot take a project off a saved ticket, while a new one can drop
 * its pick. Null when the board has no projects.
 */
export function projectPicker(
  t: Translate,
  projects: readonly BoardOption[],
  currentId: string | null,
  onPick: (project: BoardOption) => void,
  onClear?: () => void
): TicketPicker | null {
  if (projects.length === 0) return null;
  return {
    title: t("ticketFieldProject"),
    actions: [
      ...projects.map((project) => ({
        label: project.name,
        disabled: project.id === currentId,
        onPress: () => onPick(project),
      })),
      ...(currentId && onClear ? [{ label: t("ticketProjectRemove"), onPress: onClear }] : []),
    ],
  };
}

/** Every board of the tree, child boards under their parent. */
export function boardPicker(
  t: Translate,
  rows: readonly BoardRow[],
  currentId: string | null,
  onPick: (boardId: string) => void
): TicketPicker {
  return {
    title: t("newTicketBoard"),
    actions: rows.map((row) => ({
      label: parentOptionLabel(row),
      disabled: row.id === currentId,
      onPress: () => onPick(row.id),
    })),
  };
}
