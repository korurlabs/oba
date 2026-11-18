import { TICKET_STATUSES, type TicketStatus } from "@/components/kaizen/ticketMetaModel";
import { addDaysToDateOnly, currentWeekRange, daysBetween, toDateOnly } from "@/lib/dates";
import type { BoardTicket, BoardTreeItem, Milestone, Sprint } from "@/lib/schemas/boards";

/*
 * The pure half of the Boards tab: the board tree, the view and filter route
 * params, the ticket list query, sprint choice, grouping and sorting, and the
 * roadmap geometry. No React or native import, so `bun test` covers it.
 */

// ── Board tree ───────────────────────────────────────────────────

/** A board as a row of the Boards list: its depth in the tree and its parent. */
export interface BoardRow {
  id: string;
  name: string;
  prefix: string | null;
  slug: string;
  color: string | null;
  /** The icon name the board was given, such as `lock`; null when none. */
  icon: string | null;
  depth: number;
  parentId: string | null;
  parentName: string | null;
  childCount: number;
}

function byName(a: { name: string }, b: { name: string }): number {
  return a.name.localeCompare(b.name);
}

/**
 * The board order the user arranged in Kaizen web's sidebar, kept in
 * `userPreferences.sidebar`: top-level boards, and child boards per parent.
 */
export interface SavedBoardOrder {
  boardOrder: readonly string[];
  childBoardOrder: Readonly<Record<string, readonly string[]>>;
}

/**
 * Siblings in the saved order: the boards the order lists first, as listed,
 * then the others by name, as boards created since the order was saved.
 */
function sortSiblings(boards: readonly BoardTreeItem[], order: readonly string[]): BoardTreeItem[] {
  const rank: Map<string, number> = new Map();
  for (const id of order) if (!rank.has(id)) rank.set(id, rank.size);
  return [...boards].sort((a, b) => {
    const rankA: number | undefined = rank.get(a.id);
    const rankB: number | undefined = rank.get(b.id);
    if (rankA !== undefined && rankB !== undefined) return rankA - rankB;
    if (rankA !== undefined) return -1;
    if (rankB !== undefined) return 1;
    return byName(a, b);
  });
}

/**
 * The board tree as rows, depth first: each top-level board, then its child
 * boards under it. Siblings follow the saved sidebar order when one is given
 * and by name otherwise, so the list matches the web sidebar.
 */
export function flattenBoardTree(
  tree: readonly BoardTreeItem[],
  order?: SavedBoardOrder
): BoardRow[] {
  const rows: BoardRow[] = [];
  const visit = (board: BoardTreeItem, depth: number, parent: BoardTreeItem | null): void => {
    const children: BoardTreeItem[] = sortSiblings(
      board.childBoards ?? [],
      order?.childBoardOrder[board.id] ?? []
    );
    rows.push({
      id: board.id,
      name: board.name,
      prefix: board.prefix ?? null,
      slug: board.slug,
      color: board.color ?? null,
      icon: board.icon ?? null,
      depth,
      parentId: parent?.id ?? null,
      parentName: parent?.name ?? null,
      childCount: children.length,
    });
    for (const child of children) visit(child, depth + 1, board);
  };
  for (const board of sortSiblings(tree, order?.boardOrder ?? [])) visit(board, 0, null);
  return rows;
}

/** Rows whose name or key contains the query, ignoring case; every row for a blank query. */
export function searchBoardRows(rows: readonly BoardRow[], query: string): BoardRow[] {
  const needle: string = query.trim().toLocaleLowerCase();
  if (!needle) return [...rows];
  return rows.filter(
    (row) =>
      row.name.toLocaleLowerCase().includes(needle) ||
      (row.prefix ?? "").toLocaleLowerCase().includes(needle)
  );
}

/**
 * The boards a board may move under: every board except itself and its own
 * descendants, since a board under its own child would close a loop.
 */
export function parentCandidates(rows: readonly BoardRow[], boardId: string | null): BoardRow[] {
  if (!boardId) return [...rows];
  const excluded: Set<string> = new Set([boardId]);
  let grew: boolean = true;
  while (grew) {
    grew = false;
    for (const row of rows) {
      if (row.parentId && excluded.has(row.parentId) && !excluded.has(row.id)) {
        excluded.add(row.id);
        grew = true;
      }
    }
  }
  return rows.filter((row) => !excluded.has(row.id));
}

