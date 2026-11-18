/**
 * Query-key factories for milestone and roadmap queries.
 *
 * Not marked "use client": the roadmap `page.tsx` imports these on the server
 * to prefetch under the exact keys the client hooks use. Inside a
 * "use client" module the functions would become client-reference proxies
 * and could not be called during the server prefetch.
 */
import type { MilestoneListParams, RoadmapParams } from "../schemas";

export const MILESTONE_KEYS = {
  all: ["milestones"] as const,
  list: (boardId: string, params: MilestoneListParams = {}) =>
    [...MILESTONE_KEYS.all, "list", boardId, params] as const,
  detail: (boardId: string, milestoneId: string) =>
    [...MILESTONE_KEYS.all, "detail", boardId, milestoneId] as const,
};

export const ROADMAP_KEYS = {
  all: ["roadmap"] as const,
  board: (boardId: string, params: RoadmapParams = {}) =>
    [...ROADMAP_KEYS.all, "board", boardId, params] as const,
};

/** The roadmap tab always asks for the child-board lanes. */
export const ROADMAP_PAGE_PARAMS: RoadmapParams = { includeChildBoards: true };
