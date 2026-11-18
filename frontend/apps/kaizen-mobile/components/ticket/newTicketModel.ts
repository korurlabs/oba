import { sortSprintsForPicker, type BoardRow } from "@/components/boards/boardModel";
import {
  DEFAULT_MISSION_RANK,
  isMissionRank,
  TICKET_STATUSES,
  TICKET_TYPES,
  type MissionRank,
  type TicketStatus,
  type TicketType,
} from "@/components/kaizen/ticketMetaModel";
import { priorityChoices } from "@/components/ticket/ticketActionsModel";
import { dueDateInstant } from "@/components/ticket/dueDateModel";
import type { Sprint } from "@/lib/schemas/boards";
import { TICKET_DESCRIPTION_MAX, TICKET_TITLE_MAX } from "@/lib/schemas/tickets";

/*
 * The pure rules of a new ticket: the defaults, where the ticket lands, the
 * field checks and the create request. No React, so `bun test` covers them.
 */

/** The type a new ticket starts as. */
export const DEFAULT_TICKET_TYPE: TicketType = "FEATURE";

/** The status a new ticket starts with, and the only one a backlog ticket can have. */
export const DEFAULT_TICKET_STATUS: TicketStatus = "TODO";

/** The placement value of the board's backlog; a sprint id is never this string. */
export const BACKLOG_PLACEMENT = "backlog";

/** The priority a ticket of this type starts with: P2 for an incident, else Medium. */
export function defaultPriority(type: string): string {
  return type === "INCIDENT" ? "P2" : "MEDIUM";
}

/**
 * The priority after the type changes: kept when the new type allows it,
 * else the new type's default. Incidents use P1 to P3 and every other type
 * Lowest to Highest, so switching between the two families resets it.
 */
export function priorityForType(type: string, current: string | null | undefined): string {
  const allowed: readonly string[] = priorityChoices(type);
  return current && allowed.includes(current) ? current : defaultPriority(type);
}

/** A type from a link or a caller, or the default when it is not a ticket type. */
export function typeOrDefault(raw: string | null | undefined): TicketType {
  const known: TicketType | undefined = TICKET_TYPES.find((type) => type === raw);
  return known ?? DEFAULT_TICKET_TYPE;
}

/** A status from a link or a caller, or Todo when it is not a ticket status. */
export function statusOrDefault(raw: string | null | undefined): TicketStatus {
  const known: TicketStatus | undefined = TICKET_STATUSES.find((status) => status === raw);
  return known ?? DEFAULT_TICKET_STATUS;
}

/** A rank, or M when the value is not a mission rank. */
export function rankOrDefault(raw: string | null | undefined): MissionRank {
  return isMissionRank(raw) ? raw : DEFAULT_MISSION_RANK;
}

/** The sprints a new ticket can be added to: active first, then planned; never a completed one. */
export function openSprints(sprints: readonly Sprint[]): Sprint[] {
  return sortSprintsForPicker(sprints).filter((sprint) => sprint.status !== "COMPLETED");
}

/**
 * Where a new ticket starts: the sprint it was created from when that sprint
 * is still open on this board, else the backlog.
 */
export function initialPlacement(
  sprints: readonly Sprint[],
  requestedSprintId: string | null
): string {
  if (!requestedSprintId) return BACKLOG_PLACEMENT;
  return openSprints(sprints).some((sprint) => sprint.id === requestedSprintId)
    ? requestedSprintId
    : BACKLOG_PLACEMENT;
}

/**
 * The board a new ticket goes to: the requested one, else the board opened
 * last on this phone, else the first board of the tree. Null while the tree
 * is empty.
 */
export function defaultBoardId(
  requested: string | null,
  recent: readonly string[],
  rows: readonly Pick<BoardRow, "id">[]
): string | null {
  const known = (id: string): boolean => rows.some((row) => row.id === id);
  if (requested && (rows.length === 0 || known(requested))) return requested;
  return recent.find(known) ?? rows[0]?.id ?? null;
}

/** Why a title is refused before the request is sent. */
export type TitleProblem = "required" | "too-long";

