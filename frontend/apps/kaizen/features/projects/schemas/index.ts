import { z } from "zod";

// ── Request Schemas ─────────────────────────────────────────────────

export const projectCreateRequestSchema = z.object({
  name: z.string().min(1),
  color: z.string().optional(),
});

export const projectUpdateRequestSchema = z.object({
  name: z.string().optional(),
  color: z.string().optional(),
});

// ── Response Schemas ────────────────────────────────────────────────

export const projectDetailResponseSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  name: z.string(),
  color: z.string().optional(),
  boardId: z.string(),
});

export const projectListResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  color: z.string().optional(),
  boardId: z.string(),
  boardName: z.string(),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type ProjectCreateRequest = z.infer<typeof projectCreateRequestSchema>;
export type ProjectUpdateRequest = z.infer<typeof projectUpdateRequestSchema>;
export type ProjectDetailResponse = z.infer<typeof projectDetailResponseSchema>;
export type ProjectListResponse = z.infer<typeof projectListResponseSchema>;
