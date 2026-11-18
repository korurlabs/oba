/**
 * Ticket type exports — derived from Zod schemas in features/tickets/schemas.
 *
 * TicketListResponse  → used for list/kanban/backlog views
 * TicketDetailResponse → used for the detail/edit sheet
 *
 * TicketUser is a UI-only type for board member mappings (not a backend DTO).
 */
export type {
  TicketListResponse,
  TicketDetailResponse,
  TicketType,
  TicketStatus,
  TicketPriority,
  TicketAssigneeResponse,
  TicketReviewerResponse,
} from "@/features/tickets/schemas";
export type { MissionRank } from "@/features/tickets/schemas";

/**
 * Alias for TicketPriority — replaces Prisma's `Priority` type.
 */
export type { TicketPriority as Priority } from "@/features/tickets/schemas";

/**
 * TicketUser — UI-only type used for board member selections.
 * This is NOT a backend DTO; it's built by mapping BoardMemberResponse
 * in use-sprint-page.ts and backlog/page.tsx.
 */
export interface TicketUser {
  id: string;
  userId?: string;
  name?: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  image?: string | null;
  avatar?: string | null;
}

/**
 * Backward-compatible aliases. Components that previously imported `Ticket`
 * now receive `TicketListResponse`. Components that imported
 * `TicketWithRelations` now receive `TicketListResponse` as well
 * (the list response already contains assignees, subTickets, etc.).
 */
export type { TicketListResponse as Ticket } from "@/features/tickets/schemas";
export type { TicketListResponse as TicketWithRelations } from "@/features/tickets/schemas";
