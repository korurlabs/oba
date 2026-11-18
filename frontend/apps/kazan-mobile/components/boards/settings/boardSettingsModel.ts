import { parentCandidates, personName, type BoardRow } from "@/components/boards/boardModel";
import { parseDateOnly } from "@/lib/dates";
import { hasKazanAccess } from "@/lib/kazanAccess";
import {
  ASSIGNABLE_BOARD_ROLES,
  DEFAULT_OPTION_COLOR,
  HEX_COLOR_PATTERN,
  MILESTONE_DESCRIPTION_MAX,
  MILESTONE_NAME_MAX,
  type AssignableBoardRole,
  type BoardMember,
  type BoardMemberCandidate,
  type BoardOwnedOption,
  type BoardOptionBody,
  type Milestone,
  type MilestoneCreateBody,
  type MilestoneDetail,
  type MilestoneUpdateBody,
} from "@/lib/schemas/boards";

/*
 * The pure half of Board settings: label and project rows, the milestone form,
 * Add member's people, the member role actions, the child board list and Add
 * existing boards. Each rule is Kazan web's for the same screen. No React or
 * native import, so `bun test` covers it.
 */

// ── Roles ────────────────────────────────────────────────────────

/** OWNER and ADMIN manage a board: its settings, members and child boards. */
export function isBoardManageRole(role: string | null | undefined): boolean {
  return role === "OWNER" || role === "ADMIN";
}

// ── Labels and projects ──────────────────────────────────────────

/** A label or project as a row of its list. */
export interface BoardOptionRow {
  option: BoardOwnedOption;
  /** True for an option of a related board; it is written under that board. */
  related: boolean;
  /** The owning board's name, shown under an option of a related board; null for this board's own. */
  ownerBoardName: string | null;
}

/**
 * The rows of `GET /labels` or `GET /projects`, one list in the server's
 * order: this board's options and its related boards', each marked by the
 * board that owns it.
 */
export function boardOptionRows(
  options: readonly BoardOwnedOption[],
  boardId: string
): BoardOptionRow[] {
  return options.map((option) => {
    const related: boolean = option.boardId !== boardId;
    return { option, related, ownerBoardName: related ? option.boardName : null };
  });
}

/** The values a label or project sheet opens with: the option's, or an empty name in blue. */
export function boardOptionDefaults(option: BoardOwnedOption | null): {
  name: string;
  color: string;
} {
  return option
    ? { name: option.name, color: option.color ?? DEFAULT_OPTION_COLOR }
    : { name: "", color: DEFAULT_OPTION_COLOR };
}

/**
 * Why a label or project name is refused before sending: only a blank name.
 * The web checks neither length nor duplicates; the server answers those.
 */
export function boardOptionNameProblem(name: string): "required" | null {
  return name.trim() ? null : "required";
}

/** The create and update body, with the name trimmed as the web sends it. */
export function boardOptionBody(name: string, color: string): BoardOptionBody {
  return { name: name.trim(), color };
}

// ── Milestones ───────────────────────────────────────────────────

/** Milestones by target date, then name; a milestone without a target date comes last. */
function byTargetThenName(a: Milestone, b: Milestone): number {
  const targetA: string = a.targetDate ?? "￿";
  const targetB: string = b.targetDate ?? "￿";
  if (targetA !== targetB) return targetA < targetB ? -1 : 1;
  return a.name.localeCompare(b.name);
}

/** The Open and Closed sections of the milestone list, each by target date, then name. */
export function milestoneSections(milestones: readonly Milestone[]): {
  open: Milestone[];
  closed: Milestone[];
} {
  const sorted: Milestone[] = [...milestones].sort(byTargetThenName);
  return {
    open: sorted.filter((milestone) => milestone.status !== "CLOSED"),
    closed: sorted.filter((milestone) => milestone.status === "CLOSED"),
  };
}

