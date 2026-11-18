import { z } from "zod";
import { ticketListResponseSchema } from "@/features/tickets/schemas";

// ── Response Schemas ────────────────────────────────────────────────

export const backlogDetailResponseSchema = z.object({
  id: z.string(),
  boardId: z.string(),
  tickets: z.array(ticketListResponseSchema).optional(),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type BacklogDetailResponse = z.infer<typeof backlogDetailResponseSchema>;