/** The rows of the recently opened boards, newest first; a board no longer listed is skipped. */
export function recentBoardRows(
  rows: readonly BoardRow[],
  recentIds: readonly string[]
): BoardRow[] {
  const byId: Map<string, BoardRow> = new Map(rows.map((row) => [row.id, row]));
  return recentIds.map((id) => byId.get(id)).filter((row): row is BoardRow => row !== undefined);
}

/** The most recently opened boards first, without duplicates, at most `max`. */
export function pushRecent(recent: readonly string[], boardId: string, max: number = 5): string[] {
  return [boardId, ...recent.filter((id) => id !== boardId)].slice(0, max);
}

/**
 * A ticket key suggested from a board name: the initials of a name of several
 * words, otherwise its first three letters, in upper case and at most five long.
 */
export function suggestPrefix(name: string): string {
  const words: string[] = name
    .normalize("NFKD")
    .replace(/[^A-Za-z0-9 ]/g, " ")
    .split(/\s+/)
    .filter(Boolean);
  const raw: string =
    words.length > 1 ? words.map((word) => word[0]).join("") : (words[0] ?? "").slice(0, 3);
  return raw.toUpperCase().slice(0, 5);
}

/** A person's full name, or the email when the name is empty. */
export function personName(person: {
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
}): string {
  const name: string = [person.firstName, person.lastName]
    .filter((part): part is string => !!part && part.trim().length > 0)
    .join(" ")
    .trim();
  return name || person.email || "?";
}

// ── Roles ────────────────────────────────────────────────────────

/** The organization role may write: everyone but a guest. */
function organizationWrites(organizationRole: string | null): boolean {
  return (
    organizationRole === "OWNER" || organizationRole === "ADMIN" || organizationRole === "MEMBER"
  );
}

/** Creating a board needs an organization role above guest. */
export function canCreateBoard(organizationRole: string | null): boolean {
  return organizationWrites(organizationRole);
}

/** Ticket and sprint writes need an organization role above guest and a board role above viewer. */
export function canWriteBoard(organizationRole: string | null, boardRole: string | null): boolean {
  return (
    organizationWrites(organizationRole) &&
    (boardRole === "OWNER" || boardRole === "ADMIN" || boardRole === "MEMBER")
  );
}

/** Renaming a board or changing its parent needs board ADMIN or OWNER. */
export function canEditBoard(boardRole: string | null): boolean {
  return boardRole === "OWNER" || boardRole === "ADMIN";
}

/**
 * Creating, editing, starting and completing a sprint needs an organization
 * role above guest and board ADMIN or OWNER, as the server checks.
 */
export function canManageSprints(
  organizationRole: string | null,
  boardRole: string | null
): boolean {
  return organizationWrites(organizationRole) && canEditBoard(boardRole);
}

/** Deleting a board needs board OWNER. */
export function canDeleteBoard(boardRole: string | null): boolean {
  return boardRole === "OWNER";
}

/**
 * Selecting and moving several tickets of a board is offered to its OWNER,
 * ADMIN and MEMBER, Kaizen web's check. It reads no organization role, as the
 * web does not; the server refuses a guest's move, and the refusal is toasted.
 */
export function canEditBoardTickets(boardRole: string | null): boolean {
  return boardRole === "OWNER" || boardRole === "ADMIN" || boardRole === "MEMBER";
}

// ── Views and filters in route params ────────────────────────────

export const BOARD_VIEWS = ["all", "sprint", "backlog", "roadmap"] as const;
export type BoardView = (typeof BOARD_VIEWS)[number];

/**
 * The view a board opens on when nothing names one: All, the sprint and the
 * backlog together, as Kaizen web's bare board URL. Every way into a board
 * shares it, so a board row, a child board or a plain board link all land on
 * the same view.
 */
export const DEFAULT_BOARD_VIEW: BoardView = "all";

export type SprintLayout = "kanban" | "list";

/** A due-date filter preset; weeks run Monday to Sunday. */
export type DuePreset = "week";

export interface BoardFilters {
  statuses: TicketStatus[];
  assignees: string[];
  types: string[];
  priorities: string[];
  labels: string[];
  project: string | null;
  milestone: string | null;
  due: DuePreset | null;
}

export const EMPTY_FILTERS: BoardFilters = {
  statuses: [],
  assignees: [],
  types: [],
  priorities: [],
  labels: [],
  project: null,
  milestone: null,
  due: null,
};

