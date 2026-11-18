/**
 * Which sprint a board view shows, shared by the server pages that redirect
 * to a sprint and the client hooks that select one, so both follow one rule.
 *
 * Not marked "use client": the board route pages import it on the server.
 */

import { SprintStatus, type SprintListResponse } from "@/features/sprints/schemas";

/**
 * Search param of the All tab that names the sprint it shows, by the sprint's
 * slug on the board. Absent for the board's default sprint, so a plain All tab
 * link always opens on the sprint the board is working in.
 */
export const ALL_TAB_SPRINT_PARAM = "sprint";

/**
 * The sprint a board opens on: the active one, else the first planned one,
 * else the first in the list; null when the board has no sprint. The list
 * keeps the server's order, the same order the client views use.
 */
export function defaultSprint(sprints: readonly SprintListResponse[]): SprintListResponse | null {
  return (
    sprints.find((sprint) => sprint.status === SprintStatus.ACTIVE) ??
    sprints.find((sprint) => sprint.status === SprintStatus.PLANNED) ??
    sprints[0] ??
    null
  );
}
