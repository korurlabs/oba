import {
  MISSION_RANKS,
  TICKET_STATUSES,
  type MissionRank,
  type TicketPriority,
  type TicketStatus,
} from "@/components/kazan/ticketMetaModel";
import type { TicketBulkMoveBody } from "@/components/boards/selection/bulkMoveModel";
import { BACKLOG_PLACEMENT, priorityForType } from "@/components/ticket/newTicketModel";
import { personName } from "@/components/ticket/ticketModel";
import type { TranslationKey } from "@/i18n";
import { hasKazanAccess } from "@/lib/kazanAccess";
import type { BoardMember, BoardTreeItem, Sprint } from "@/lib/schemas/boards";
import type { TicketDetail, TicketPerson } from "@/lib/schemas/tickets";

/*
 * The pure rules of the ticket actions: which choices a picker offers, how a
 * write changes the ticket before the API answers, and which rows the
 * assignee and board pickers list. No React, so `bun test` covers them.
 */

/** Every status a ticket can be set to, in board order. */
export const STATUS_CHOICES: readonly TicketStatus[] = TICKET_STATUSES;

/** An incident is ranked P1 to P3; every other type HIGHEST to LOWEST. */
const INCIDENT_PRIORITIES: readonly TicketPriority[] = ["P1", "P2", "P3"];
const TICKET_PRIORITIES: readonly TicketPriority[] = ["HIGHEST", "HIGH", "MEDIUM", "LOW", "LOWEST"];

/** The priorities the API accepts for a ticket of this type, most urgent first. */
export function priorityChoices(type: string): readonly TicketPriority[] {
  return type === "INCIDENT" ? INCIDENT_PRIORITIES : TICKET_PRIORITIES;
}

/** A saved ticket's new type with the priority it keeps or falls back to. */
export interface TypeChange {
  type: string;
  priority: string;
}

/**
 * What a type change writes. Incidents are ranked P1 to P3 and every other
 * type Lowest to Highest, so a priority the new type does not allow falls
 * back to its default (P2 or Medium), as on Kazan web; one it allows is
 * kept. Both go in one update, so the ticket never holds a priority its type
 * refuses.
 */
export function typeChange(type: string, currentPriority: string | null | undefined): TypeChange {
  return { type, priority: priorityForType(type, currentPriority) };
}

/**
 * Whether the status can change. A top-level ticket in the backlog is always
 * Todo: the API resets any other status there, so no other status is pickable.
 */
export function canChangeStatus(ticket: Pick<TicketDetail, "sprint" | "parentTicketId">): boolean {
  return !!ticket.sprint || !!ticket.parentTicketId;
}

/** A mission rank with its effort and the leaderboard points a done ticket earns. */
export interface RankChoice {
  rank: MissionRank;
  sizeKey: TranslationKey;
  points: number;
}

const RANK_SIZE_KEYS: Record<MissionRank, TranslationKey> = {
  XS: "homeInfoRankXs",
  S: "homeInfoRankS",
  M: "homeInfoRankM",
  L: "homeInfoRankL",
  XL: "homeInfoRankXl",
  XXL: "homeInfoRankXxl",
};

const RANK_POINTS: Record<MissionRank, number> = { XS: 1, S: 2, M: 3, L: 5, XL: 8, XXL: 13 };

/** The mission ranks, smallest first. */
export const RANK_CHOICES: readonly RankChoice[] = MISSION_RANKS.map((rank) => ({
  rank,
  sizeKey: RANK_SIZE_KEYS[rank],
  points: RANK_POINTS[rank],
}));

/** The user ids of the ticket's assignees, in their order. */
export function assigneeIdsOf(ticket: Pick<TicketDetail, "assignees">): string[] {
  return (ticket.assignees ?? []).map((person) => person.userId);
}

/** Whether the user is one of the ticket's assignees. */
export function isAssignee(
  ticket: Pick<TicketDetail, "assignees">,
  userId: string | undefined
): boolean {
  return !!userId && assigneeIdsOf(ticket).includes(userId);
}