/** A milestone's dot: its own colour, else its board's. */
export function milestoneDotColor(
  milestone: Pick<Milestone, "color">,
  boardColor: string | null | undefined
): string | null {
  return milestone.color ?? boardColor ?? null;
}

/** The fields of the milestone sheet. Dates are date-only `YYYY-MM-DD`; null when not picked. */
export interface MilestoneFormValues {
  name: string;
  description: string;
  startDate: string | null;
  targetDate: string | null;
  color: string | null;
}

/** The web's milestone form messages, by field; each names a `milestone.form.*` text. */
export interface MilestoneFormProblems {
  name?: "nameRequired" | "nameTooLong";
  description?: "descriptionTooLong";
  startDate?: "startAfterTarget";
  targetDate?: "targetDateRequired";
}

/** A `YYYY-MM-DD` day, or null for anything else. */
function dayOrNull(value: string | null | undefined): string | null {
  return value && parseDateOnly(value) ? value : null;
}

/**
 * The values the milestone sheet opens with. A new milestone starts in its
 * board's colour when that is a `#RRGGBB` colour, as the server would store
 * it. An edited one starts from its detail; a target that is not a day is
 * left empty, so the required message shows and the user picks it again.
 */
export function milestoneFormDefaults(
  milestone: MilestoneDetail | null,
  boardColor: string | null | undefined
): MilestoneFormValues {
  if (!milestone) {
    return {
      name: "",
      description: "",
      startDate: null,
      targetDate: null,
      color: boardColor && HEX_COLOR_PATTERN.test(boardColor) ? boardColor : null,
    };
  }
  return {
    name: milestone.name,
    description: milestone.description ?? "",
    startDate: dayOrNull(milestone.startDate),
    targetDate: dayOrNull(milestone.targetDate),
    color: milestone.color ?? null,
  };
}

/**
 * The web form's rules: a name of 1 to 255 characters once trimmed, a
 * description of at most 2000, a target date, and a start on or before it.
 */
export function milestoneFormProblems(values: MilestoneFormValues): MilestoneFormProblems {
  const problems: MilestoneFormProblems = {};
  const name: string = values.name.trim();
  if (!name) problems.name = "nameRequired";
  else if (name.length > MILESTONE_NAME_MAX) problems.name = "nameTooLong";
  if (values.description.length > MILESTONE_DESCRIPTION_MAX) {
    problems.description = "descriptionTooLong";
  }
  if (!values.targetDate) problems.targetDate = "targetDateRequired";
  else if (values.startDate && values.startDate > values.targetDate) {
    problems.startDate = "startAfterTarget";
  }
  return problems;
}

/** True when the milestone sheet may be sent. */
export function isMilestoneFormValid(values: MilestoneFormValues): boolean {
  return Object.keys(milestoneFormProblems(values)).length === 0;
}

/**
 * The web's create body: the trimmed name, a trimmed description only when it
 * holds text, the start only when picked, the colour only when one is chosen.
 * Call it only for valid values.
 */
export function milestoneCreateBody(values: MilestoneFormValues): MilestoneCreateBody {
  const description: string = values.description.trim();
  const body: MilestoneCreateBody = {
    name: values.name.trim(),
    targetDate: values.targetDate ?? "",
  };
  if (description) body.description = description;
  if (values.startDate) body.startDate = values.startDate;
  if (values.color) body.color = values.color;
  return body;
}

/**
 * The web's update body. The API keeps an omitted field, so an emptied
 * description or start date is sent as its clear flag, and only when the
 * milestone had one. The colour is sent only when it changed: the API cannot
 * clear a colour. Call it only for valid values.
 */
export function milestoneUpdateBody(
  values: MilestoneFormValues,
  milestone: MilestoneDetail
): MilestoneUpdateBody {
  const description: string = values.description.trim();
  const body: MilestoneUpdateBody = {
    name: values.name.trim(),
    targetDate: values.targetDate ?? "",
  };
  if (description) body.description = description;
  else if (milestone.description) body.clearDescription = true;
  if (values.startDate) body.startDate = values.startDate;
  else if (milestone.startDate) body.clearStartDate = true;
  if (values.color && values.color !== milestone.color) body.color = values.color;
  return body;
}

