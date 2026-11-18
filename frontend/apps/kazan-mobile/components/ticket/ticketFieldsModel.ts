import { BACKLOG_PLACEMENT, openSprints } from "@/components/ticket/newTicketModel";
import type { BoardOption, Milestone, Sprint } from "@/lib/schemas/boards";
import type { TicketDetail } from "@/lib/schemas/tickets";

/*
 * The pure rules of the ticket field pickers: the choices of the sprint,
 * milestone, project and label pickers, and how the ticket reads once a pick
 * lands. No React, so `bun test` covers them.
 */

/** A field of the ticket that opens a picker when tapped. */
export type TicketField = "assignees" | "labels" | "sprint" | "milestone" | "project" | "dueDate";

/**
 * What the top of the ticket screen shows. A saved ticket passes itself; a
 * new ticket has no key and no last change yet.
 */
export type TicketHeaderValue = Pick<
  TicketDetail,
  "type" | "title" | "status" | "priority" | "missionRank"
> & {
  key?: string | null;
  updatedAt?: string | null;
};

/**
 * What the field list of the ticket screen shows. A saved ticket passes
 * itself; a new ticket has no reporter yet.
 */
export type TicketFieldsValue = Pick<
  TicketDetail,
  "assignees" | "labels" | "sprint" | "milestone" | "project" | "dueDate" | "status"
> &
  Partial<Pick<TicketDetail, "createdBy" | "createdBySystemActor" | "createdAt">>;

/** Where a ticket can be planned: the backlog or one of the board's open sprints. */
export interface SprintChoice {
  /** `BACKLOG_PLACEMENT` or a sprint id. */
  id: string;
  sprint: Sprint | null;
}

/**
 * The sprint picker's choices: the backlog, then the active sprint, then the
 * planned ones. A completed sprint takes no tickets, so it is never offered.
 */
export function sprintChoices(sprints: readonly Sprint[]): SprintChoice[] {
  return [
    { id: BACKLOG_PLACEMENT, sprint: null },
    ...openSprints(sprints).map((sprint) => ({ id: sprint.id, sprint })),
  ];
}

/** The sprint choice the ticket is on now: its sprint, or the backlog. */
export function currentPlacement(ticket: Pick<TicketDetail, "sprint">): string {
  return ticket.sprint?.id ?? BACKLOG_PLACEMENT;
}

/** The label ids of the ticket, in their order. */
export function labelIdsOf(ticket: Pick<TicketDetail, "labels">): string[] {
  return (ticket.labels ?? []).map((label) => label.id);
}

/**
 * The labels as the ticket shows them once a write of `labelIds` lands. A
 * label the board no longer lists keeps the name the ticket had for it.
 */
export function labelsFor(
  labelIds: readonly string[],
  options: readonly BoardOption[],
  current: readonly BoardOption[]
): BoardOption[] {
  return labelIds.flatMap((id) => {
    const option: BoardOption | undefined =
      options.find((item) => item.id === id) ?? current.find((item) => item.id === id);
    return option ? [{ id: option.id, name: option.name, color: option.color ?? null }] : [];
  });
}

/**
 * The milestone as the ticket carries it once a pick of `milestoneId` lands,
 * or null for "No milestone" and a milestone the board no longer lists.
 */
export function milestoneFor(
  milestoneId: string | null,
  milestones: readonly Milestone[]
): NonNullable<TicketDetail["milestone"]> | null {
  const milestone: Milestone | undefined = milestones.find((item) => item.id === milestoneId);
  if (!milestone) return null;
  return {
    id: milestone.id,
    name: milestone.name,
    status: milestone.status ?? null,
    targetDate: milestone.targetDate ?? null,
  };
}

/** The project as the ticket carries it once a pick of `projectId` lands. */
export function projectFor(
  projectId: string,
  projects: readonly BoardOption[]
): NonNullable<TicketDetail["project"]> | null {
  const project: BoardOption | undefined = projects.find((item) => item.id === projectId);
  return project ? { id: project.id, name: project.name, color: project.color ?? null } : null;
}

/** The description as the editor sends it: an empty text clears it. */
export function descriptionForSave(text: string): string {
  return text.trim() ? text : "";
}