/** Someone who can be picked as an assignee. */
export interface AssigneeCandidate {
  userId: string;
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
  /** The board role, or null for a current assignee who has left the board. */
  role: string | null;
  /**
   * Whether the API accepts this person as a new assignee: false for a board
   * member who cannot open Kazan. A current assignee who left the board
   * counts as true, because the picker offers them only for removal.
   */
  kazanAccess: boolean;
}

/**
 * The people the assignee picker lists: the board members, plus current
 * assignees who are no longer members so they can still be removed. The
 * signed-in user comes first, then by name; `query` matches name or email.
 */
export function assigneeCandidates(
  members: readonly BoardMember[],
  assignees: readonly TicketPerson[],
  meId: string | undefined,
  query: string,
  unknownName: string
): AssigneeCandidate[] {
  const byUser = new Map<string, AssigneeCandidate>();
  for (const member of members) {
    byUser.set(member.userId, {
      userId: member.userId,
      firstName: member.firstName,
      lastName: member.lastName,
      email: member.email,
      role: member.role,
      kazanAccess: hasKazanAccess(member),
    });
  }
  for (const person of assignees) {
    if (!byUser.has(person.userId)) {
      byUser.set(person.userId, {
        userId: person.userId,
        firstName: person.firstName,
        lastName: person.lastName,
        email: person.email,
        role: null,
        kazanAccess: true,
      });
    }
  }
  const needle: string = query.trim().toLowerCase();
  return [...byUser.values()]
    .filter((candidate) => {
      if (!needle) return true;
      const name: string = personName(candidate, unknownName).toLowerCase();
      return name.includes(needle) || (candidate.email ?? "").toLowerCase().includes(needle);
    })
    .sort((a, b) => {
      if (a.userId === meId) return -1;
      if (b.userId === meId) return 1;
      return personName(a, unknownName).localeCompare(personName(b, unknownName));
    });
}

/**
 * The assignees as the ticket shows them once a write of `userIds` lands, so
 * the screen can show it at once. The link row id is not known until the API
 * answers, so the user id stands in for it.
 */
export function assigneesFor(
  userIds: readonly string[],
  candidates: readonly AssigneeCandidate[],
  current: readonly TicketPerson[]
): TicketPerson[] {
  return userIds.map((userId) => {
    const known: TicketPerson | undefined = current.find((person) => person.userId === userId);
    if (known) return known;
    const candidate: AssigneeCandidate | undefined = candidates.find(
      (item) => item.userId === userId
    );
    return {
      id: userId,
      userId,
      firstName: candidate?.firstName ?? null,
      lastName: candidate?.lastName ?? null,
      email: candidate?.email ?? null,
    };
  });
}

/** Whether two id lists hold the same ids, in any order. */
export function sameIds(a: readonly string[], b: readonly string[]): boolean {
  if (a.length !== b.length) return false;
  const set = new Set<string>(a);
  return b.every((id) => set.has(id));
}

/** A board a ticket can move to, with its depth in the board tree. */
export interface MoveTarget {
  id: string;
  name: string;
  prefix: string | null;
  depth: number;
}

/**
 * The boards a ticket can move to: every board of the tree in tree order,
 * child boards under their parent, without the ticket's own board.
 */
export function moveTargets(tree: readonly BoardTreeItem[], currentBoardId: string): MoveTarget[] {
  const targets: MoveTarget[] = [];
  const visit = (boards: readonly BoardTreeItem[], depth: number) => {
    for (const board of boards) {
      if (board.id !== currentBoardId) {
        targets.push({ id: board.id, name: board.name, prefix: board.prefix ?? null, depth });
      }
      visit(board.childBoards ?? [], depth + 1);
    }
  };
  visit(tree, 0);
  return targets;
}

/** The sub-tickets that are not done yet, which a move can take along. */
export function unfinishedSubTicketCount(ticket: Pick<TicketDetail, "subTickets">): number {
  return (ticket.subTickets ?? []).filter((sub) => sub.status !== "DONE").length;
}