/** The params of the board route: view, layout, sprint, search and filters. */
export interface BoardRouteState {
  view: BoardView;
  layout: SprintLayout;
  sprintId: string | null;
  search: string;
  filters: BoardFilters;
}

type RawParams = Record<string, string | string[] | undefined>;

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function list(value: string | string[] | undefined): string[] {
  const raw: string | undefined = first(value);
  return raw ? raw.split(",").filter(Boolean) : [];
}

/**
 * The known statuses of a route param. An unknown value is dropped rather than
 * sent on, since the API refuses a status it cannot bind and the whole list
 * would fail to load.
 */
function statusList(value: string | string[] | undefined): TicketStatus[] {
  return list(value).filter((status): status is TicketStatus =>
    (TICKET_STATUSES as readonly string[]).includes(status)
  );
}

/** The board route state from its params; anything unknown falls back to the default. */
export function parseBoardRoute(params: RawParams): BoardRouteState {
  const view: string | undefined = first(params.view);
  return {
    view: (BOARD_VIEWS as readonly string[]).includes(view ?? "")
      ? (view as BoardView)
      : DEFAULT_BOARD_VIEW,
    layout: first(params.layout) === "list" ? "list" : "kanban",
    sprintId: first(params.sprintId) || null,
    search: first(params.q) ?? "",
    filters: {
      statuses: statusList(params.statuses),
      assignees: list(params.assignees),
      types: list(params.types),
      priorities: list(params.priorities),
      labels: list(params.labels),
      project: first(params.project) || null,
      milestone: first(params.milestone) || null,
      due: first(params.due) === "week" ? "week" : null,
    },
  };
}

/**
 * The params of a board route state. Defaults and empty values are left out,
 * so a plain board link carries only its id.
 */
export function boardRouteParams(state: Partial<BoardRouteState>): Record<string, string> {
  const params: Record<string, string> = {};
  if (state.view && state.view !== DEFAULT_BOARD_VIEW) params.view = state.view;
  if (state.layout === "list") params.layout = "list";
  if (state.sprintId) params.sprintId = state.sprintId;
  if (state.search?.trim()) params.q = state.search.trim();
  const filters: BoardFilters | undefined = state.filters;
  if (filters) {
    if (filters.statuses.length) params.statuses = filters.statuses.join(",");
    if (filters.assignees.length) params.assignees = filters.assignees.join(",");
    if (filters.types.length) params.types = filters.types.join(",");
    if (filters.priorities.length) params.priorities = filters.priorities.join(",");
    if (filters.labels.length) params.labels = filters.labels.join(",");
    if (filters.project) params.project = filters.project;
    if (filters.milestone) params.milestone = filters.milestone;
    if (filters.due) params.due = filters.due;
  }
  return params;
}

/** Every board route param, set or cleared, for `router.setParams`, which merges. */
export const BOARD_ROUTE_KEYS = [
  "view",
  "layout",
  "sprintId",
  "q",
  "statuses",
  "assignees",
  "types",
  "priorities",
  "labels",
  "project",
  "milestone",
  "due",
] as const;

/**
 * The params that move a board route to a new state: each key of the state
 * is set, and a key the state leaves at its default is cleared.
 */
export function boardRouteParamPatch(state: BoardRouteState): Record<string, string | undefined> {
  const params: Record<string, string> = boardRouteParams(state);
  const patch: Record<string, string | undefined> = {};
  for (const key of BOARD_ROUTE_KEYS) patch[key] = params[key];
  return patch;
}

/** How many filter groups are set, for the count on the Filters chip. */
export function activeFilterCount(filters: BoardFilters): number {
  return (
    (filters.statuses.length ? 1 : 0) +
    (filters.assignees.length ? 1 : 0) +
    (filters.types.length ? 1 : 0) +
    (filters.priorities.length ? 1 : 0) +
    (filters.labels.length ? 1 : 0) +
    (filters.project ? 1 : 0) +
    (filters.milestone ? 1 : 0) +
    (filters.due ? 1 : 0)
  );
}

/** Adds a value to a multi-select filter or removes it, returning a new list. */
export function toggleValue(values: readonly string[], value: string): string[] {
  return values.includes(value) ? values.filter((item) => item !== value) : [...values, value];
}

