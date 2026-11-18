import { personName, type BoardRow } from "@/components/boards/boardModel";
import {
  BOARD_NAME_MAX,
  BOARD_PREFIX_MAX,
  BOARD_PREFIX_PATTERN,
  type BoardMember,
  type BoardTreeItem,
} from "@/lib/schemas/boards";

/*
 * The pure half of the board forms and the Filters sheet: field rules, the
 * assignee order, the result count and the slug lookup. No React or native
 * import, so `bun test` covers it.
 */

// ── New board and board settings ─────────────────────────────────

/** Why a board field is refused, before the request is sent. */
export type BoardFieldProblem = "required" | "too-long" | "pattern";

/** Why a board name is refused; a name has no character rule. */
export type BoardNameProblem = Exclude<BoardFieldProblem, "pattern">;

/** A board name: required and at most 255 characters, as the API checks it. */
export function boardNameProblem(name: string): BoardNameProblem | null {
  const trimmed: string = name.trim();
  if (!trimmed) return "required";
  if (trimmed.length > BOARD_NAME_MAX) return "too-long";
  return null;
}

/**
 * A board key: required, at most five characters, upper-case letters, digits
 * and underscores only, as the web app's create-board form checks it. Every
 * ticket key of the board starts with it, so it stays short and plain.
 */
export function boardPrefixProblem(prefix: string): BoardFieldProblem | null {
  if (!prefix) return "required";
  if (prefix.length > BOARD_PREFIX_MAX) return "too-long";
  if (!BOARD_PREFIX_PATTERN.test(prefix)) return "pattern";
  return null;
}

/** A typed key in upper case without spaces, as the field shows it. */
export function normalizePrefix(input: string): string {
  return input.replace(/\s+/g, "").toUpperCase();
}

/** The New board fields that are checked before the request is sent. */
export type NewBoardValues = { name: string; prefix: string };

/** Which New board fields an edit checks again, so their errors are replaced. */
export type NewBoardRecheck = { name: boolean; prefix: boolean };

/**
 * The fields an edit of the New board form checks again, with the web app's
 * create-board timing: no error shows before the first create, except under a
 * key the user types, and after it every field whose value changed is checked
 * on each change. A key the name fills in is a change of the key too, so an
 * error never describes a key the field no longer holds. A field whose value
 * did not change keeps its error, including a key the server refused as taken.
 */
export function newBoardRecheck(
  before: NewBoardValues,
  after: NewBoardValues,
  when: { submitted: boolean; typedKey: boolean }
): NewBoardRecheck {
  return {
    name: when.submitted && after.name !== before.name,
    prefix: (when.submitted || when.typedKey) && after.prefix !== before.prefix,
  };
}

/**
 * The parent board choices as menu labels, "(KEY) Name", indented by depth
 * so the tree stays readable in a flat menu.
 */
export function parentOptionLabel(row: BoardRow): string {
  const indent: string = "  ".repeat(row.depth);
  return row.prefix ? `${indent}(${row.prefix}) ${row.name}` : `${indent}${row.name}`;
}

// ── Filters ──────────────────────────────────────────────────────

/** Assignee chips shown before "+ N more". */
export const VISIBLE_ASSIGNEES = 8;

/** A person a ticket can be filtered by. */
export interface AssigneeOption {
  id: string;
  name: string;
  isMe: boolean;
}

/** The board's members as assignee choices: the signed-in user first, then by name. */
export function assigneeOptions(
  members: readonly BoardMember[],
  meId: string | undefined
): AssigneeOption[] {
  const options: AssigneeOption[] = members.map((member) => ({
    id: member.userId,
    name: personName(member),
    isMe: member.userId === meId,
  }));
  return options.sort((a, b) => Number(b.isMe) - Number(a.isMe) || a.name.localeCompare(b.name));
}

/**
 * The assignee chips to show: the first few, plus every selected one so a
 * chosen person never hides behind "+ N more".
 */
export function visibleAssignees(
  options: readonly AssigneeOption[],
  selected: readonly string[],
  expanded: boolean
): { shown: AssigneeOption[]; hidden: number } {
  if (expanded || options.length <= VISIBLE_ASSIGNEES) return { shown: [...options], hidden: 0 };
  const shown: AssigneeOption[] = options.filter(
    (option, index) => index < VISIBLE_ASSIGNEES || selected.includes(option.id)
  );
  return { shown, hidden: options.length - shown.length };
}

/**
 * The tickets the filters would show: the sum over the view's lists, or null
 * until every list has loaded, so the count never undercounts.
 */
export function filterResultCount(
  lists: readonly { enabled: boolean; data: readonly unknown[] | undefined }[]
): number | null {
  let total: number = 0;
  for (const list of lists) {
    if (!list.enabled) continue;
    if (!list.data) return null;
    total += list.data.length;
  }
  return total;
}

// ── Links from other tabs ────────────────────────────────────────

/** The id of the board with a slug, searched through the board tree; null when absent. */
export function boardIdBySlug(tree: readonly BoardTreeItem[], slug: string): string | null {
  for (const board of tree) {
    if (board.slug === slug) return board.id;
    const child: string | null = boardIdBySlug(board.childBoards ?? [], slug);
    if (child) return child;
  }
  return null;
}