/**
 * The placement a ticket is offered when it moves to another board:
 * `BACKLOG_PLACEMENT` or a sprint id. A sprint is linked to boards and the
 * move only accepts a sprint linked to the target board, so a sprint shared
 * with that board is kept, else an open sprint of the same name (trimmed,
 * case-insensitive) stands in for it, and anything else lands in the target
 * board's backlog. The user can still pick another one.
 */
export function resolvePlacementOnBoard(
  currentSprint: { id: string; name: string } | null | undefined,
  targetSprints: readonly Pick<Sprint, "id" | "name" | "status">[]
): string {
  if (!currentSprint) return BACKLOG_PLACEMENT;
  const open = targetSprints.filter((sprint) => sprint.status !== "COMPLETED");
  const shared = open.find((sprint) => sprint.id === currentSprint.id);
  if (shared) return shared.id;
  const name: string = currentSprint.name.trim().toLowerCase();
  const sameName = open.find((sprint) => sprint.name.trim().toLowerCase() === name);
  return sameName?.id ?? BACKLOG_PLACEMENT;
}

/** One line of "What the move changes": its text and the values it names. */
export interface MoveChange {
  key: TranslationKey;
  values: Record<string, string | number>;
}

/**
 * What a move to another board changes, in the web's order: the new key;
 * the sub-tickets, which move along or stay on the old board without a
 * parent; the project, which belongs to the old board; the milestone, which
 * goes when it is unrelated to the target board; and the status, which
 * becomes Todo in a backlog. Only the status line depends on the placement,
 * so with no placement yet (the target's sprints still load) the other lines
 * are listed already.
 */
export function moveBoardChanges(
  ticket: Pick<TicketDetail, "key" | "status" | "subTickets" | "project" | "milestone" | "board">,
  target: { prefix?: string | null },
  placement: string | null,
  detach: boolean
): MoveChange[] {
  const changes: MoveChange[] = [
    target.prefix
      ? { key: "ticketMoveBoardChangeNewKey", values: { key: ticket.key, prefix: target.prefix } }
      : { key: "ticketMoveBoardChangeNewKeyNoPrefix", values: { key: ticket.key } },
  ];
  const subTicketCount: number = ticket.subTickets?.length ?? 0;
  if (subTicketCount > 0) {
    changes.push(
      detach
        ? {
            key: "ticketMoveBoardChangeSubTicketsDetached",
            values: { count: subTicketCount, board: ticket.board.name },
          }
        : { key: "ticketMoveBoardChangeSubTickets", values: { count: subTicketCount } }
    );
  }
  if (ticket.project) {
    changes.push({ key: "ticketMoveBoardChangeProject", values: { project: ticket.project.name } });
  }
  if (ticket.milestone) {
    changes.push({
      key: "ticketMoveBoardChangeMilestone",
      values: { milestone: ticket.milestone.name },
    });
  }
  if (placement === BACKLOG_PLACEMENT && ticket.status !== "TODO") {
    changes.push({ key: "ticketMoveBoardChangeBacklogStatus", values: {} });
  }
  return changes;
}

/**
 * `TicketBulkMoveRequest` of a move to another board: into its backlog or
 * one of its sprints. `detachSubTickets` is sent only when the sub-tickets
 * stay behind; without it the server takes them along.
 */
export function moveToBoardBody(
  ticketId: string,
  targetBoardId: string,
  placement: string,
  detach: boolean
): TicketBulkMoveBody {
  const toBacklog: boolean = placement === BACKLOG_PLACEMENT;
  return {
    ticketIds: [ticketId],
    action: toBacklog ? "MOVE_TO_BACKLOG" : "MOVE_TO_SPRINT",
    ...(toBacklog ? {} : { targetSprintId: placement }),
    targetBoardId,
    ...(detach ? { detachSubTickets: true } : {}),
  };
}
