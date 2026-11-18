import { z } from "zod";

// ── Enum Schemas ───────────────────────────────────────────────────
export const RepositoryStatusSchema = z.enum(["ACTIVE", "ARCHIVED"]);
export type RepositoryStatus = z.infer<typeof RepositoryStatusSchema>;
export const RepositoryStatus = RepositoryStatusSchema.enum;

// ── Request Schemas ─────────────────────────────────────────────────
export const repositoryUpdateRequestSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Enter a name.")
    .max(100, "A name has at most 100 characters.")
    .optional(),
  /** "" removes the template. */
  ticketUrlTemplate: z
    .string()
    .trim()
    .max(500, "A template has at most 500 characters.")
    .refine(
      (value: string) => value === "" || /^https:\/\/\S*\{key\}\S*$/.test(value),
      "Use an https URL that contains {key}, such as https://tickets.example.com/browse/{key}."
    )
    .optional(),
  status: RepositoryStatusSchema.optional(),
});

// ── Response Schemas ────────────────────────────────────────────────
export const repositorySettingsResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  remoteUrl: z.string(),
  ticketUrlTemplate: z.string().nullish(),
  status: RepositoryStatusSchema,
  createdAt: z.string(),
  updatedAt: z.string().nullish(),
});

// ── Inferred Types ──────────────────────────────────────────────────
export type RepositoryUpdateRequest = z.infer<typeof repositoryUpdateRequestSchema>;
export type RepositorySettingsResponse = z.infer<typeof repositorySettingsResponseSchema>;
