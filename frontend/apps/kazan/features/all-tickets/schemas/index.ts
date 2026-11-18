import { z } from "zod";
import type { TicketListResponse } from "@/features/tickets/schemas";
import {
  TicketTypeSchema,
  TicketStatusSchema,
  TicketPrioritySchema,
} from "@/features/tickets/schemas";

// ── Sprint/Backlog Embedded Types ──────────────────────────────────

export interface TicketSprintResponse {
  id: string;
  name: string;
  status: string;
}

export interface TicketBacklogResponse {
  id: string;
}

// ── AllTicketListResponse ──────────────────────────────────────────

export interface AllTicketListResponse extends TicketListResponse {
  sprint?: TicketSprintResponse | null;
  backlog?: TicketBacklogResponse | null;
}

// ── Query Request Schema ───────────────────────────────────────────

export const allTicketQueryRequestSchema = z.object({
  search: z.string().optional(),
  statuses: z.array(TicketStatusSchema).optional(),
  types: z.array(TicketTypeSchema).optional(),
  priorities: z.array(TicketPrioritySchema).optional(),
  assigneeIds: z.array(z.string()).optional(),
  reviewerIds: z.array(z.string()).optional(),
  boardIds: z.array(z.string()).optional(),
  milestoneId: z.string().optional(),
  sortBy: z.string().optional(),
  sortDirection: z.string().optional(),
});

// ── Inferred Types ─────────────────────────────────────────────────

export type AllTicketQueryRequest = z.infer<typeof allTicketQueryRequestSchema>;
