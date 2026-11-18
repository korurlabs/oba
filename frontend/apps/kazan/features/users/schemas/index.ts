import { z } from "zod";

// ── Response Schemas ────────────────────────────────────────────────

export const organizationUserResponseSchema = z.object({
  id: z.string(),
  email: z.string(),
  profile: z
    .object({
      firstName: z.string().nullish(),
      lastName: z.string().nullish(),
    })
    .nullish(),
});

// ── Inferred Types ──────────────────────────────────────────────────

export type OrganizationUser = z.infer<typeof organizationUserResponseSchema>;
