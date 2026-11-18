import { z } from "zod";

// ── Request Schemas ─────────────────────────────────────────────────

export const aiTicketSuggestionResponseSchema = z.object({
  title: z.string(),
  description: z.string(),
  type: z.string(),
  priority: z.string(),
});

export const aiAnalyzeRequestSchema = z.object({
  userInput: z.string().min(1),
});

export const aiCreateFromSuggestionsRequestSchema = z.object({
  suggestions: z.array(aiTicketSuggestionResponseSchema),
  addToBacklog: z.boolean(),
});

// ── Response Schemas ────────────────────────────────────────────────

export const aiAnalysisResponseSchema = z.object({
  suggestions: z.array(aiTicketSuggestionResponseSchema),
  reasoning: z.string(),
  shouldSplit: z.boolean(),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type AiAnalyzeRequest = z.infer<typeof aiAnalyzeRequestSchema>;
export type AiCreateFromSuggestionsRequest = z.infer<
  typeof aiCreateFromSuggestionsRequestSchema
>;
export type AiAnalysisResponse = z.infer<typeof aiAnalysisResponseSchema>;
export type AiTicketSuggestionResponse = z.infer<
  typeof aiTicketSuggestionResponseSchema
>;