/** A title: required and at most 255 characters, as the API checks it. */
export function titleProblem(title: string): TitleProblem | null {
  const trimmed: string = title.trim();
  if (!trimmed) return "required";
  if (trimmed.length > TICKET_TITLE_MAX) return "too-long";
  return null;
}

/** A description is optional and at most 10,000 characters. */
export function descriptionTooLong(description: string): boolean {
  return description.trim().length > TICKET_DESCRIPTION_MAX;
}

/**
 * Whether a new ticket's status can be chosen. A top-level ticket in the
 * backlog is always Todo, as the API resets any other status there; a sprint
 * ticket and a sub-ticket can start in any status.
 */
export function canPickStatus(placement: string, isSubTicket: boolean): boolean {
  return isSubTicket || placement !== BACKLOG_PLACEMENT;
}

/** The status a new ticket is sent with: the picked one where it can be chosen, else Todo. */
export function statusForPlacement(
  status: string | null | undefined,
  placement: string,
  isSubTicket: boolean
): TicketStatus {
  if (!canPickStatus(placement, isSubTicket)) return DEFAULT_TICKET_STATUS;
  const known: TicketStatus | undefined = TICKET_STATUSES.find((value) => value === status);
  return known ?? DEFAULT_TICKET_STATUS;
}

/** What the New ticket screen holds when Create is pressed. */
export interface NewTicketDraft {
  title: string;
  description: string;
  type: string;
  priority: string;
  /** Todo without it, and always Todo for a top-level backlog ticket. */
  status?: string | null;
  missionRank: string;
  /** `BACKLOG_PLACEMENT` or a sprint id; ignored for a sub-ticket. */
  placement: string;
  assigneeIds: readonly string[];
  labelIds: readonly string[];
  /** The picked due day, `YYYY-MM-DD`; none without it. */
  dueDay?: string | null;
  milestoneId?: string | null;
  projectId?: string | null;
}

/** Where the ticket lands: the board's backlog id, and the parent for a sub-ticket. */
export interface NewTicketTarget {
  backlogId: string | null;
  parentTicketId: string | null;
}

/** The `TicketCreateRequest` fields the app sends. */
export interface TicketCreateBody {
  title: string;
  description?: string;
  type: string;
  priority: string;
  status: TicketStatus;
  missionRank: string;
  sprintId?: string;
  backlogId?: string;
  parentTicketId?: string;
  assigneeIds: string[];
  labelIds: string[];
  /** An instant with `Z`, from the picked due day. */
  dueDate?: string;
  milestoneId?: string;
  projectId?: string;
  /** The root cause analysis an incident created as Done must carry. */
  rca?: string;
  /** The lessons learned an incident created as Done must carry. */
  lessonsLearned?: string;
}

/**
 * The create request, or null when the ticket has nowhere to land: the API
 * wants exactly one of sprint and backlog, so a backlog choice without the
 * backlog's id cannot be sent. A sub-ticket sends neither, because the API
 * puts it where its parent is. A top-level backlog ticket is sent as Todo.
 */
export function createTicketBody(
  draft: NewTicketDraft,
  target: NewTicketTarget
): TicketCreateBody | null {
  const description: string = draft.description.trim();
  const dueDate: string | null = dueDateInstant(draft.dueDay);
  const body: TicketCreateBody = {
    title: draft.title.trim(),
    ...(description ? { description } : {}),
    type: draft.type,
    priority: priorityForType(draft.type, draft.priority),
    status: statusForPlacement(draft.status, draft.placement, !!target.parentTicketId),
    missionRank: rankOrDefault(draft.missionRank),
    assigneeIds: [...draft.assigneeIds],
    labelIds: [...draft.labelIds],
    ...(dueDate ? { dueDate } : {}),
    ...(draft.milestoneId ? { milestoneId: draft.milestoneId } : {}),
    ...(draft.projectId ? { projectId: draft.projectId } : {}),
  };
  if (target.parentTicketId) return { ...body, parentTicketId: target.parentTicketId };
  if (draft.placement !== BACKLOG_PLACEMENT) return { ...body, sprintId: draft.placement };
  if (!target.backlogId) return null;
  return { ...body, backlogId: target.backlogId };
}