/** Toggles a status filter, keeping the statuses in board order so the link stays stable. */
export function toggleStatus(
  statuses: readonly TicketStatus[],
  status: TicketStatus
): TicketStatus[] {
  const next: string[] = toggleValue(statuses, status);
  return TICKET_STATUSES.filter((item) => next.includes(item));
}

/** One ticket list of a board: a sprint, or the board's backlog. */
export type TicketScope = { sprintId: string } | { backlogId: string };

/**
 * The query string of `GET /kaizen/boards/{boardId}/tickets`: board order,
 * the scope, the filters and the search text. Lists go comma-joined, as Spring
 * binds them. "This week" is Monday to Sunday of the viewer's week, compared
 * by calendar day.
 */
export function boardTicketParams(
  scope: TicketScope,
  filters: BoardFilters,
  search: string,
  now: Date = new Date()
): Record<string, string> {
  const params: Record<string, string> = { sortBy: "displayOrder", sortDirection: "asc" };
  if ("sprintId" in scope) params.sprintId = scope.sprintId;
  else params.backlogId = scope.backlogId;
  if (search.trim()) params.search = search.trim();
  if (filters.statuses.length) params.statuses = filters.statuses.join(",");
  if (filters.assignees.length) params.assigneeIds = filters.assignees.join(",");
  if (filters.types.length) params.types = filters.types.join(",");
  if (filters.priorities.length) params.priorities = filters.priorities.join(",");
  if (filters.labels.length) params.labelIds = filters.labels.join(",");
  if (filters.project) params.projectId = filters.project;
  if (filters.milestone) params.milestoneId = filters.milestone;
  if (filters.due === "week") {
    const week = currentWeekRange(now);
    params.dueDateStart = week.start;
    params.dueDateEnd = week.end;
  }
  return params;
}

// ── Sprints ──────────────────────────────────────────────────────

/** The sprint a board view opens on: the active one, else the first planned, else the first. */
export function defaultSprint(sprints: readonly Sprint[]): Sprint | null {
  return (
    sprints.find((sprint) => sprint.status === "ACTIVE") ??
    sprints.find((sprint) => sprint.status === "PLANNED") ??
    sprints[0] ??
    null
  );
}

/** The board's running sprint, or null. */
export function activeSprint(sprints: readonly Sprint[]): Sprint | null {
  return sprints.find((sprint) => sprint.status === "ACTIVE") ?? null;
}

/** The planned sprint that starts first, which the Backlog view shows above the backlog. */
export function nextPlannedSprint(sprints: readonly Sprint[]): Sprint | null {
  const planned: Sprint[] = sprints.filter((sprint) => sprint.status === "PLANNED");
  planned.sort((a, b) => (a.startDate ?? "￿").localeCompare(b.startDate ?? "￿"));
  return planned[0] ?? null;
}

/**
 * The sprint a board view shows: on All and Sprint the one picked in the
 * route, else the default; on Backlog the next planned one; none on Roadmap.
 */
export function sprintForView(
  view: BoardView,
  sprints: readonly Sprint[],
  chosenId: string | null
): Sprint | null {
  if (view === "roadmap") return null;
  if (view === "backlog") return nextPlannedSprint(sprints);
  return sprints.find((sprint) => sprint.id === chosenId) ?? defaultSprint(sprints);
}

/** The sprint picker order: active, planned, then completed, each newest start first. */
export function sortSprintsForPicker(sprints: readonly Sprint[]): Sprint[] {
  const rank = (status: string): number => (status === "ACTIVE" ? 0 : status === "PLANNED" ? 1 : 2);
  return [...sprints].sort(
    (a, b) =>
      rank(a.status) - rank(b.status) || (b.startDate ?? "").localeCompare(a.startDate ?? "")
  );
}

// ── Tickets ──────────────────────────────────────────────────────

export interface StatusGroup {
  status: TicketStatus;
  tickets: BoardTicket[];
}

/**
 * Tickets by status in board order (Todo to Done), every status present even
 * when empty, as the Kanban columns need. An unknown status counts as Todo.
 */
export function groupByStatus(tickets: readonly BoardTicket[]): StatusGroup[] {
  const groups: StatusGroup[] = TICKET_STATUSES.map((status) => ({ status, tickets: [] }));
  for (const ticket of tickets) {
    const index: number = (TICKET_STATUSES as readonly string[]).indexOf(ticket.status);
    groups[index >= 0 ? index : 0].tickets.push(ticket);
  }
  return groups;
}

