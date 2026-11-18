import { z } from "zod";

// ── Request Schemas ─────────────────────────────────────────────────

export const labelCreateRequestSchema = z.object({
  name: z.string().min(1),
  color: z.string().optional(),
});

export const labelUpdateRequestSchema = z.object({
  name: z.string().optional(),
  color: z.string().optional(),
});

// ── Response Schemas ────────────────────────────────────────────────

export const labelResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  color: z.string().optional(),
  boardId: z.string(),
  boardName: z.string(),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type LabelCreateRequest = z.infer<typeof labelCreateRequestSchema>;
export type LabelUpdateRequest = z.infer<typeof labelUpdateRequestSchema>;
export type LabelResponse = z.infer<typeof labelResponseSchema>;