// ── Members ──────────────────────────────────────────────────────

/** A candidate's full name, or the email when they have none. */
export function candidateName(candidate: BoardMemberCandidate): string {
  return personName({
    firstName: candidate.firstName,
    lastName: candidate.lastName,
    email: candidate.email,
  });
}

/**
 * The subtitle of a candidate's row: the email when the title is the name, and
 * for a candidate without Kazan the reason they cannot be added on the next
 * line, so VoiceOver and TalkBack read why the row is disabled.
 */
export function candidateSubtitle(
  candidate: BoardMemberCandidate,
  noAccessHint: string
): string | undefined {
  const lines: string[] = [];
  if (candidateName(candidate) !== candidate.email) lines.push(candidate.email);
  if (!hasKazanAccess(candidate)) lines.push(noAccessHint);
  return lines.length > 0 ? lines.join("\n") : undefined;
}

/**
 * The people Add member offers: the candidates who are not on the board yet,
 * matching the search in "first last" or the email, ignoring case, in the
 * server's order, as the web filters them. Candidates without Kazan stay in
 * the list, so the manager sees why they cannot be added.
 */
export function memberCandidates(
  candidates: readonly BoardMemberCandidate[],
  members: readonly BoardMember[],
  search: string
): BoardMemberCandidate[] {
  const memberIds: Set<string> = new Set(members.map((member) => member.userId));
  const needle: string = search.toLowerCase();
  return candidates.filter((candidate) => {
    if (memberIds.has(candidate.userId)) return false;
    if (!needle) return true;
    const name: string = `${candidate.firstName ?? ""} ${candidate.lastName ?? ""}`.toLowerCase();
    return name.includes(needle) || candidate.email.toLowerCase().includes(needle);
  });
}

/** What a manager may do with a member: give one of the roles, or remove them. */
export interface MemberActions {
  roles: { role: AssignableBoardRole; selected: boolean }[];
}

/**
 * The actions on a member row: the three roles, the current one selected, and
 * Remove. Only a manager gets them, and never on the OWNER row, as on the web.
 */
export function memberActions(member: BoardMember, canManage: boolean): MemberActions | null {
  if (!canManage || member.role === "OWNER") return null;
  return {
    roles: ASSIGNABLE_BOARD_ROLES.map((role) => ({ role, selected: role === member.role })),
  };
}

// ── Child boards ─────────────────────────────────────────────────

/** A descendant of the board in its Child boards list. */
export interface ChildBoardRow {
  board: BoardRow;
  /** Levels below the board's direct children: 0 for a direct child. */
  indent: number;
  /** True for a direct child, the only kind Remove applies to. */
  direct: boolean;
}

/** The rows of the board's subtree after the board itself, in tree order. */
function subtreeRows(rows: readonly BoardRow[], boardId: string): BoardRow[] {
  const index: number = rows.findIndex((row) => row.id === boardId);
  if (index < 0) return [];
  const depth: number = rows[index]!.depth;
  const subtree: BoardRow[] = [];
  for (let next = index + 1; next < rows.length && rows[next]!.depth > depth; next += 1) {
    subtree.push(rows[next]!);
  }
  return subtree;
}

/** Every descendant of the board, from the flattened board tree, indented per level. */
export function childBoardRows(rows: readonly BoardRow[], boardId: string): ChildBoardRow[] {
  const board: BoardRow | undefined = rows.find((row) => row.id === boardId);
  if (!board) return [];
  return subtreeRows(rows, boardId).map((row) => ({
    board: row,
    indent: row.depth - board.depth - 1,
    direct: row.parentId === boardId,
  }));
}

