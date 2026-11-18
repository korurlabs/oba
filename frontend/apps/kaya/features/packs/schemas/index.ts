import { z } from "zod";

// ── Limits ─────────────────────────────────────────────────────────
/** Versions per page of a pack's history; the server takes 1 to 50. */
export const PACK_VERSIONS_PAGE_SIZE = 20;

// ── Request Schemas ─────────────────────────────────────────────────
/** `GET /versions`: one pack's versions, newest first; `page` counts from 1. */
export const packVersionQueryRequestSchema = z.object({
  packId: z.string().min(1),
  page: z.number().int().min(1),
  amount: z.number().int().min(1).max(50),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type PackVersionQueryRequest = z.infer<typeof packVersionQueryRequestSchema>;