/** The ticket count per status. */
export function statusCounts(tickets: readonly BoardTicket[]): Record<TicketStatus, number> {
  const counts = Object.fromEntries(TICKET_STATUSES.map((status) => [status, 0])) as Record<
    TicketStatus,
    number
  >;
  for (const group of groupByStatus(tickets)) counts[group.status] = group.tickets.length;
  return counts;
}

/** The statuses of a sprint's progress bar and legend, furthest along first. */
export const SPRINT_PROGRESS_ORDER: readonly TicketStatus[] = [
  "DONE",
  "IN_REVIEW",
  "WAITING_FOR_THIRD_PARTY",
  "IN_PROGRESS",
  "TODO",
];

/**
 * A sprint's progress bar: each status with its ticket count, furthest
 * along first, and the whole percent of tickets done (0 without tickets).
 */
export function sprintProgressSegments(tickets: readonly BoardTicket[]): {
  segments: { status: TicketStatus; count: number }[];
  donePercent: number;
} {
  const counts: Record<TicketStatus, number> = statusCounts(tickets);
  const total: number = tickets.length;
  return {
    segments: SPRINT_PROGRESS_ORDER.map((status) => ({ status, count: counts[status] })),
    donePercent: total === 0 ? 0 : Math.round((counts.DONE / total) * 100),
  };
}

export const BACKLOG_SORTS = ["rank", "priority", "created"] as const;
export type BacklogSort = (typeof BACKLOG_SORTS)[number];

const PRIORITY_WEIGHT: Record<string, number> = {
  P1: 8,
  P2: 7,
  P3: 6,
  HIGHEST: 5,
  HIGH: 4,
  MEDIUM: 3,
  LOW: 2,
  LOWEST: 1,
};

/**
 * The backlog in the chosen order: rank is the board order the team set by
 * dragging; priority puts incidents and the highest first; created puts the
 * newest first. Never mutates the input.
 */
export function sortBacklog(tickets: readonly BoardTicket[], sort: BacklogSort): BoardTicket[] {
  const copy: BoardTicket[] = [...tickets];
  if (sort === "priority") {
    return copy.sort(
      (a, b) => (PRIORITY_WEIGHT[b.priority ?? ""] ?? 0) - (PRIORITY_WEIGHT[a.priority ?? ""] ?? 0)
    );
  }
  if (sort === "created") return copy.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return copy.sort(
    (a, b) =>
      (a.displayOrder ?? Number.MAX_SAFE_INTEGER) - (b.displayOrder ?? Number.MAX_SAFE_INTEGER)
  );
}

/** Done and total sub-tickets, or null for a ticket without sub-tickets. */
export function subTicketProgress(ticket: BoardTicket): { done: number; total: number } | null {
  const subTickets = ticket.subTickets ?? [];
  if (subTickets.length === 0) return null;
  return {
    done: subTickets.filter((subTicket) => subTicket.status === "DONE").length,
    total: subTickets.length,
  };
}

/** The heading over sub-tickets whose parent is not in the same list. */
export interface ParentGroup {
  parent: { id: string; key: string; title: string };
  /** Done and total sub-tickets of the parent, when the parent is loaded; else null. */
  progress: { done: number; total: number } | null;
  /** True when the parent itself is done, so the heading strikes its title. */
  parentDone: boolean;
}

/**
 * A list with the sub-tickets of a parent that sits elsewhere gathered under
 * one heading, at the place of the first of them; every other row keeps its
 * place. `headers` maps the first row of each group to its heading, and
 * `children` holds the ids of the grouped rows, which the list indents. The
 * progress comes from the parent row when `known` holds it, such as the
 * other group of the All view.
 */