/**
 * Remove is offered on a direct child to a manager of the board who is also
 * OWNER or ADMIN of that child, the role the server checks for it. `roles`
 * holds the reader's role per board (`useBoardRoles`).
 */
export function canRemoveChild(
  child: ChildBoardRow,
  canManage: boolean,
  roles: ReadonlyMap<string, string>
): boolean {
  return canManage && child.direct && isBoardManageRole(roles.get(child.board.id));
}

/**
 * The boards Add existing may list: every board of the tree except the board
 * and its descendants, which would close a loop. Its ancestors stay, as on
 * the web.
 */
export function addExistingBoardRows(rows: readonly BoardRow[], boardId: string): BoardRow[] {
  return parentCandidates(rows, boardId);
}

/** The descendants of the picked boards, which come along with them and leave the list. */
export function hiddenDescendantIds(
  rows: readonly BoardRow[],
  pickedIds: readonly string[]
): Set<string> {
  const hidden: Set<string> = new Set();
  for (const id of pickedIds) {
    for (const row of subtreeRows(rows, id)) hidden.add(row.id);
  }
  return hidden;
}

/** The picks without any board a picked ancestor already brings, in pick order. */
export function normalizePicks(rows: readonly BoardRow[], pickedIds: readonly string[]): string[] {
  const hidden: Set<string> = hiddenDescendantIds(rows, pickedIds);
  return pickedIds.filter((id) => !hidden.has(id));
}

/** Picks or unpicks a board; picking a board drops the picks of its descendants. */
export function togglePick(
  rows: readonly BoardRow[],
  pickedIds: readonly string[],
  boardId: string
): string[] {
  if (pickedIds.includes(boardId)) return pickedIds.filter((id) => id !== boardId);
  return normalizePicks(rows, [...pickedIds, boardId]);
}

/** A board of the Add existing list. */
export interface AddExistingCandidate {
  board: BoardRow;
  /**
   * The reader's MEMBER or VIEWER role on the board, which keeps it from
   * being picked, shown as the reason; null for a board that can be picked.
   */
  blockingRole: string | null;
}

/**
 * The Add existing list: `addExistingBoardRows` without the picked boards'
 * descendants. A board where the reader is MEMBER or VIEWER is disabled with
 * that role; a board the reader is not a member of stays enabled, as on the
 * web, and the server decides.
 */
export function addExistingCandidates(
  rows: readonly BoardRow[],
  pickedIds: readonly string[],
  roles: ReadonlyMap<string, string>
): AddExistingCandidate[] {
  const hidden: Set<string> = hiddenDescendantIds(rows, pickedIds);
  return rows
    .filter((row) => !hidden.has(row.id))
    .map((row) => {
      const role: string | undefined = roles.get(row.id);
      return { board: row, blockingRole: role && !isBoardManageRole(role) ? role : null };
    });
}

/** A row of the Add existing preview. */
export interface AddExistingPreviewRow {
  board: BoardRow;
  /** True for a picked board; false for a descendant that comes along ("included"). */
  picked: boolean;
  /** Levels below the picked board it comes with: 0 for the picked board. */
  indent: number;
}

/** Each picked board with its descendants, in tree order, as the web previews them. */
export function addExistingPreview(
  rows: readonly BoardRow[],
  pickedIds: readonly string[]
): AddExistingPreviewRow[] {
  const picked: Set<string> = new Set(pickedIds);
  const preview: AddExistingPreviewRow[] = [];
  const added: Set<string> = new Set();
  for (const row of rows) {
    if (!picked.has(row.id) || added.has(row.id)) continue;
    preview.push({ board: row, picked: true, indent: 0 });
    added.add(row.id);
    for (const descendant of subtreeRows(rows, row.id)) {
      if (added.has(descendant.id)) continue;
      preview.push({ board: descendant, picked: false, indent: descendant.depth - row.depth });
      added.add(descendant.id);
    }
  }
  return preview;
}