export function groupUnderParents(
  rows: readonly BoardTicket[],
  known: readonly BoardTicket[] = []
): { rows: BoardTicket[]; headers: Map<string, ParentGroup>; children: Set<string> } {
  const inList: Set<string> = new Set(rows.map((row) => row.id));
  const members: Map<string, BoardTicket[]> = new Map();
  for (const row of rows) {
    const parentId: string | null | undefined = row.parentTicket?.id ?? row.parentTicketId;
    if (!row.parentTicket || !parentId || inList.has(parentId)) continue;
    members.set(parentId, [...(members.get(parentId) ?? []), row]);
  }
  const lookup: Map<string, BoardTicket> = new Map([...known, ...rows].map((row) => [row.id, row]));
  const ordered: BoardTicket[] = [];
  const headers: Map<string, ParentGroup> = new Map();
  const children: Set<string> = new Set();
  const placed: Set<string> = new Set();
  for (const row of rows) {
    const parentId: string | null | undefined = row.parentTicket?.id ?? row.parentTicketId;
    const group: BoardTicket[] | undefined =
      row.parentTicket && parentId ? members.get(parentId) : undefined;
    if (!group || !row.parentTicket || !parentId) {
      ordered.push(row);
      continue;
    }
    if (placed.has(parentId)) continue;
    placed.add(parentId);
    const parentRow: BoardTicket | undefined = lookup.get(parentId);
    headers.set(group[0].id, {
      parent: row.parentTicket,
      progress: parentRow ? subTicketProgress(parentRow) : null,
      parentDone: parentRow?.status === "DONE",
    });
    for (const member of group) {
      ordered.push(member);
      children.add(member.id);
    }
  }
  return { rows: ordered, headers, children };
}

/** Open root tickets per board id, from the cross-board list of open tickets. */
export function countByBoard(
  tickets: readonly { board: { id: string } | null }[]
): Record<string, number> {
  const counts: Record<string, number> = {};
  for (const ticket of tickets) {
    if (ticket.board) counts[ticket.board.id] = (counts[ticket.board.id] ?? 0) + 1;
  }
  return counts;
}

// ── Roadmap ──────────────────────────────────────────────────────

export interface Quarter {
  /** 1 to 4. */
  quarter: number;
  year: number;
  /** First day, `YYYY-MM-DD`. */
  from: string;
  /** Last day, `YYYY-MM-DD`. */
  to: string;
  /** The first day of each of its three months, `YYYY-MM-DD`. */
  months: string[];
}

/** The calendar quarter of a day, `offset` quarters on (negative for earlier ones). */
export function quarterOf(now: Date = new Date(), offset: number = 0): Quarter {
  const index: number = now.getFullYear() * 4 + Math.floor(now.getMonth() / 3) + offset;
  const year: number = Math.floor(index / 4);
  const quarter: number = (index % 4) + 1;
  const firstMonth: number = (quarter - 1) * 3;
  const months: string[] = [0, 1, 2].map((step) =>
    toDateOnly(new Date(year, firstMonth + step, 1))
  );
  const nextQuarterStart: string = toDateOnly(new Date(year, firstMonth + 3, 1));
  return { quarter, year, from: months[0], to: addDaysToDateOnly(nextQuarterStart, -1), months };
}

/** Where a day falls in a window, 0 to 100, clamped to the window. */
export function percentInWindow(day: string, from: string, to: string): number {
  const length: number = (daysBetween(from, to) ?? 0) + 1;
  const offset: number = daysBetween(from, day) ?? 0;
  if (length <= 0) return 0;
  return Math.min(100, Math.max(0, (offset / length) * 100));
}

/** Today's place in a quarter, 0 to 100, or null when today lies outside it. */
export function quarterTodayPercent(quarter: Quarter, today: string): number | null {
  if (today < quarter.from || today > quarter.to) return null;
  return percentInWindow(today, quarter.from, quarter.to);
}

/**
 * A milestone's bar in the quarter: its start and width as percentages. A
 * milestone without a start begins at its target day; one without either has
 * no bar.
 */
export function milestoneBar(
  milestone: Pick<Milestone, "startDate" | "targetDate">,
  from: string,
  to: string
): { start: number; width: number } | null {
  const start: string | null | undefined = milestone.startDate ?? milestone.targetDate;
  const end: string | null | undefined = milestone.targetDate ?? milestone.startDate;
  if (!start || !end) return null;
  const left: number = percentInWindow(start, from, to);
  const right: number = percentInWindow(addDaysToDateOnly(end, 1), from, to);
  return { start: left, width: Math.max(right - left, 2) };
}

/** Done tickets of a milestone as a percentage, 0 when it has none. */
export function milestoneProgress(milestone: Pick<Milestone, "doneCount" | "totalCount">): number {
  const total: number = milestone.totalCount ?? 0;
  if (total <= 0) return 0;
  return Math.round(((milestone.doneCount ?? 0) / total) * 100);
}
